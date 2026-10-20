package com.demo.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * OAuth2/OIDC protocol endpoints. The login page is handled by the next
     * chain so browser authorization-code flows can retain their session.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Browser sign-in for Authorization Code + PKCE. Keep failed-login tracking
     * and the 15-minute lockout behavior consistent with the legacy API login.
     */
    @Bean
    @Order(2)
    SecurityFilterChain browserLoginSecurityFilterChain(HttpSecurity http, JdbcTemplate jdbc) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler defaultSuccessHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();

        http.securityMatcher("/login", "/logout", "/error")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(form -> form
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            List<UUID> userIds = jdbc.query(
                                    "SELECT id FROM users WHERE username = ?",
                                    (rs, rowNum) -> rs.getObject(1, UUID.class), username);
                            jdbc.update(
                                    "UPDATE users SET failed_login_count = 0, locked_until = NULL WHERE username = ?",
                                    username);
                            jdbc.update(
                                    "INSERT INTO login_attempts(id, username, user_id, success, ip_address, user_agent) VALUES(?,?,?,?,?,?)",
                                    UUID.randomUUID(), username, userIds.isEmpty() ? null : userIds.get(0),
                                    true, request.getRemoteAddr(), request.getHeader("User-Agent"));
                            defaultSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                        })
                        .failureHandler((request, response, exception) -> {
                            recordBrowserLoginFailure(jdbc, request, exception);
                            response.sendRedirect("/login?error");
                        }))
                .logout(Customizer.withDefaults());
        return http.build();
    }

    private void recordBrowserLoginFailure(
            JdbcTemplate jdbc, HttpServletRequest request, Exception exception) {
        String username = request.getParameter("username");
        if (!StringUtils.hasText(username) || !(exception instanceof BadCredentialsException)) {
            return;
        }

        List<UUID> userIds = jdbc.query(
                "SELECT id FROM users WHERE username = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class), username);
        UUID userId = userIds.isEmpty() ? null : userIds.get(0);
        if (userId != null) {
            jdbc.update(
                    "UPDATE users SET failed_login_count = failed_login_count + 1, "
                            + "locked_until = CASE WHEN failed_login_count + 1 >= 5 "
                            + "THEN NOW() + INTERVAL '15 minutes' ELSE locked_until END WHERE id = ?",
                    userId);
        }
        jdbc.update(
                "INSERT INTO login_attempts(id, username, user_id, success, ip_address, user_agent) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), username, userId, false,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    /**
     * Stateless API/resource-server security for the existing demo APIs.
     */
    @Bean
    @Order(3)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**", "/internal/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> result = new ArrayList<>();
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            for (String permission : permissions == null ? List.<String>of() : permissions) {
                result.add(new SimpleGrantedAuthority(permission));
            }
            return result;
        });
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.public-key-path}") String path,
                          @Value("${security.jwt.issuer}") String issuer,
                          @Value("${security.jwt.audience}") String audience) throws Exception {
        String pem = Files.readString(Path.of(path))
                .replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
