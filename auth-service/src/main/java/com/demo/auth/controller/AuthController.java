package com.demo.auth.controller;

import com.demo.auth.entity.Login;
import com.demo.auth.entity.NewUser;
import com.demo.auth.entity.Refresh;
import com.demo.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Login req, HttpServletRequest http) {
        return authService.login(req, http);
    }

    @PostMapping("/api/auth/refresh")
    @Transactional
    public Map<String, Object> refresh(@Valid @RequestBody Refresh req) {
        return authService.refresh(req);
    }

    @PostMapping("/api/auth/logout")
    @Transactional
    public Map<String, String> logout(@Valid @RequestBody Refresh req, @AuthenticationPrincipal Jwt principal) {
        return authService.logout(req, principal);
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "username", jwt.getClaimAsString("username"), "roles", jwt.getClaimAsStringList("roles"), "permissions", jwt.getClaimAsStringList("permissions"));
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public Map<String, String> create(@Valid @RequestBody NewUser req) {
        return authService.create(req);
    }
}
