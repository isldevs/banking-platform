package com.demo.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
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
    private final JdbcTemplate db;
    private final Support support;

    CustomerController(JdbcTemplate db, Support support) {
        this.db = db;
        this.support = support;
    }

    private Map<String, Object> get(UUID id) {
        return db.query(
                "SELECT id,customer_number,user_id,first_name,last_name,date_of_birth,status FROM customers WHERE id=?", (r, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getString(1));
                    m.put("customerNumber", r.getString(2));
                    m.put("userId", r.getString(3));
                    m.put("firstName", r.getString(4));
                    m.put("lastName", r.getString(5));
                    m.put("dateOfBirth", r.getDate(6).toString());
                    m.put("status", r.getString(7));
                    return m;
                }, id).stream().findFirst().orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND"));
    }

    @PostMapping("/api/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @Transactional
    public Map<String, Object> create(@Valid @RequestBody NewCustomer request, @AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.randomUUID();
        String number = "CUS-" + id.toString().substring(0, 12).toUpperCase(Locale.ROOT);
        db.update("INSERT INTO customers(id,customer_number,user_id,first_name,last_name,date_of_birth,phone,email) VALUES(?,?,?,?,?,?,?,?)", id, number, UUID.fromString(jwt.getSubject()), request.firstName(), request.lastName(), request.dateOfBirth(), request.phone(), request.email());
        db.update("INSERT INTO customer_kyc(id,customer_id) VALUES(?,?)", UUID.randomUUID(), id);
        db.update("INSERT INTO customer_status_history(id,customer_id,new_status,changed_by) VALUES(?,?,'PENDING_KYC',?)", UUID.randomUUID(), id, UUID.fromString(jwt.getSubject()));
        return get(id);
    }

    @GetMapping("/api/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Map<String, Object> read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> c = get(id);
        if (!jwt.getSubject().equals(c.get("userId")) && !jwt.getClaimAsStringList("roles").contains("ADMIN"))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_CUSTOMER_OWNER");
        return c;
    }

    @PostMapping("/api/customers/{id}/kyc/approve")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    @Transactional
    public Map<String, Object> approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> c = get(id);
        if (!"PENDING_KYC".equals(c.get("status")))
            throw Support.fail(HttpStatus.CONFLICT, "INVALID_KYC_STATE");
        db.update("UPDATE customers SET status='ACTIVE',updated_at=NOW() WHERE id=?", id);
        db.update("UPDATE customer_kyc SET verification_status='APPROVED',verified_at=NOW(),verified_by=?,updated_at=NOW() WHERE customer_id=?", UUID.fromString(jwt.getSubject()), id);
        db.update("INSERT INTO customer_status_history(id,customer_id,old_status,new_status,reason,changed_by) VALUES(?,?,'PENDING_KYC','ACTIVE','KYC_APPROVED',?)", UUID.randomUUID(), id, UUID.fromString(jwt.getSubject()));
        return get(id);
    }

    @GetMapping("/internal/customers/{id}/status")
    public Map<String, Object> internal(@PathVariable UUID id, @RequestHeader("X-Service-Key") String key) {
        support.internal(key);
        return get(id);
    }

    public record NewCustomer(@NotBlank String firstName, @NotBlank String lastName, @NotNull @Past LocalDate dateOfBirth, String phone, String email) {
    }
}
