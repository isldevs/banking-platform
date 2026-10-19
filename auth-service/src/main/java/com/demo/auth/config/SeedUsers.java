package com.demo.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.UUID;

@Configuration
public class SeedUsers {
    @Bean
    ApplicationRunner seed(JdbcTemplate jdbc,
                           @Value("${demo.admin-username}") String admin,
                           @Value("${demo.admin-password:admin123456789}") String adminPassword,
                           @Value("${DEMO_USER_PASSWORD:user123456789}") String userPassword) {
        return args -> {
            List<String> codes = List.of("USER_READ", "USER_MANAGE", "CUSTOMER_CREATE", "CUSTOMER_READ", "KYC_APPROVE", "ACCOUNT_OPEN", "ACCOUNT_READ", "ACCOUNT_FUND", "ACCOUNT_FREEZE", "TRANSFER_CREATE", "TRANSFER_READ", "AUDIT_READ", "CONFIG_READ", "CONFIG_MANAGE", "NOTIFICATION_READ");
            for (String code : codes)
                jdbc.update("INSERT INTO permissions(id,code,description) VALUES(?,?,?) ON CONFLICT(code) DO NOTHING", UUID.randomUUID(), code, code);
            for (String role : List.of("ADMIN", "USER"))
                jdbc.update("INSERT INTO roles(id,name) VALUES(?,?) ON CONFLICT(name) DO NOTHING", UUID.randomUUID(), role);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.name='ADMIN' ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r JOIN permissions p ON p.code IN ('CUSTOMER_CREATE','CUSTOMER_READ','ACCOUNT_OPEN','ACCOUNT_READ','TRANSFER_CREATE','TRANSFER_READ') WHERE r.name='USER' ON CONFLICT DO NOTHING");
            make(jdbc, admin, adminPassword, "ADMIN");
            make(jdbc, "demo", userPassword, "USER");
        };
    }

    private void make(JdbcTemplate jdbc, String username, String password, String role) {
        if (password == null || password.length() < 12)
            throw new IllegalStateException("Set strong DEMO_ADMIN_PASSWORD and DEMO_USER_PASSWORD (12+ chars) before starting auth-service");
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("INSERT INTO users(id,username,status) VALUES(?,?,'ACTIVE') ON CONFLICT(username) DO NOTHING", id, username);
        if (inserted == 0)
            return;
        jdbc.update("INSERT INTO user_credentials(id,user_id,password_hash) VALUES(?,?,?)", UUID.randomUUID(), id, new BCryptPasswordEncoder(12).encode(password));
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name=?", id, role);
    }
}
