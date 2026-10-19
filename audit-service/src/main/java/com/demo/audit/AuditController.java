package com.demo.audit;

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
    private final JdbcTemplate db;

    AuditController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public List<Map<String, Object>> find(@PathVariable UUID id) {
        return db.queryForList("SELECT id,event_id,actor_id,action,resource_type,resource_id,created_at FROM audit_events WHERE resource_type='TRANSFER' AND resource_id=? ORDER BY created_at DESC LIMIT 100", id);
    }
}
