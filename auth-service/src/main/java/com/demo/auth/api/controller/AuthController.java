package com.demo.auth.api.controller;

import com.demo.auth.api.dto.request.Login;
import com.demo.auth.api.dto.request.NewUser;
import com.demo.auth.api.dto.request.Refresh;
import com.demo.auth.application.port.in.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;

    @Autowired
    AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Legacy compatibility endpoint. New clients should use Spring Authorization
     * Server's Authorization Code + PKCE flow and POST /oauth2/token.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Login req, HttpServletRequest http) {
        return authService.login(req, http);
    }

    /**
     * Legacy refresh-token store. Its tokens are not interchangeable with tokens
     * created by Spring Authorization Server.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/api/auth/refresh")
    @Transactional
    public Map<String, Object> refresh(@Valid @RequestBody Refresh req) {
        return authService.refresh(req);
    }

    /**
     * Legacy refresh-token revocation endpoint. Use POST /oauth2/revoke for
     * Spring Authorization Server tokens.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/api/auth/logout")
    @Transactional
    public Map<String, String> logout(@Valid @RequestBody Refresh req, @AuthenticationPrincipal Jwt principal) {
        return authService.logout(req, principal);
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("user_id", jwt.getSubject(), "username", jwt.getClaimAsString("username"), "roles", jwt.getClaimAsStringList("roles"), "permissions", jwt.getClaimAsStringList("permissions"));
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public Map<String, String> create(@Valid @RequestBody NewUser req) {
        return authService.create(req);
    }
}
