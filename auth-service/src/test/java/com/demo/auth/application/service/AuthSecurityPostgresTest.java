package com.demo.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.auth.api.dto.request.Login;
import com.demo.auth.api.dto.request.Refresh;
import com.demo.auth.application.port.out.TokenService;
import com.demo.auth.common.LoginAudit;
import java.time.Duration;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AuthSecurityPostgresTest {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbc;
  private AuthServiceImpl service;
  private TokenService tokens;
  private UUID userId;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @BeforeEach
  void prepare() {
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    jdbc.execute("TRUNCATE users CASCADE");
    jdbc.execute("TRUNCATE login_attempts");
    tokens = mock(TokenService.class);
    when(tokens.access(
            any(), anyString(),
            anyList(), anyList()))
        .thenReturn("test-access-token");
    service = new AuthServiceImpl(tokens, jdbc, Duration.ofMinutes(15));
    userId = UUID.randomUUID();
    jdbc.update("INSERT INTO users(id, username) VALUES(?,?)", userId, "alice");
    jdbc.update(
        "INSERT INTO user_credentials(id,user_id,password_hash) VALUES(?,?,?)",
        UUID.randomUUID(),
        userId,
        new BCryptPasswordEncoder(4).encode("valid-test-password"));
  }

  @Test
  void oversizedUserAgentCannotBypassFailedPasswordLockout() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("User-Agent", "x".repeat(1000));
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> service.login(new Login("alice", "incorrect"), request))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_login_count FROM users WHERE id=?", Integer.class, userId))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT locked_until > NOW() FROM users WHERE id=?", Boolean.class, userId))
        .isTrue();
    assertThat(jdbc.queryForList("SELECT length(user_agent) FROM login_attempts", Integer.class))
        .hasSize(5)
        .containsOnly(400);
    assertThatThrownBy(() -> service.login(new Login("alice", "valid-test-password"), request))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(tokens);
  }

  @Test
  void sharedBrowserAndApiAuditRecorderAcceptsOversizedAndMissingMetadata() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("User-Agent", "x".repeat(1000));
    request.setRemoteAddr("a".repeat(100));
    LoginAudit.record(jdbc, null, "u".repeat(200), false, request);
    LoginAudit.record(jdbc, userId, "alice", true, new MockHttpServletRequest());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempts", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT length(username) FROM login_attempts WHERE user_id IS NULL", Integer.class))
        .isEqualTo(100);
    assertThat(
            jdbc.queryForObject(
                "SELECT length(ip_address) FROM login_attempts WHERE user_id IS NULL",
                Integer.class))
        .isEqualTo(80);
  }

  @Test
  void lockedUserCannotRefreshLegacyToken() {
    String refresh = (String) service.tokens(service.lookup("alice")).get("refresh_token");
    jdbc.update("UPDATE users SET locked_until=NOW()+INTERVAL '15 minutes' WHERE id=?", userId);
    clearInvocations(tokens);
    assertThatThrownBy(() -> service.refresh(new Refresh(refresh)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    verifyNoInteractions(tokens);
  }
}
