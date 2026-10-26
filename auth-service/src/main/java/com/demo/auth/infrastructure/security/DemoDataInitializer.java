package com.demo.auth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

/**
 * Creates deterministic local demo identities for the dev profile only.
 * Production environments must provision identities through the normal identity-management flow.
 */
@Configuration
@Profile("dev")
public class DemoDataInitializer {

    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_MANAGE_PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Bean
    ApplicationRunner initializeDemoData(
            JdbcTemplate jdbc,
            @Value("${demo.admin-username:admin}") String adminUsername,
            @Value("${demo.admin-password}") String adminPassword,
            @Value("${demo.user-username:user}") String userUsername,
            @Value("${demo.user-password}") String userPassword) {
        return args -> {
            BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

            jdbc.update("""
                    INSERT INTO roles(id, name)
                    VALUES (?, 'ADMIN')
                    ON CONFLICT (name) DO NOTHING
                    """, ADMIN_ROLE_ID);
            jdbc.update("""
                    INSERT INTO roles(id, name)
                    VALUES (?, 'USER')
                    ON CONFLICT (name) DO NOTHING
                    """, USER_ROLE_ID);
            jdbc.update("""
                    INSERT INTO permissions(id, code, description)
                    VALUES (?, 'USER_MANAGE', 'Manage users')
                    ON CONFLICT (code) DO NOTHING
                    """, USER_MANAGE_PERMISSION_ID);
            jdbc.update("""
                    INSERT INTO role_permissions(role_id, permission_id)
                    SELECT ?, id FROM permissions WHERE code = 'USER_MANAGE'
                    ON CONFLICT DO NOTHING
                    """, ADMIN_ROLE_ID);

            UUID adminId = ensureUser(jdbc, bcrypt, adminUsername, adminPassword, "admin@example.local");
            UUID userId = ensureUser(jdbc, bcrypt, userUsername, userPassword, "user@example.local");

            jdbc.update("""
                    INSERT INTO user_roles(user_id, role_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, adminId, ADMIN_ROLE_ID);
            jdbc.update("""
                    INSERT INTO user_roles(user_id, role_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, userId, USER_ROLE_ID);
        };
    }

    private UUID ensureUser(
            JdbcTemplate jdbc,
            BCryptPasswordEncoder bcrypt,
            String username,
            String password,
            String email) {
        UUID existingId = jdbc.query(
                "SELECT id FROM users WHERE username = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                username).stream().findFirst().orElse(null);

        if (existingId == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id, username, email, status) VALUES (?, ?, ?, 'ACTIVE')",
                    id, username, email);
            jdbc.update("INSERT INTO user_credentials(id, user_id, password_hash) VALUES (?, ?, ?)",
                    UUID.randomUUID(), id, bcrypt.encode(password));
            return id;
        }

        jdbc.update("""
                UPDATE users
                SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL, updated_at = NOW()
                WHERE id = ?
                """, existingId);
        jdbc.update("""
                UPDATE user_credentials
                SET password_hash = ?, password_changed_at = NOW(), updated_at = NOW()
                WHERE user_id = ?
                """, bcrypt.encode(password), existingId);
        return existingId;
    }
}
