package com.demo.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class OAuthScopeAuthorizationTest {
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final List<String> ADMIN_PERMISSIONS =
      List.of(
          "ACCOUNT_READ",
          "ACCOUNT_OPEN",
          "TRANSFER_CREATE",
          "USER_MANAGE",
          "KYC_APPROVE",
          "ACCOUNT_FUND",
          "AUDIT_READ");

  @Test
  void readOnlyGrantCannotBecomeWriteAuthoritiesOrBypassOwnershipAsAdministrator() {
    Jwt jwt = issue(Set.of("openid", "account.read"), ADMIN_PERMISSIONS, List.of("ADMIN"));

    assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("ACCOUNT_READ");
    assertThat(jwt.getClaimAsStringList("roles")).doesNotContain("ADMIN");
    assertThat(new SecurityConfig().jwtAuthenticationConverter().convert(jwt).getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactly("ACCOUNT_READ");
  }

  @Test
  void identityOnlyGrantHasNoBankingPermissions() {
    Jwt jwt = issue(Set.of("openid", "profile"), ADMIN_PERMISSIONS, List.of("ADMIN"));

    assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
    assertThat(jwt.getClaimAsStringList("roles")).isEmpty();
  }

  @Test
  void customTokenClaimsUseJdbcAuthorizationSafeCollectionTypes() {
    Jwt jwt = issue(Set.of("openid", "account.read"), ADMIN_PERMISSIONS, List.of("USER"));

    assertThat(jwt.getAudience()).isExactlyInstanceOf(ArrayList.class);
    assertThat(jwt.getClaimAsStringList("permissions")).isExactlyInstanceOf(ArrayList.class);
    assertThat(jwt.getClaimAsStringList("roles")).isExactlyInstanceOf(ArrayList.class);
  }

  @Test
  void grantedScopeCannotInventPermissionsOrAdministratorRoleForOrdinaryUser() {
    Jwt jwt =
        issue(
            Set.of("openid", "account.read", "account.write", "transfer.create", "banking.admin"),
            List.of("ACCOUNT_READ"),
            List.of("USER"));

    assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("ACCOUNT_READ");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
  }

  @Test
  void administratorRequiresExplicitAdministrativeDelegation() {
    Jwt jwt =
        issue(
            Set.of("openid", "account.read", "banking.admin"), ADMIN_PERMISSIONS, List.of("ADMIN"));

    assertThat(jwt.getClaimAsStringList("permissions"))
        .containsExactly(
            "ACCOUNT_FUND", "ACCOUNT_READ", "AUDIT_READ", "KYC_APPROVE", "USER_MANAGE");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
    assertThat(jwt.getClaimAsStringList("permissions"))
        .doesNotContain("ACCOUNT_OPEN", "TRANSFER_CREATE");
  }

  @Test
  void publicClientCannotGainAdministrativeDelegationFromStaleAuthorizationScopes() {
    Jwt jwt =
        issue(
            Set.of("openid", "account.read", "banking.admin"),
            Set.of("openid", "account.read"),
            ADMIN_PERMISSIONS,
            List.of("ADMIN"),
            AuthorizationGrantType.AUTHORIZATION_CODE,
            ClientAuthenticationMethod.NONE);

    assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("ACCOUNT_READ");
    assertThat(jwt.getClaimAsStringList("roles")).isEmpty();
    assertThat(jwt.getClaimAsStringList("scope")).doesNotContain("banking.admin", "audit.read");
  }

  @Test
  void publicClientCannotDelegateAdministrationEvenWhenMisconfiguredToRegisterTheScope() {
    Set<String> scopes = Set.of("openid", "account.read", "banking.admin", "audit.read");
    Jwt jwt =
        issue(
            scopes,
            scopes,
            ADMIN_PERMISSIONS,
            List.of("ADMIN"),
            AuthorizationGrantType.AUTHORIZATION_CODE,
            ClientAuthenticationMethod.NONE);

    assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("ACCOUNT_READ");
    assertThat(jwt.getClaimAsStringList("roles")).isEmpty();
  }

  @Test
  void refreshTokenReappliesScopeLimitsToCurrentUserPermissions() {
    Jwt jwt =
        issue(
            Set.of("openid", "account.read"),
            Set.of("openid", "account.read", "account.write", "banking.admin"),
            ADMIN_PERMISSIONS,
            List.of("ADMIN"),
            AuthorizationGrantType.REFRESH_TOKEN);

    assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("ACCOUNT_READ");
    assertThat(jwt.getClaimAsStringList("roles")).isEmpty();
  }

  @Test
  void disabledOrDeletedUserCannotReceiveAnAccessTokenOnRefresh() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq("disabled-user")))
        .thenReturn(List.of());
    RegisteredClient client =
        RegisteredClient.withId("confidential-client")
            .clientId("confidential-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope("account.read")
            .build();
    JwtEncodingContext context =
        JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
            .registeredClient(client)
            .principal(new UsernamePasswordAuthenticationToken("disabled-user", "irrelevant"))
            .authorizedScopes(Set.of("account.read"))
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();

    assertThatThrownBy(
            () ->
                new AuthorizationServerConfig().jwtTokenCustomizer(jdbc, "api").customize(context))
        .isInstanceOfSatisfying(
            OAuth2AuthenticationException.class,
            error ->
                assertThat(error.getError().getErrorCode())
                    .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
  }

  private Jwt issue(Set<String> scopes, List<String> permissions, List<String> roles) {
    return issue(scopes, scopes, permissions, roles, AuthorizationGrantType.AUTHORIZATION_CODE);
  }

  private Jwt issue(
      Set<String> scopes,
      Set<String> registeredScopes,
      List<String> permissions,
      List<String> roles,
      AuthorizationGrantType grantType) {
    return issue(
        scopes,
        registeredScopes,
        permissions,
        roles,
        grantType,
        ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
  }

  private Jwt issue(
      Set<String> scopes,
      Set<String> registeredScopes,
      List<String> permissions,
      List<String> roles,
      AuthorizationGrantType grantType,
      ClientAuthenticationMethod authenticationMethod) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq("alice")))
        .thenReturn(List.of(USER_ID));
    when(jdbc.queryForList(contains("SELECT DISTINCT p.code"), eq(String.class), eq(USER_ID)))
        .thenReturn(permissions);
    when(jdbc.queryForList(contains("SELECT r.name"), eq(String.class), eq(USER_ID)))
        .thenReturn(roles);
    RegisteredClient client =
        RegisteredClient.withId("scoped-client")
            .clientId("scoped-client")
            .clientAuthenticationMethod(authenticationMethod)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://example.test/callback")
            .scopes(values -> values.addAll(registeredScopes))
            .build();
    JwtClaimsSet.Builder claims = JwtClaimsSet.builder().claim("scope", scopes);
    JwtEncodingContext context =
        JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
            .registeredClient(client)
            .principal(new UsernamePasswordAuthenticationToken("alice", "irrelevant"))
            .authorizedScopes(scopes)
            .authorizationGrantType(grantType)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();

    new AuthorizationServerConfig().jwtTokenCustomizer(jdbc, "api").customize(context);

    return Jwt.withTokenValue("test-access-token")
        .header("alg", "RS256")
        .claims(values -> values.putAll(claims.build().getClaims()))
        .build();
  }
}
