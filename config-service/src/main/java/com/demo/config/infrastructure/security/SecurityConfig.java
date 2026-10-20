package com.demo.config.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {

    @Bean
    AuthenticationProvider configServerAuthenticationProvider(
            @Value("${config-server.security.username}") String configuredUsername,
            @Value("${config-server.security.password}") String configuredPassword) {

        if (!StringUtils.hasText(configuredUsername) || !StringUtils.hasText(configuredPassword)) {
            throw new IllegalStateException(
                    "CONFIG_SERVER_USERNAME and CONFIG_SERVER_PASSWORD must be configured");
        }

        byte[] expectedUsername = configuredUsername.getBytes(StandardCharsets.UTF_8);
        byte[] expectedPassword = configuredPassword.getBytes(StandardCharsets.UTF_8);

        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                String presentedUsername = authentication.getName();
                Object credentials = authentication.getCredentials();

                boolean usernameMatches = presentedUsername != null
                        && MessageDigest.isEqual(expectedUsername,
                                presentedUsername.getBytes(StandardCharsets.UTF_8));
                boolean passwordMatches = credentials instanceof String presentedPassword
                        && MessageDigest.isEqual(expectedPassword,
                                presentedPassword.getBytes(StandardCharsets.UTF_8));

                if (!usernameMatches || !passwordMatches) {
                    throw new BadCredentialsException("Invalid Config Server credentials");
                }

                return UsernamePasswordAuthenticationToken.authenticated(
                        configuredUsername,
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_CONFIG_CLIENT"));
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider configServerAuthenticationProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(configServerAuthenticationProvider)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
