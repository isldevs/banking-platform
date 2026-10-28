package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AccountOpeningConcurrencyPostgresTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("account")
          .withUsername("account_test")
          .withPassword("account_test");

  private static final String LOCK_SQL =
      "SELECT 1 FROM pg_advisory_xact_lock("
          + "hashtextextended(CAST(? AS text) || ':' || ?, 0))";

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void concurrentRetryWaitsForFirstOpeningCommitAndSeesItsRequest() throws Exception {
    UUID principal = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    UUID account = UUID.randomUUID();
    String key = "same-principal-key";
    createAccount(account, principal);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection first = connect(); Connection retry = connect()) {
      first.setAutoCommit(false);
      retry.setAutoCommit(false);

      lock(first, principal, key);
      saveOpening(first, UUID.randomUUID(), principal, customer, account, key);
      CountDownLatch attemptingLock = new CountDownLatch(1);
      Future<Integer> result =
          executor.submit(
              () -> {
                attemptingLock.countDown();
                lock(retry, principal, key);
                return countOpenings(retry, principal, key);
              });

      assertTrue(attemptingLock.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> result.get(300, TimeUnit.MILLISECONDS));

      first.commit();
      assertEquals(1, result.get(15, TimeUnit.SECONDS));
      retry.commit();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void sameClientKeyIsAllowedForDifferentPrincipals() throws Exception {
    UUID firstPrincipal = UUID.randomUUID();
    UUID secondPrincipal = UUID.randomUUID();
    UUID firstAccount = UUID.randomUUID();
    UUID secondAccount = UUID.randomUUID();
    createAccount(firstAccount, firstPrincipal);
    createAccount(secondAccount, secondPrincipal);

    try (Connection connection = connect()) {
      saveOpening(
          connection,
          UUID.randomUUID(),
          firstPrincipal,
          UUID.randomUUID(),
          firstAccount,
          "shared-client-key");
      saveOpening(
          connection,
          UUID.randomUUID(),
          secondPrincipal,
          UUID.randomUUID(),
          secondAccount,
          "shared-client-key");
      assertEquals(1, countOpenings(connection, firstPrincipal, "shared-client-key"));
      assertEquals(1, countOpenings(connection, secondPrincipal, "shared-client-key"));
    }
  }

  private static void createAccount(UUID accountId, UUID principal) throws SQLException {
    try (Connection connection = connect();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO accounts(id, account_number, owner_user_id, currency, status, account_type) "
                    + "VALUES (?, ?, ?, 'USD', 'ACTIVE', 'CUSTOMER')")) {
      statement.setObject(1, accountId);
      statement.setString(2, "TEST-" + accountId);
      statement.setObject(3, principal);
      statement.executeUpdate();
    }
  }

  private static void saveOpening(
      Connection connection, UUID id, UUID principal, UUID customer, UUID account, String key)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO account_opening_requests "
                + "(id, principal_id, idempotency_key, customer_id, account_id, product_code, currency) "
                + "VALUES (?, ?, ?, ?, ?, 'BASIC-USD', 'USD')")) {
      statement.setObject(1, id);
      statement.setObject(2, principal);
      statement.setString(3, key);
      statement.setObject(4, customer);
      statement.setObject(5, account);
      statement.executeUpdate();
    }
  }

  private static void lock(Connection connection, UUID principal, String key) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL)) {
      statement.setObject(1, principal);
      statement.setString(2, key);
      try (ResultSet row = statement.executeQuery()) {
        assertTrue(row.next());
        assertEquals(1, row.getInt(1));
      }
    }
  }

  private static int countOpenings(Connection connection, UUID principal, String key)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM account_opening_requests "
                + "WHERE principal_id = ? AND idempotency_key = ?")) {
      statement.setObject(1, principal);
      statement.setString(2, key);
      try (ResultSet row = statement.executeQuery()) {
        assertTrue(row.next());
        return row.getInt(1);
      }
    }
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
