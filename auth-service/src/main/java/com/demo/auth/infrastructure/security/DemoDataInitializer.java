package com.demo.auth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.UUID;

/**
 * Creates local demo identities and authorization data for the dev profile only.
 * Production environments must provision identities and permissions through the normal identity-management flow.
 */
@Configuration
@Profile("dev")
public class DemoDataInitializer {

    private record Permission(String code, String description) {}

    private static final List<Permission> ADMIN_PERMISSIONS = List.of(
            new Permission("USER_MANAGE", "Manage users"),
            new Permission("CUSTOMER_CREATE", "Create customers"),
            new Permission("CUSTOMER_READ", "Read customers"),
            new Permission("KYC_APPROVE", "Approve customer KYC"),
            new Permission("ACCOUNT_OPEN", "Open accounts"),
            new Permission("ACCOUNT_READ", "Read accounts"),
            new Permission("ACCOUNT_FREEZE", "Freeze accounts"),
            new Permission("ACCOUNT_FUND", "Fund accounts"),
            new Permission("TRANSFER_CREATE", "Create transfers"),
            new Permission("TRANSFER_READ", "Read transfers"),
            new Permission("NOTIFICATION_READ", "Read notifications"),
            new Permission("AUDIT_READ", "Read audit records")
    );

    private static final List<String> USER_PERMISSIONS = List.of(
            "CUSTOMER_CREATE",
            "CUSTOMER_READ",
            "ACCOUNT_OPEN",
            "ACCOUNT_READ",
            "TRANSFER_CREATE",
            "TRANSFER_READ",
            "NOTIFICATION_READ"
    );

    @Bean
    ApplicationRunner initializeDemoData(
            JdbcTemplate jdbc,
            @Value("${demo.admin-username:admin}") String adminUsername,
            @Value("${demo.admin-password}") String adminPassword,
            @Value("${demo.user-username:user}") String userUsername,
            @Value("${demo.user-password}") String userPassword) {
        return args -> {
            BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

            UUID adminRoleId = ensureRole(jdbc, "ADMIN");
            UUID userRoleId = ensureRole(jdbc, "USER");

            for (Permission permission : ADMIN_PERMISSIONS) {
                UUID permissionId = ensurePermission(jdbc, permission.code(), permission.description());
                grantPermission(jdbc, adminRoleId, permissionId);
            }

            for (String permissionCode : USER_PERMISSIONS) {
                UUID permissionId = ensurePermission(jdbc, permissionCode, permissionCode);
                grantPermission(jdbc, userRoleId, permissionId);
            }

            UUID adminId = ensureUser(jdbc, bcrypt, adminUsername, adminPassword, "admin@example.local");
            UUID userId = ensureUser(jdbc, bcrypt, userUsername, userPassword, "user@example.local");

            grantRole(jdbc, adminId, adminRoleId);
            grantRole(jdbc, userId, userRoleId);
        };
    }

    private UUID ensureRole(JdbcTemplate jdbc, String name) {
        UUID existingId = jdbc.query(
                "SELECT id FROM roles WHERE name = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                name).stream().findFirst().orElse(null);

        if (existingId != null) {
            return existingId;
        }

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO roles(id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID ensurePermission(JdbcTemplate jdbc, String code, String description) {
        UUID existingId = jdbc.query(
                "SELECT id FROM permissions WHERE code = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                code).stream().findFirst().orElse(null);

        if (existingId != null) {
            return existingId;
        }

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO permissions(id, code, description) VALUES (?, ?, ?)", id, code, description);
        return id;
    }

    private void grantPermission(JdbcTemplate jdbc, UUID roleId, UUID permissionId) {
        jdbc.update("""
                INSERT INTO role_permissions(role_id, permission_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, roleId, permissionId);
    }

    private void grantRole(JdbcTemplate jdbc, UUID userId, UUID roleId) {
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, userId, roleId);
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
            upsertPassword(jdbc, bcrypt, id, password);
            return id;
        }

        jdbc.update("""
                UPDATE users
                SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL, updated_at = NOW()
                WHERE id = ?
                """, existingId);
        upsertPassword(jdbc, bcrypt, existingId, password);
        return existingId;
    }

    private void upsertPassword(
            JdbcTemplate jdbc,
            BCryptPasswordEncoder bcrypt,
            UUID userId,
            String password) {
        String passwordHash = bcrypt.encode(password);
        int updated = jdbc.update("""
                UPDATE user_credentials
                SET password_hash = ?, password_changed_at = NOW(), updated_at = NOW()
                WHERE user_id = ?
                """, passwordHash, userId);

        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_credentials(id, user_id, password_hash)
                    VALUES (?, ?, ?)
                    """, UUID.randomUUID(), userId, passwordHash);
        }
    }
}
