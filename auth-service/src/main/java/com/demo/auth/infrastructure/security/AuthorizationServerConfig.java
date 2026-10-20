package com.demo.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);
        RegisteredClient browserClient = RegisteredClient.withId("demo-browser-client")
                .clientId("demo-browser")
                .clientName("Demo Browser / Postman")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://oauth.pstmn.io/v1/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("customer.read")
                .scope("account.read")
                .scope("account.write")
                .scope("transfer.create")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        if (repository.findByClientId(browserClient.getClientId()) == null) {
            repository.save(browserClient);
        }
        return repository;
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, clients);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${security.jwt.keystore-path}") String keystorePath,
            @Value("${security.jwt.keystore-password}") String keystorePassword,
            @Value("${security.jwt.keystore-alias:jwt-signing}") String keyAlias) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(keystorePath))) {
            keyStore.load(input, keystorePassword.toCharArray());
        }
        if (!keyStore.containsAlias(keyAlias) || !keyStore.isKeyEntry(keyAlias)) {
            throw new IllegalStateException("Configured JWT signing alias is missing or is not a key entry");
        }

        RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        var certificate = keyStore.getCertificate(keyAlias);
        if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Configured JWT keystore entry does not contain an RSA certificate");
        }

        RSAKey signingKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("demo-rsa-1").build();
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${security.jwt.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> jdbc.query(
                """
                SELECT u.username, u.status, u.locked_until, c.password_hash
                FROM users u
                JOIN user_credentials c ON c.user_id = u.id
                WHERE u.username = ?
                """,
                (rs, rowNum) -> {
                    Timestamp lockedUntil = rs.getTimestamp("locked_until");
                    boolean locked = lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now());
                    return org.springframework.security.core.userdetails.User.withUsername(rs.getString("username"))
                            .password(rs.getString("password_hash"))
                            .authorities("ROLE_USER")
                            .disabled(!"ACTIVE".equals(rs.getString("status")))
                            .accountLocked(locked)
                            .build();
                },
                username
        ).stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            JdbcTemplate jdbc,
            @Value("${security.jwt.audience}") String audience) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            context.getClaims().audience(List.of(audience));
            Authentication principal = context.getPrincipal();
            if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
                return;
            }
            String username = principal.getName();
            List<UUID> userIds = jdbc.query(
                    "SELECT id FROM users WHERE username = ? AND status = 'ACTIVE'",
                    (rs, rowNum) -> rs.getObject(1, UUID.class), username);
            if (userIds.isEmpty()) return;
            UUID userId = userIds.get(0);
            List<String> permissions = jdbc.queryForList(
                    """
                    SELECT DISTINCT p.code FROM permissions p
                    JOIN role_permissions rp ON rp.permission_id = p.id
                    JOIN user_roles ur ON ur.role_id = rp.role_id
                    WHERE ur.user_id = ?
                    """, String.class, userId);
            List<String> roles = jdbc.queryForList(
                    """
                    SELECT r.name FROM roles r
                    JOIN user_roles ur ON ur.role_id = r.id
                    WHERE ur.user_id = ?
                    """, String.class, userId);
            context.getClaims().subject(userId.toString()).claim("username", username)
                    .claim("permissions", permissions).claim("roles", roles);
        };
    }
}
