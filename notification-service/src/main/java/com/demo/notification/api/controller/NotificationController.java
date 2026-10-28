package com.demo.notification.api.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final JdbcTemplate db;

  NotificationController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('NOTIFICATION_READ') or hasAuthority('TRANSFER_READ')")
  public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT id,type,subject,message,status,created_at FROM notifications WHERE recipient_user_id=? ORDER BY created_at DESC LIMIT 100",
        UUID.fromString(jwt.getSubject()));
  }
}
