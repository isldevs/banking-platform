package com.demo.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationHandler {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    NotificationHandler(JdbcTemplate db, ObjectMapper json) {
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
        String type = e.path("eventType").asText();
        String transfer = e.path("transferId").asText();
        String label = "transfer.completed.v1".equals(type) ? "Transfer completed" : "Transfer failed";
        db.update(
                "INSERT INTO notifications(id,event_id,recipient_user_id,channel,type,subject,message,status,sent_at) VALUES(?,?,?,'IN_APP',?,?,?,'DELIVERED',NOW())",
                UUID.randomUUID(), eventId, UUID.fromString(e.path("requestedBy").asText()), type, label, label + ": " + transfer);
    }
}
