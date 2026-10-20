package com.demo.account.application.service;

import com.demo.account.common.Support;
import com.demo.account.domain.model.Account;
import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.application.port.in.AccountingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingServiceImpl implements AccountingService {

    private final JdbcTemplate db;
    private final Support support;
    private final RestClient remote;
    private final String customerUrl;
    private final boolean fundingEnabled;

    @Autowired
    AccountingServiceImpl(JdbcTemplate db,
                          Support support,
                          @Value("${services.customer-url}") String customerUrl,
                          @Value("${demo.funding-enabled:false}") boolean fundingEnabled) {
        this.db = db;
        this.support = support;
        this.customerUrl = customerUrl;
        this.fundingEnabled = fundingEnabled;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(4));
        this.remote = RestClient.builder().requestFactory(f).build();
    }

    @Override
    public Map<String, Object> operation(UUID id) {
        return db.query("SELECT operation_id,status FROM account_operations WHERE operation_id=?", (r, n) -> Map.<String, Object>of("transferId", r.getString(1), "status", r.getString(2)), id).stream().findFirst().orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND"));
    }

    @Override
    public Map<String, Object> open(OpenAccount c, Jwt jwt) {
        if (c.currency() == null || !Set.of("USD", "KHR").contains(c.currency()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY");
        Map<?, ?> customer;
        try {
            customer = remote.get().uri(customerUrl + "/internal/customers/" + c.customerId() + "/status")
                    .header("X-Service-Key", support.internalKey).retrieve().body(Map.class);
        } catch (Exception ex) {
            throw Support.fail(HttpStatus.BAD_GATEWAY, "CUSTOMER_VALIDATION_UNAVAILABLE");
        }
        if (customer == null || !"ACTIVE".equals(customer.get("status")))
            throw Support.fail(HttpStatus.CONFLICT, "CUSTOMER_NOT_ACTIVE");
        if (!jwt.getSubject().equals(customer.get("userId")) && !isAdmin(jwt))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_CUSTOMER_OWNER");
        UUID id = UUID.randomUUID();
        String accountNo = "AC" + id.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        db.update("INSERT INTO accounts(id,account_number,customer_id,owner_user_id,currency) VALUES(?,?,?,?,?)", id, accountNo, c.customerId(), UUID.fromString((String) customer.get("userId")), c.currency());
        db.update("INSERT INTO account_holders(account_id,customer_id) VALUES(?,?)", id, c.customerId());
        db.update("INSERT INTO account_balances(account_id) VALUES(?)", id);
        return view(id);
    }

    @Override
    public Map<String, Object> read(UUID id, Jwt jwt) {
        var m = view(id);
        assertOwner(m, jwt);
        return m;
    }

    @Override
    public List<Map<String, Object>> list(Jwt jwt) {
        return db.queryForList("SELECT id FROM accounts WHERE owner_user_id=? ORDER BY created_at DESC LIMIT 100", UUID.fromString(jwt.getSubject())).stream().map(m -> view((UUID) m.get("id"))).toList();
    }

    @Override
    public Map<String, Object> freeze(UUID id) {
        int n = db.update("UPDATE accounts SET status='FROZEN',version=version+1,updated_at=NOW() WHERE id=? AND account_type='CUSTOMER' AND status='ACTIVE'", id);
        if (n != 1)
            throw Support.fail(HttpStatus.CONFLICT, "ACCOUNT_FREEZE_INVALID");
        return view(id);
    }

    @Override
    public Map<String, Object> fund(UUID id, Funding c) {
        if (!fundingEnabled)
            throw Support.fail(HttpStatus.FORBIDDEN, "DEMO_FUNDING_DISABLED");
        Map<String, Object> dest = view(id);
        String currency = (String) dest.get("currency");
        UUID treasury = UUID.fromString("KHR".equals(currency) ? "00000000-0000-0000-0000-000000000002" : "00000000-0000-0000-0000-000000000001");
        return post(new TransferCommand(UUID.randomUUID(), treasury, id, c.amount(), currency, "SYSTEM"));
    }

    @Override
    public Map<String, Object> ledger(UUID operationId) {
        List<Map<String, Object>> entries = db.queryForList("SELECT account_id,entry_type,amount,currency FROM ledger_entries WHERE transaction_id=? ORDER BY entry_type", operationId);
        if (entries.isEmpty())
            throw Support.fail(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND");
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) {
            if ("DEBIT".equals(e.get("entry_type")))
                debit = debit.add((BigDecimal) e.get("amount"));
            else
                credit = credit.add((BigDecimal) e.get("amount"));
        }
        return Map.of("operationId", operationId, "entries", entries, "totalDebit", debit, "totalCredit", credit, "balanced", debit.compareTo(credit) == 0);
    }

    @Override
    public Map<String, Object> internalTransfer(String key, TransferCommand c) {
        support.internal(key);
        return post(c);
    }

    @Override
    public Map<String, Object> operation(String key, UUID id) {
        support.internal(key);
        return operation(id);
    }

    private Map<String, Object> view(UUID id) {
        return db.query(
                "SELECT a.id,a.account_number,a.customer_id,a.owner_user_id,a.currency,a.status,a.account_type,b.available_balance,b.ledger_balance FROM accounts a JOIN account_balances b ON b.account_id=a.id WHERE a.id=?", (r, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getString(1));
                    m.put("accountNumber", r.getString(2));
                    m.put("customerId", r.getString(3));
                    m.put("ownerUserId", r.getString(4));
                    m.put("currency", r.getString(5));
                    m.put("status", r.getString(6));
                    m.put("type", r.getString(7));
                    m.put("availableBalance", r.getBigDecimal(8));
                    m.put("ledgerBalance", r.getBigDecimal(9));
                    return m;
                }, id).stream().findFirst().orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt.getClaimAsStringList("roles").contains("ADMIN");
    }

    private void assertOwner(Map<String, Object> m, Jwt jwt) {
        if (!jwt.getSubject().equals(m.get("ownerUserId")) && !isAdmin(jwt))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
    }

    public static BigDecimal money(BigDecimal value, String currency) {
        if (value == null || currency == null || !Set.of("USD", "KHR").contains(currency) || value.signum() <= 0)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_OR_CURRENCY");
        int scale = "KHR".equals(currency) ? 0 : 2;
        if (value.scale() > scale && value.stripTrailingZeros().scale() > scale)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_MONEY_SCALE");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_MONEY_SCALE");
        }
    }

    public Account locked(UUID id) {
        return db.query("SELECT a.id,a.currency,a.status,a.account_type,a.owner_user_id,b.available_balance FROM accounts a JOIN account_balances b ON b.account_id=a.id WHERE a.id=? FOR UPDATE OF a,b", (r, n) -> new Account(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4), r.getObject(5, UUID.class), r.getBigDecimal(6)), id).stream().findFirst().orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
    }

    @Transactional
    public Map<String, Object> post(TransferCommand c) {
        if (c.transferId() == null || c.sourceAccountId() == null || c.destinationAccountId() == null || c.requestedBy() == null)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_FIELDS");
        if (c.sourceAccountId().equals(c.destinationAccountId()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT");
        BigDecimal amount = money(c.amount(), c.currency());
        // Lock in deterministic order to prevent deadlocks under concurrent opposing transfers.
        UUID first = c.sourceAccountId().compareTo(c.destinationAccountId()) < 0 ? c.sourceAccountId() : c.destinationAccountId();
        UUID second = first.equals(c.sourceAccountId()) ? c.destinationAccountId() : c.sourceAccountId();
        Account a = locked(first);
        Account b = locked(second);
        Account source = a.id().equals(c.sourceAccountId()) ? a : b;
        Account destination = a.id().equals(c.destinationAccountId()) ? a : b;
        List<Map<String, Object>> prev = db.query(
                "SELECT source_account_id,destination_account_id,amount,currency,requested_by FROM account_operations WHERE operation_id=?", (r, n) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("source", r.getObject(1, UUID.class));
                    m.put("destination", r.getObject(2, UUID.class));
                    m.put("amount", r.getBigDecimal(3));
                    m.put("currency", r.getString(4));
                    m.put("requestedBy", r.getString(5));
                    return m;
                }, c.transferId());
        if (!prev.isEmpty()) {
            Map<String, Object> p = prev.get(0);
            if (!p.get("source").equals(c.sourceAccountId()) || !p.get("destination").equals(c.destinationAccountId()) ||
                    ((BigDecimal) p.get("amount")).compareTo(amount) != 0 || !p.get("currency").equals(c.currency()) || !p.get("requestedBy").equals(c.requestedBy()))
                throw Support.fail(HttpStatus.CONFLICT, "OPERATION_ID_REUSED");
            return Map.of("transferId", c.transferId().toString(), "status", "COMMITTED", "duplicate", true);
        }
        if (!"ACTIVE".equals(source.status()) || !"ACTIVE".equals(destination.status()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");
        if (!c.currency().equals(source.currency()) || !c.currency().equals(destination.currency()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        if (!"SYSTEM".equals(source.type()) && !c.requestedBy().equals(String.valueOf(source.owner())))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
        if (!"SYSTEM".equals(source.type()) && source.balance().compareTo(amount) < 0)
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE");
        // The only account allowed to go negative is the demo treasury clearing account.
        if ("SYSTEM".equals(source.type()) && !"SYSTEM".equals(c.requestedBy()))
            throw Support.fail(HttpStatus.FORBIDDEN, "RESERVED_ACCOUNT");
        db.update("INSERT INTO account_operations(operation_id,source_account_id,destination_account_id,amount,currency,requested_by,status) VALUES(?,?,?,?,?,?,'COMMITTED')", c.transferId(), c.sourceAccountId(), c.destinationAccountId(), amount, c.currency(), c.requestedBy());
        db.update("UPDATE account_balances SET ledger_balance=ledger_balance-?,available_balance=available_balance-?,updated_at=NOW() WHERE account_id=?", amount, amount, c.sourceAccountId());
        db.update("UPDATE account_balances SET ledger_balance=ledger_balance+?,available_balance=available_balance+?,updated_at=NOW() WHERE account_id=?", amount, amount, c.destinationAccountId());
        for (var entry : List.of(Map.entry(c.sourceAccountId(), "DEBIT"), Map.entry(c.destinationAccountId(), "CREDIT"))) {
            db.update("INSERT INTO account_transactions(id,operation_id,account_id,transaction_type,amount,currency,reference_id) VALUES(?,?,?,?,?,?,?)", UUID.randomUUID(), c.transferId(), entry.getKey(), entry.getValue(), amount, c.currency(), c.transferId());
            db.update("INSERT INTO ledger_entries(id,transaction_id,account_id,entry_type,amount,currency) VALUES(?,?,?,?,?,?)", UUID.randomUUID(), c.transferId(), entry.getKey(), entry.getValue(), amount, c.currency());
        }
        db.update("UPDATE accounts SET version=version+1,updated_at=NOW() WHERE id IN (?,?)", c.sourceAccountId(), c.destinationAccountId());
        return Map.of("transferId", c.transferId().toString(), "status", "COMMITTED", "duplicate", false);
    }
}
