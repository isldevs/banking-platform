package com.demo.customer.api.controller;

import com.demo.customer.common.Support;
import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.application.port.in.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class CustomerController {

    private final CustomerService customerService;

    @Autowired
    CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/api/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @Transactional
    public Map<String, Object> create(@Valid @RequestBody NewCustomer request, @AuthenticationPrincipal Jwt jwt) {
        return customerService.create(request, jwt);
    }

    @GetMapping("/api/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Map<String, Object> read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return customerService.read(id, jwt);
    }

    @PostMapping("/api/customers/{id}/kyc/approve")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    @Transactional
    public Map<String, Object> approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return  customerService.approve(id, jwt);
    }

    @GetMapping("/internal/customers/{id}/status")
    public Map<String, Object> internal(@PathVariable UUID id, @RequestHeader("X-Service-Key") String key) {
        return customerService.internal(id, key);
    }
}
