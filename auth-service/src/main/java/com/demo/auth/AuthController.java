package com.demo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {
    private final JdbcTemplate jdbc;
    private final TokenService jwt;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    private final SecureRandom secure = new SecureRandom();

    AuthController(JdbcTemplate jdbc, TokenService jwt) {
        this.jdbc = jdbc;
        this.jwt = jwt;
    }

    private User lookup(String username) {
        return jdbc.query(
                "SELECT u.id,u.username,u.status,u.failed_login_count,u.locked_until,c.password_hash FROM users u JOIN user_credentials c ON c.user_id=u.id WHERE u.username=?",
                (r, n) -> new User(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getInt(4), r.getTimestamp(5) == null ? null : r.getTimestamp(5).toInstant(), r.getString(6)), username).stream().findFirst().orElse(null);
    }

    private void attempt(User u, String name, boolean success, HttpServletRequest req) {
        jdbc.update("INSERT INTO login_attempts(id,username,user_id,success,ip_address,user_agent) VALUES(?,?,?,?,?,?)", UUID.randomUUID(), name, u == null ? null : u.id(), success, req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    private Map<String, Object> tokens(User u) {
        List<String> perms = jdbc.queryForList("SELECT DISTINCT p.code FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=?", String.class, u.id());
        List<String> roles = jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=?", String.class, u.id());
        byte[] bytes = new byte[48];
        secure.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at) VALUES(?,?,?,?)", UUID.randomUUID(), u.id(), Support.hash(refresh), java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
        return Map.of("accessToken", jwt.access(u.id(), u.username(), perms, roles), "refreshToken", refresh, "tokenType", "Bearer", "expiresIn", 900);
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Login req, HttpServletRequest http) {
        User u = lookup(req.username());
        if (u == null || !bcrypt.matches(req.password(), u.hash())) {
            attempt(u, req.username(), false, http);
            if (u != null)
                jdbc.update("UPDATE users SET failed_login_count=failed_login_count+1,locked_until=CASE WHEN failed_login_count+1>=5 THEN NOW()+INTERVAL '15 minutes' ELSE locked_until END WHERE id=?", u.id());
            throw Support.fail(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        if (!"ACTIVE".equals(u.status()) || (u.locked() != null && u.locked().isAfter(Instant.now())))
            throw Support.fail(HttpStatus.FORBIDDEN, "USER_LOCKED_OR_INACTIVE");
        jdbc.update("UPDATE users SET failed_login_count=0,locked_until=NULL WHERE id=?", u.id());
        attempt(u, req.username(), true, http);
        return tokens(u);
    }

    @PostMapping("/api/auth/refresh")
    @Transactional
    public Map<String, Object> refresh(@Valid @RequestBody Refresh req) {
        String tokenHash = Support.hash(req.refreshToken());
        List<UUID> matches = jdbc.query("UPDATE refresh_tokens SET revoked_at=NOW() WHERE token_hash=? AND revoked_at IS NULL AND expires_at>NOW() RETURNING user_id", (r, n) -> r.getObject(1, UUID.class), tokenHash);
        if (matches.isEmpty())
            throw Support.fail(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        List<String> names = jdbc.queryForList("SELECT username FROM users WHERE id=? AND status='ACTIVE'", String.class, matches.get(0));
        User u = names.isEmpty() ? null : lookup(names.get(0));
        if (u == null)
            throw Support.fail(HttpStatus.UNAUTHORIZED, "USER_INACTIVE");
        return tokens(u);
    }

    @PostMapping("/api/auth/logout")
    @Transactional
    public Map<String, String> logout(@Valid @RequestBody Refresh req, @AuthenticationPrincipal Jwt principal) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at=NOW() WHERE token_hash=? AND user_id=? AND revoked_at IS NULL", Support.hash(req.refreshToken()), UUID.fromString(principal.getSubject()));
        return Map.of("status", "LOGGED_OUT");
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "username", jwt.getClaimAsString("username"), "roles", jwt.getClaimAsStringList("roles"), "permissions", jwt.getClaimAsStringList("permissions"));
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public Map<String, String> create(@Valid @RequestBody NewUser req) {
        if (req.password().length() < 12)
            throw Support.fail(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,email) VALUES(?,?,?)", id, req.username(), req.email());
        jdbc.update("INSERT INTO user_credentials(id,user_id,password_hash) VALUES(?,?,?)", UUID.randomUUID(), id, bcrypt.encode(req.password()));
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", id);
        return Map.of("userId", id.toString());
    }

    public record Login(@NotBlank String username, @NotBlank String password) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    public record NewUser(@NotBlank String username, @NotBlank String password, String email) {
    }

    private record User(UUID id, String username, String status, Integer failed, Instant locked, String hash) {
    }
}
