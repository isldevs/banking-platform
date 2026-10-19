package com.demo.audit.service.impl;

import com.demo.audit.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private final JdbcTemplate db;

    @Autowired
    public AuditServiceImpl(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public List<Map<String, Object>> find(UUID id) {
        return db.queryForList("SELECT id,event_id,actor_id,action,resource_type,resource_id,created_at FROM audit_events WHERE resource_type='TRANSFER' AND resource_id=? ORDER BY created_at DESC LIMIT 100", id);
    }
}
