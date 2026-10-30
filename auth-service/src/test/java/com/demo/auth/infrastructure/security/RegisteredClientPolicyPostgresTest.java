package com.demo.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RegisteredClientPolicyPostgresTest {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private JdbcTemplate jdbc;
  private JdbcRegisteredClientRepository clients;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @BeforeEach
  void prepareRepository() {
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    jdbc.update("DELETE FROM oauth2_registered_client");
    clients = new JdbcRegisteredClientRepository(jdbc);
  }

  @Test
  void upgradingKnownDemoClientsPreservesIdentityCredentialsAndPkceWhileReplacingScopes() {
    String secret = "regression-only-client-secret";
    String secretHash = encoder.encode(secret);
    RegisteredClient browser = client("demo-browser", ClientAuthenticationMethod.NONE, null);
    RegisteredClient confidential =
        client("demo-bff", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, secretHash);
    clients.save(browser);
    clients.save(confidential);

    new AuthorizationServerConfig().registeredClientRepository(jdbc, encoder, secret);

    RegisteredClient updatedBrowser = clients.findByClientId("demo-browser");
    RegisteredClient updatedConfidential = clients.findByClientId("demo-bff");
    assertThat(updatedBrowser.getId()).isEqualTo(browser.getId());
    assertThat(updatedBrowser.getScopes())
        .contains("customer.write", "transfer.read", "notification.read")
        .doesNotContain("banking.admin", "audit.read", "obsolete.scope");
    assertThat(updatedBrowser.getClientAuthenticationMethods())
        .containsExactly(ClientAuthenticationMethod.NONE);
    assertThat(updatedConfidential.getId()).isEqualTo(confidential.getId());
    assertThat(updatedConfidential.getClientSecret()).isEqualTo(secretHash);
    assertThat(updatedConfidential.getScopes()).contains("banking.admin", "audit.read");
    for (RegisteredClient updated : new RegisteredClient[] {updatedBrowser, updatedConfidential}) {
      assertThat(updated.getRedirectUris()).containsExactly("https://example.test/callback");
      assertThat(updated.getAuthorizationGrantTypes())
          .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
      assertThat(updated.getClientSettings().isRequireProofKey()).isTrue();
      assertThat(updated.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    }
  }

  @Test
  void repeatedStartupDoesNotRehashUnchangedSecretAndExplicitRotationPreservesClientIdentity() {
    AuthorizationServerConfig config = new AuthorizationServerConfig();
    config.registeredClientRepository(jdbc, encoder, "initial-regression-secret");
    RegisteredClient original = clients.findByClientId("demo-bff");
    config.registeredClientRepository(jdbc, encoder, "initial-regression-secret");
    assertThat(clients.findByClientId("demo-bff").getClientSecret())
        .isEqualTo(original.getClientSecret());

    config.registeredClientRepository(jdbc, encoder, "rotated-regression-secret");
    RegisteredClient rotated = clients.findByClientId("demo-bff");
    assertThat(rotated.getId()).isEqualTo(original.getId());
    assertThat(encoder.matches("rotated-regression-secret", rotated.getClientSecret())).isTrue();
    assertThat(encoder.matches("initial-regression-secret", rotated.getClientSecret())).isFalse();
    assertThat(rotated.getClientSettings().isRequireProofKey()).isTrue();
  }

  @Test
  void oauthTokenIssuanceRejectsLockedUserAndAllowsExpiredLock() {
    UUID userId = UUID.randomUUID();
    String username = "locked-" + userId;
    jdbc.update(
        "INSERT INTO users(id, username, locked_until) VALUES(?,?,NOW()+INTERVAL '15 minutes')",
        userId,
        username);
    RegisteredClient client =
        client(
            "lockout-test",
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            encoder.encode("test-secret"));
    var claims = JwtClaimsSet.builder();
    var context =
        JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
            .registeredClient(client)
            .principal(new UsernamePasswordAuthenticationToken(username, "irrelevant"))
            .authorizedScopes(Set.of("account.read"))
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();
    var customizer = new AuthorizationServerConfig().jwtTokenCustomizer(jdbc, "api");
    assertThatThrownBy(() -> customizer.customize(context))
        .isInstanceOfSatisfying(
            OAuth2AuthenticationException.class,
            error -> assertThat(error.getError().getErrorCode()).isEqualTo("invalid_grant"));
    jdbc.update("UPDATE users SET locked_until=NOW()-INTERVAL '1 minute' WHERE id=?", userId);
    customizer.customize(context);
    assertThat(claims.build().getSubject()).isEqualTo(userId.toString());
  }

  private RegisteredClient client(
      String clientId, ClientAuthenticationMethod authentication, String secretHash) {
    RegisteredClient.Builder builder =
        RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientAuthenticationMethod(authentication)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.test/callback")
            .scopes(scopes -> scopes.addAll(Set.of("openid", "account.read", "obsolete.scope")))
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(true)
                    .build());
    if (secretHash != null) builder.clientSecret(secretHash);
    return builder.build();
  }
}
