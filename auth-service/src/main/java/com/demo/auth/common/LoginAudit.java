package com.demo.auth.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class LoginAudit {
  private LoginAudit() {}

  public static void record(
      JdbcTemplate jdbc,
      UUID userId,
      String username,
      boolean success,
      HttpServletRequest request) {
    // Request metadata must fit the audit schema so it cannot interrupt login or lockout updates.
    jdbc.update(
        "INSERT INTO login_attempts(id,username,user_id,success,ip_address,user_agent)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        bounded(username, 100),
        userId,
        success,
        bounded(request.getRemoteAddr(), 80),
        bounded(request.getHeader("User-Agent"), 400));
  }

  private static String bounded(String value, int limit) {
    return value == null || value.length() <= limit ? value : value.substring(0, limit);
  }
}
