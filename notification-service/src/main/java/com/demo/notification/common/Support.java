package com.demo.notification.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Shared application helpers.
 *
 * Authentication and internal-service credentials are handled by dedicated
 * security components, not by this utility class.
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
