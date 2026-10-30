package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.application.mapper.AccountMapperImpl;
import com.demo.account.application.port.in.AccountingService;
import com.demo.account.application.service.AccountingServiceImpl;
import com.demo.account.common.Support;
import com.demo.account.infrastructure.config.FundingProperties;
import com.demo.account.infrastructure.config.SettlementAccountProperties;
import com.demo.account.infrastructure.security.LegacySecurityProperties;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = AccountSettlementPostgresTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.config.name=account-settlement-test",
      "spring.cloud.config.enabled=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "services.customer-url=http://unused-customer-service"
    })
class AccountSettlementPostgresTest {
  private static final UUID CLEARING = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String SERVICE_KEY = "integration-test-service-key-at-least-24";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("account_settlement")
          .withUsername("account_test")
          .withPassword("account_test");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired AccountingService service;
  @Autowired JdbcTemplate db;
  @Autowired DataSource dataSource;

  @Test
  void sameInstructionRetryPostsExactlyOnce() {
    Instruction instruction = instruction("PENDING_FUNDING");
    String reference = UUID.randomUUID().toString();

    service.postFundingInstruction(SERVICE_KEY, instruction.id(), " " + reference + " ");
    service.postFundingInstruction(SERVICE_KEY, instruction.id(), reference);

    assertEquals(new BigDecimal("100.00"), balance(instruction.account()));
    assertEquals(1, count("account_operations", "operation_id", instruction.id()));
    assertEquals(2, count("ledger_entries", "transaction_id", instruction.id()));
    assertEquals("POSTED", fundingValue(instruction.id(), "status"));
    assertEquals(reference, fundingValue(instruction.id(), "external_reference"));
  }

  @Test
  void referenceCannotCreditAnotherInstruction() {
    Instruction first = instruction("PENDING_FUNDING");
    Instruction second = instruction("PENDING_FUNDING");
    String reference = UUID.randomUUID().toString();
    service.postFundingInstruction(SERVICE_KEY, first.id(), reference);

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.postFundingInstruction(SERVICE_KEY, second.id(), reference));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertEquals("EXTERNAL_REFERENCE_ALREADY_USED", failure.getReason());
    assertEquals(new BigDecimal("0.00"), balance(second.account()));
    assertEquals(0, count("account_operations", "operation_id", second.id()));
    assertEquals("PENDING_SETTLEMENT", fundingValue(second.id(), "status"));
    assertNull(fundingValue(second.id(), "external_reference"));
  }

  @Test
  void concurrentConsumptionHasOneWinnerAndOneFinancialPosting() throws Exception {
    Instruction first = instruction("PENDING_FUNDING");
    Instruction second = instruction("PENDING_FUNDING");
    String reference = UUID.randomUUID().toString();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> outcomes =
          List.of(
              workers.submit(() -> settle(first.id(), reference, ready, start)),
              workers.submit(() -> settle(second.id(), reference, ready, start)));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> statuses =
          List.of(
              outcomes.get(0).get(15, TimeUnit.SECONDS), outcomes.get(1).get(15, TimeUnit.SECONDS));
      assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
      assertEquals(
          new BigDecimal("100.00"), balance(first.account()).add(balance(second.account())));
      assertEquals(
          1,
          count("account_operations", "operation_id", first.id())
              + count("account_operations", "operation_id", second.id()));
      assertEquals(
          2,
          count("ledger_entries", "transaction_id", first.id())
              + count("ledger_entries", "transaction_id", second.id()));
    } finally {
      start.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void rejectedPostingRollsBackSettlementReservation() {
    Instruction instruction = instruction("FROZEN");
    String reference = UUID.randomUUID().toString();

    assertThrows(
        ResponseStatusException.class,
        () -> service.postFundingInstruction(SERVICE_KEY, instruction.id(), reference));

    assertNull(fundingValue(instruction.id(), "external_reference"));
    assertEquals("PENDING_SETTLEMENT", fundingValue(instruction.id(), "status"));
    assertEquals(0, count("account_operations", "operation_id", instruction.id()));
    assertEquals(new BigDecimal("0.00"), balance(instruction.account()));
  }

  @Test
  void databaseRejectsDuplicateReferencesFromOtherWriters() {
    Instruction first = instruction("PENDING_FUNDING");
    Instruction second = instruction("PENDING_FUNDING");
    String reference = UUID.randomUUID().toString();
    service.postFundingInstruction(SERVICE_KEY, first.id(), reference);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            db.update(
                "UPDATE funding_instructions SET external_reference=? WHERE id=?",
                reference,
                second.id()));
  }

  @Test
  void migrationStopsRatherThanDeletingDuplicateFinancialHistory() {
    String schema = "duplicate_history";
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .target("4")
        .load()
        .migrate();
    UUID account = UUID.randomUUID();
    db.update(
        "INSERT INTO duplicate_history.accounts(id,account_number,currency,account_type) VALUES(?,?,'USD','CUSTOMER')",
        account,
        account.toString());
    for (int i = 0; i < 2; i++)
      db.update(
          "INSERT INTO duplicate_history.funding_instructions(id,account_id,amount,currency,channel,status,idempotency_key,external_reference) VALUES(?,?,100,'USD','BANK_TRANSFER','POSTED',?,'DUPLICATE')",
          UUID.randomUUID(),
          account,
          UUID.randomUUID().toString());

    FlywayException failure =
        assertThrows(
            FlywayException.class,
            () ->
                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate());

    assertTrue(failure.getMessage().contains("reconcile affected funding instructions"));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM duplicate_history.funding_instructions", Integer.class));
  }

  @Test
  void fundingWaitsForFreezeAndCannotOverwriteCommittedFrozenState() throws Exception {
    Instruction instruction = instruction("ACTIVE");
    ExecutorService workers = Executors.newSingleThreadExecutor();
    try (Connection freeze = dataSource.getConnection()) {
      freeze.setAutoCommit(false);
      try (PreparedStatement update =
          freeze.prepareStatement("UPDATE accounts SET status='FROZEN' WHERE id=?")) {
        update.setObject(1, instruction.account());
        update.executeUpdate();
      }
      Future<Integer> funding =
          workers.submit(
              () -> {
                try {
                  service.fund(instruction.account(), new Funding(new BigDecimal("25.00")));
                  return 200;
                } catch (ResponseStatusException failure) {
                  return failure.getStatusCode().value();
                }
              });
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      boolean waiting = false;
      while (System.nanoTime() < deadline) {
        waiting =
            db.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%accounts%'",
                    Integer.class)
                > 0;
        if (waiting) break;
        Thread.sleep(25);
      }
      assertTrue(waiting, "Funding should be waiting on the account lifecycle lock");
      freeze.commit();
      assertEquals(422, funding.get(10, TimeUnit.SECONDS));
      assertEquals(
          "FROZEN",
          db.queryForObject(
              "SELECT status FROM accounts WHERE id=?", String.class, instruction.account()));
      assertEquals(new BigDecimal("0.00"), balance(instruction.account()));
    } finally {
      workers.shutdownNow();
    }
  }

  private int settle(UUID instruction, String reference, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    try {
      service.postFundingInstruction(SERVICE_KEY, instruction, reference);
      return 200;
    } catch (ResponseStatusException failure) {
      return failure.getStatusCode().value();
    }
  }

  private Instruction instruction(String status) {
    UUID account = UUID.randomUUID();
    UUID instruction = UUID.randomUUID();
    db.update(
        "INSERT INTO accounts(id,account_number,currency,status,account_type) VALUES(?,?,'USD',?,'CUSTOMER')",
        account,
        account.toString(),
        status);
    db.update("INSERT INTO account_balances(account_id) VALUES(?)", account);
    db.update(
        "INSERT INTO funding_instructions(id,account_id,amount,currency,channel,status,idempotency_key) VALUES(?,?,100,'USD','BANK_TRANSFER','PENDING_SETTLEMENT',?)",
        instruction,
        account,
        instruction.toString());
    return new Instruction(instruction, account);
  }

  private BigDecimal balance(UUID account) {
    return db.queryForObject(
        "SELECT ledger_balance FROM account_balances WHERE account_id=?",
        BigDecimal.class,
        account);
  }

  private int count(String table, String column, UUID id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class, id);
  }

  private String fundingValue(UUID instruction, String column) {
    return db.queryForObject(
        "SELECT " + column + " FROM funding_instructions WHERE id=?", String.class, instruction);
  }

  private record Instruction(UUID id, UUID account) {}

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.demo.account.domain.model")
  @EnableJpaRepositories("com.demo.account.infrastructure.persistence")
  @Import({AccountingServiceImpl.class, AccountMapperImpl.class, Support.class})
  static class TestApplication {
    @Bean
    LegacySecurityProperties legacySecurity() {
      LegacySecurityProperties properties = new LegacySecurityProperties();
      properties.setInternalKey(SERVICE_KEY);
      return properties;
    }

    @Bean
    FundingProperties funding() {
      return new FundingProperties(true, Map.of("USD", CLEARING));
    }

    @Bean
    SettlementAccountProperties settlement() {
      return new SettlementAccountProperties(Map.of("BANK_TRANSFER", Map.of("USD", CLEARING)));
    }
  }
}
