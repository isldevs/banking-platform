package com.demo.audit.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditHandler {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    AuditHandler(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @KafkaListener(topics = {"transfer.completed.v1", "transfer.failed.v1"})
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode e = json.readTree(payload);
        UUID eventId = UUID.fromString(e.path("eventId").asText());
        if (db.update("INSERT INTO processed_events(event_id) VALUES(?) ON CONFLICT DO NOTHING", eventId) == 0)
            return;
        db.update(
                "INSERT INTO audit_events(id,event_id,actor_id,action,resource_type,resource_id,new_value) VALUES(?,?,?,?, 'TRANSFER',?, CAST(? AS jsonb))",
                UUID.randomUUID(), eventId, UUID.fromString(e.path("requestedBy").asText()), e.path("eventType").asText(), UUID.fromString(e.path("transferId").asText()), payload);
    }
}
