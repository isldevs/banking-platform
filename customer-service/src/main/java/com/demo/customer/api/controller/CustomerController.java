package com.demo.customer.api.controller;

import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.api.dto.response.CustomerResponse;
import com.demo.customer.application.port.in.CustomerService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/api/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public CustomerResponse create(@Valid @RequestBody NewCustomer request, @AuthenticationPrincipal Jwt jwt) {
        return customerService.create(request, jwt);
    }

    @GetMapping("/api/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerResponse read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return customerService.read(id, jwt);
    }

    @PostMapping("/api/customers/{id}/kyc/approve")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public CustomerResponse approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return customerService.approve(id, jwt);
    }

    /**
     * Legacy/internal compatibility endpoint.
     *
     * Modern endpoints use OAuth2/JWT. This endpoint intentionally keeps
     * X-Service-Key authentication until all legacy callers are migrated.
     */
    @GetMapping("/internal/customers/{id}/status")
    public CustomerResponse internal(@PathVariable UUID id, @RequestHeader("X-Service-Key") String key) {
        return customerService.internal(id, key);
    }
}
