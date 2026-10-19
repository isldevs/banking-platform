package com.demo.audit.controller;

import com.demo.audit.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;

    @Autowired
    AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public List<Map<String, Object>> find(@PathVariable UUID id) {
        return auditService.find(id);
    }
}
