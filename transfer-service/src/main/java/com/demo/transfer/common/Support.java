package com.demo.transfer.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared application helpers.
 *
 * <p>Authentication and internal-service credentials are handled by dedicated security components,
 * not by this utility class.
 */
@Component
public class Support {

  public static String hash(String s) {
    try {
      byte[] v = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(v);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static ResponseStatusException fail(HttpStatus status, String code) {
    return new ResponseStatusException(status, code);
  }
}
