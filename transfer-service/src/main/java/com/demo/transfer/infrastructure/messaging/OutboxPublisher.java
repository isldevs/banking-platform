package com.demo.transfer.infrastructure.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionTemplate tx;

  OutboxPublisher(
      JdbcTemplate db,
      KafkaTemplate<String, String> kafka,
      org.springframework.transaction.PlatformTransactionManager mgr) {
    this.db = db;
    this.kafka = kafka;
    this.tx = new TransactionTemplate(mgr);
  }

  @Scheduled(fixedDelay = 3000, initialDelay = 5000)
  public void publish() {
    for (int i = 0; i < 30; i++) {
      try {
        Boolean sent =
            tx.execute(
                t -> {
                  List<Map<String, Object>> rows =
                      db.queryForList(
                          "SELECT id,aggregate_id,event_type,payload::text AS payload FROM outbox_events WHERE status='PENDING' AND available_at<=NOW() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                  if (rows.isEmpty()) return false;
                  Map<String, Object> e = rows.get(0);
                  UUID id = (UUID) e.get("id");
                  try {
                    kafka
                        .send(
                            (String) e.get("event_type"),
                            e.get("aggregate_id").toString(),
                            (String) e.get("payload"))
                        .get(5, TimeUnit.SECONDS);
                    db.update(
                        "UPDATE outbox_events SET status='PUBLISHED',published_at=NOW() WHERE id=?",
                        id);
                  } catch (Exception ex) {
                    db.update(
                        "UPDATE outbox_events SET attempt_count=attempt_count+1,available_at=NOW()+INTERVAL '10 seconds' WHERE id=?",
                        id);
                  }
                  return true;
                });
        if (!Boolean.TRUE.equals(sent)) break;
      } catch (Exception e) {
        break;
      }
    }
  }
}
