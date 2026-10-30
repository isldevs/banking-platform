package com.demo.transfer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual transfer persistence and HTTP dispatch, with a controlled account-service boundary. */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(25)
class TransferPostingPostgresTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("transfer")
          .withUsername("transfer_test")
          .withPassword("transfer_test");

  private final UUID owner = UUID.randomUUID();
  private final UUID sourceAccount = UUID.randomUUID();
  private final UUID destinationAccount = UUID.randomUUID();
  private final ObjectMapper json = new ObjectMapper();
  private final ObjectMapper receiverJson =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final AtomicInteger accountRequests = new AtomicInteger();
  private final AtomicInteger operationLookups = new AtomicInteger();
  private final AtomicReference<Throwable> fixtureFailure = new AtomicReference<>();
  private final AtomicReference<PostHandler> posting = new AtomicReference<>();
  private final AtomicReference<UUID> committedOperation = new AtomicReference<>();
  private HikariDataSource dataSource;
  private JdbcTemplate db;
  private HttpServer server;
  private ExecutorService httpWorkers;
  private ExecutorService callers;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = pool(true);
    db = new JdbcTemplate(dataSource);
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    db.execute(
        "TRUNCATE transfers,transfer_items,idempotency_keys,transfer_status_history,outbox_events CASCADE");
    httpWorkers = Executors.newFixedThreadPool(4);
    callers = Executors.newFixedThreadPool(4);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(httpWorkers);
    server.createContext(
        "/internal/accounts/transfer",
        exchange -> {
          try {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(
                "test-service-key", exchange.getRequestHeaders().getFirst("X-Service-Key"));
            byte[] request = exchange.getRequestBody().readAllBytes();
            JsonNode payload = json.readTree(request);
            // Naming strategies do not rename Map keys; require the actual receiver's wire names.
            assertEquals(6, payload.size());
            assertFalse(payload.has("transferId"));
            ReceiverCommand command = receiverJson.readValue(request, ReceiverCommand.class);
            assertNotNull(command.transferId());
            assertEquals(sourceAccount, command.sourceAccountId());
            assertEquals(destinationAccount, command.destinationAccountId());
            assertEquals(new BigDecimal("10.00"), command.amount());
            assertEquals("USD", command.currency());
            assertEquals(owner.toString(), command.requestedBy());
            accountRequests.incrementAndGet();
            posting.get().handle(exchange, command);
          } catch (Throwable failure) {
            fixtureFailure.compareAndSet(null, failure);
            respond(exchange, 500, "{}");
          } finally {
            exchange.close();
          }
        });
    server.createContext(
        "/internal/accounts/operations/",
        exchange -> {
          try {
            operationLookups.incrementAndGet();
            UUID id =
                UUID.fromString(
                    exchange
                        .getRequestURI()
                        .getPath()
                        .replace("/internal/accounts/operations/", ""));
            if (id.equals(committedOperation.get()))
              respond(exchange, 200, "{\"transfer_id\":\"" + id + "\",\"status\":\"COMMITTED\"}");
            else respond(exchange, 404, "{\"code\":\"OPERATION_NOT_FOUND\"}");
          } finally {
            exchange.close();
          }
        });
    server.start();
    posting.set(
        (exchange, command) -> {
          committedOperation.set(command.transferId());
          respond(exchange, 200, "{}");
        });
  }

  @AfterEach
  void tearDown() {
    if (server != null) server.stop(0);
    if (callers != null) callers.shutdownNow();
    if (httpWorkers != null) httpWorkers.shutdownNow();
    if (dataSource != null) dataSource.close();
    if (fixtureFailure.get() != null)
      throw new AssertionError("Account fixture failed", fixtureFailure.get());
  }

  @Test
  void concurrentSameKeyCreatesAcrossInstancesDispatchOnceAndCommitOneCompletedEvent()
      throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    posting.set(
        (exchange, command) -> {
          entered.countDown();
          assertTrue(finish.await(10, TimeUnit.SECONDS));
          committedOperation.set(command.transferId());
          respond(exchange, 200, "{}");
        });
    TransferService firstInstance = service(dataSource);
    TransferService secondInstance = service(dataSource);
    Future<Map<String, Object>> first =
        callers.submit(() -> firstInstance.create(request(), owner, "same-client-key"));
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    Map<String, Object> retry =
        callers
            .submit(() -> secondInstance.create(request(), owner, "same-client-key"))
            .get(3, TimeUnit.SECONDS);
    assertEquals("PENDING", retry.get("status"));
    assertEquals(1, accountRequests.get());
    finish.countDown();
    Map<String, Object> complete = first.get(5, TimeUnit.SECONDS);
    assertEquals(retry.get("id"), complete.get("id"));
    assertEquals("COMPLETED", complete.get("status"));
    assertEquals(complete, secondInstance.create(request(), owner, "same-client-key"));
    assertEquals(1, accountRequests.get());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM transfers", Integer.class));
    assertEquals(List.of("transfer.completed.v1"), events());
    assertTrue(db.queryForObject("SELECT dispatch_attempted FROM transfers", Boolean.class));
  }

  @Test
  void firstDefinitiveRejectionProducesOneFailedEventAndIsNotRetried() {
    posting.set(
        (exchange, command) -> respond(exchange, 422, "{\"code\":\"INSUFFICIENT_BALANCE\"}"));
    TransferService service = service(dataSource);
    Map<String, Object> result = service.create(request(), owner, "rejected-client-key");
    assertEquals("FAILED", result.get("status"));
    service.process(UUID.fromString((String) result.get("id")));
    assertEquals(1, accountRequests.get());
    assertEquals(0, operationLookups.get());
    assertEquals(List.of("transfer.failed.v1"), events());
  }

  @Test
  void timedOutEarlierPostingCanCommitAfterRetryRejectionWithoutFalseFailure() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch allowCommit = new CountDownLatch(1);
    CountDownLatch committed = new CountDownLatch(1);
    posting.set(
        (exchange, command) -> {
          if (accountRequests.get() == 1) {
            entered.countDown();
            assertTrue(allowCommit.await(12, TimeUnit.SECONDS));
            committedOperation.set(command.transferId());
            committed.countDown();
            try {
              respond(exchange, 200, "{}");
            } catch (IOException clientAlreadyTimedOut) {
              // A socket timeout does not cancel the account database transaction.
            }
          } else respond(exchange, 422, "{\"code\":\"ACCOUNT_NOT_ACTIVE\"}");
        });
    TransferService firstInstance = service(dataSource);
    Future<Map<String, Object>> first =
        callers.submit(() -> firstInstance.create(request(), owner, "timeout-client-key"));
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    Map<String, Object> pending = first.get(7, TimeUnit.SECONDS);
    UUID id = UUID.fromString((String) pending.get("id"));
    assertEquals("PENDING_RECONCILIATION", pending.get("status"));
    TransferService recoveryInstance = service(dataSource);
    recoveryInstance.process(id);
    assertEquals("PENDING_RECONCILIATION", recoveryInstance.view(id, owner, false).get("status"));
    assertEquals(2, accountRequests.get());
    assertTrue(events().isEmpty());
    allowCommit.countDown();
    assertTrue(committed.await(3, TimeUnit.SECONDS));
    recoveryInstance.process(id);
    assertEquals("COMPLETED", recoveryInstance.view(id, owner, false).get("status"));
    assertEquals(2, accountRequests.get(), "An authoritative commit must not be dispatched again");
    assertEquals(List.of("transfer.completed.v1"), events());
  }

  @Test
  void reconciliationRotatesPastAnOldAmbiguousBatchWithoutStarvingLaterTransfers() {
    Set<UUID> attempted = ConcurrentHashMap.newKeySet();
    posting.set(
        (exchange, command) -> {
          attempted.add(command.transferId());
          respond(exchange, 422, "{\"code\":\"ACCOUNT_NOT_ACTIVE\"}");
        });
    for (int index = 0; index < 25; index++) {
      UUID id = UUID.randomUUID();
      db.update(
          "INSERT INTO transfers(id,transfer_reference,source_account_id,destination_account_id,amount,currency,status,requested_by,created_at,updated_at,dispatch_attempted) "
              + "VALUES(?,?,?,?,10.00,'USD','PENDING_RECONCILIATION',?,NOW()-INTERVAL '2 minutes'+?*INTERVAL '1 second',NOW()-INTERVAL '2 minutes'+?*INTERVAL '1 second',true)",
          id,
          "TRF-" + id,
          sourceAccount,
          destinationAccount,
          owner,
          index,
          index);
    }
    TransferService service = service(dataSource);
    service.reconcile();
    assertEquals(20, attempted.size());
    assertEquals(20, accountRequests.get());
    // Make the first batch eligible again without sleeping. Its more recent attempt
    // timestamp must put it behind the five untouched, older rows in the next batch.
    db.update(
        "UPDATE transfers SET updated_at=NOW()-INTERVAL '10 seconds' WHERE updated_at>NOW()-INTERVAL '5 seconds'");
    service.reconcile();
    assertEquals(25, attempted.size());
    assertEquals(40, accountRequests.get());
    assertEquals(
        25,
        db.queryForObject(
            "SELECT count(*) FROM transfers WHERE status='PENDING_RECONCILIATION'", Integer.class));
    assertTrue(events().isEmpty());
  }

  @Test
  void attemptedMarkerIsCommittedBeforeHttpEvenWithAutoCommitDisabledInPool() {
    posting.set(
        (exchange, command) -> {
          assertTrue(
              db.queryForObject(
                  "SELECT dispatch_attempted FROM transfers WHERE id=?",
                  Boolean.class,
                  command.transferId()));
          committedOperation.set(command.transferId());
          respond(exchange, 200, "{}");
        });
    try (HikariDataSource manualCommitPool = pool(false)) {
      Map<String, Object> result =
          service(manualCommitPool).create(request(), owner, "manual-autocommit-key");
      assertEquals("COMPLETED", result.get("status"));
    }
    assertEquals(List.of("transfer.completed.v1"), events());
  }

  @Test
  void lostDispatchSessionCannotPersistTerminalFailureAndRecoveryUsesCommittedOperation()
      throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch respond = new CountDownLatch(1);
    posting.set(
        (exchange, command) -> {
          entered.countDown();
          assertTrue(respond.await(10, TimeUnit.SECONDS));
          committedOperation.set(command.transferId());
          respond(exchange, 200, "{}");
        });
    TransferService firstInstance = service(dataSource);
    Future<Map<String, Object>> first =
        callers.submit(() -> firstInstance.create(request(), owner, "session-loss-key"));
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    UUID id = db.queryForObject("SELECT id FROM transfers", UUID.class);
    String lockKey = "transfer-dispatch:" + id;
    Integer session =
        db.queryForObject(
            "SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted "
                + "AND classid::bigint=((hashtextextended(?,0)>>32)&4294967295) "
                + "AND objid::bigint=(hashtextextended(?,0)&4294967295)",
            Integer.class,
            lockKey,
            lockKey);
    assertTrue(db.queryForObject("SELECT pg_terminate_backend(?)", Boolean.class, session));
    respond.countDown();
    assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
    assertTrue(
        db.queryForObject(
            "SELECT dispatch_attempted FROM transfers WHERE id=?", Boolean.class, id));
    assertEquals("PENDING", firstInstance.view(id, owner, false).get("status"));
    assertTrue(events().isEmpty());
    service(dataSource).process(id);
    assertEquals("COMPLETED", firstInstance.view(id, owner, false).get("status"));
    assertEquals(1, accountRequests.get());
    assertEquals(List.of("transfer.completed.v1"), events());
  }

  private TransferService service(HikariDataSource source) {
    return new TransferService(
        new JdbcTemplate(source),
        new DataSourceTransactionManager(source),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "test-service-key",
        json);
  }

  private HikariDataSource pool(boolean autoCommit) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    config.setMaximumPoolSize(5);
    config.setAutoCommit(autoCommit);
    return new HikariDataSource(config);
  }

  private TransferService.NewTransfer request() {
    return new TransferService.NewTransfer(
        sourceAccount, destinationAccount, new BigDecimal("10.00"), "USD", "reference");
  }

  private List<String> events() {
    return db.queryForList(
        "SELECT event_type FROM outbox_events ORDER BY created_at", String.class);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  private record ReceiverCommand(
      UUID transferId,
      UUID sourceAccountId,
      UUID destinationAccountId,
      BigDecimal amount,
      String currency,
      String requestedBy) {}

  @FunctionalInterface
  private interface PostHandler {
    void handle(HttpExchange exchange, ReceiverCommand command) throws Exception;
  }
}
