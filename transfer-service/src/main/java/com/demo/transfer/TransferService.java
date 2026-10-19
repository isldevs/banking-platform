package com.demo.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TransferService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final RestClient remote;
    private final String accountUrl;
    private final String key;
    private final ObjectMapper mapper;

    TransferService(JdbcTemplate db, org.springframework.transaction.PlatformTransactionManager manager,
                    @Value("${services.account-url}") String accountUrl, @Value("${security.internal-key}") String key, ObjectMapper mapper) {
        this.db = db;
        this.tx = new TransactionTemplate(manager);
        this.accountUrl = accountUrl;
        this.key = key;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(4));
        remote = RestClient.builder().requestFactory(f).build();
    }

    private Row row(UUID id) {
        return db.query("SELECT id,source_account_id,destination_account_id,amount,currency,status,requested_by FROM transfers WHERE id=?", (r, n) -> new Row(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class), r.getBigDecimal(4), r.getString(5), r.getString(6), r.getObject(7, UUID.class)), id).stream().findFirst().orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND"));
    }

    public Map<String, Object> view(UUID id, UUID user, boolean admin) {
        Row r = row(id);
        if (!admin && !r.owner().equals(user))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_TRANSFER_OWNER");
        return output(r);
    }

    private Map<String, Object> output(Row r) {
        return Map.of("id", r.id().toString(), "sourceAccountId", r.source().toString(), "destinationAccountId", r.dest().toString(), "amount", r.amount(), "currency", r.currency(), "status", r.status());
    }

    public Map<String, Object> create(NewTransfer request, UUID user, String idempotency) {
        if (idempotency == null || !idempotency.matches("[a-zA-Z0-9._:-]{8,200}"))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
        if (request.sourceAccountId() == null || request.destinationAccountId() == null || request.sourceAccountId().equals(request.destinationAccountId()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_PAIR");
        BigDecimal amount = money(request.amount(), request.currency());
        String normalizedRef = request.reference() == null ? "" : request.reference().trim();
        String fingerprint = Support.hash(request.sourceAccountId() + "|" + request.destinationAccountId() + "|" + amount + "|" + request.currency() + "|" + normalizedRef);
        UUID id = tx.execute(status -> {
            // Transaction-scoped advisory lock prevents concurrent duplicate key inserts racing.
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", user + ":" + idempotency);
            List<Map<String, Object>> prior = db.queryForList("SELECT transfer_id,request_hash FROM idempotency_keys WHERE client_id=? AND client_key=? FOR UPDATE", user, idempotency);
            if (!prior.isEmpty()) {
                if (!fingerprint.equals(prior.get(0).get("request_hash")))
                    throw Support.fail(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
                return (UUID) prior.get(0).get("transfer_id");
            }
            UUID newId = UUID.randomUUID();
            db.update("INSERT INTO transfers(id,transfer_reference,source_account_id,destination_account_id,amount,currency,status,requested_by,client_reference) VALUES(?,?,?,?,?,?,'PENDING',?,?)", newId, "TRF-" + newId.toString(), request.sourceAccountId(), request.destinationAccountId(), amount, request.currency(), user, normalizedRef);
            db.update("INSERT INTO transfer_items(id,transfer_id,account_id,item_type,amount) VALUES(?,?,?,'DEBIT',?),(?,?,?,'CREDIT',?)", UUID.randomUUID(), newId, request.sourceAccountId(), amount, UUID.randomUUID(), newId, request.destinationAccountId(), amount);
            db.update("INSERT INTO idempotency_keys(id,client_id,client_key,request_hash,transfer_id) VALUES(?,?,?,?,?)", UUID.randomUUID(), user, idempotency, fingerprint, newId);
            db.update("INSERT INTO transfer_status_history(id,transfer_id,new_status,changed_by) VALUES(?,?,'PENDING',?)", UUID.randomUUID(), newId, user.toString());
            return newId;
        });
        Row r = row(id);
        if ("PENDING".equals(r.status()) || "PENDING_RECONCILIATION".equals(r.status()))
            process(id);
        return output(row(id));
    }

    private BigDecimal money(BigDecimal value, String currency) {
        if (value == null || currency == null || value.signum() <= 0 || !Set.of("USD", "KHR").contains(currency))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_OR_CURRENCY");
        int scale = "KHR".equals(currency) ? 0 : 2;
        if (value.stripTrailingZeros().scale() > scale)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_MONEY_SCALE");
        BigDecimal max = "USD".equals(currency) ? new BigDecimal("10000.00") : new BigDecimal("40000000");
        if (value.compareTo(max) > 0)
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSFER_LIMIT_EXCEEDED");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public void process(UUID id) {
        Row r = row(id);
        if (!Set.of("PENDING", "PENDING_RECONCILIATION").contains(r.status()))
            return;
        Map<String, Object> payload = Map.of("transferId", r.id(), "sourceAccountId", r.source(), "destinationAccountId", r.dest(), "amount", r.amount(), "currency", r.currency(), "requestedBy", r.owner().toString());
        try {
            remote.post().uri(accountUrl + "/internal/accounts/transfer").header("X-Service-Key", key).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
            complete(id);
        } catch (HttpClientErrorException e) {
            // Only definitive business rejections can be marked failed. Other results stay reconcilable.
            if (Set.of(400, 403, 404, 409, 422).contains(e.getStatusCode().value()))
                fail(id, "ACCOUNT_REJECTED_" + e.getStatusCode().value());
            else
                ambiguous(id);
        } catch (Exception e) {
            ambiguous(id);
        }
    }

    private void ambiguous(UUID id) {
        tx.executeWithoutResult(s -> db.update("UPDATE transfers SET status='PENDING_RECONCILIATION',updated_at=NOW() WHERE id=? AND status IN ('PENDING','PENDING_RECONCILIATION')", id));
    }

    private void complete(UUID id) {
        transition(id, "COMPLETED", null);
    }

    private void fail(UUID id, String reason) {
        transition(id, "FAILED", reason);
    }

    private void transition(UUID id, String next, String reason) {
        tx.executeWithoutResult(s -> {
            List<String> statuses = db.queryForList("SELECT status FROM transfers WHERE id=? FOR UPDATE", String.class, id);
            if (statuses.isEmpty() || !Set.of("PENDING", "PENDING_RECONCILIATION").contains(statuses.get(0)))
                return;
            String old = statuses.get(0);
            db.update("UPDATE transfers SET status=?,failure_code=?,updated_at=NOW() WHERE id=?", next, reason, id);
            db.update("INSERT INTO transfer_status_history(id,transfer_id,old_status,new_status,reason,changed_by) VALUES(?,?,?,?,?,'SYSTEM')", UUID.randomUUID(), id, old, next, reason);
            Row r = row(id);
            String topic = "COMPLETED".equals(next) ? "transfer.completed.v1" : "transfer.failed.v1";
            UUID eventId = UUID.randomUUID();
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("eventId", eventId.toString());
                event.put("eventType", topic);
                event.put("occurredAt", Instant.now().toString());
                event.put("transferId", id.toString());
                event.put("sourceAccountId", r.source().toString());
                event.put("destinationAccountId", r.dest().toString());
                event.put("amount", r.amount().toPlainString());
                event.put("currency", r.currency());
                event.put("requestedBy", r.owner().toString());
                db.update("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload) VALUES(?,'TRANSFER',?,?,CAST(? AS jsonb)) ON CONFLICT(aggregate_id,event_type) DO NOTHING", eventId, id, topic, mapper.writeValueAsString(event));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 7000, initialDelay = 10000)
    public void reconcile() {
        List<UUID> ids = db.queryForList("SELECT id FROM transfers WHERE status IN ('PENDING','PENDING_RECONCILIATION') AND created_at<NOW()-INTERVAL '5 seconds' ORDER BY created_at LIMIT 20", UUID.class);
        for (UUID id : ids) {
            try {
                process(id);
            } catch (Exception ignored) {/* next sweep retries same account operation ID */}
        }
    }

    public record NewTransfer(UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, String currency, String reference) {
    }

    private record Row(UUID id, UUID source, UUID dest, BigDecimal amount, String currency, String status, UUID owner) {
    }
}
