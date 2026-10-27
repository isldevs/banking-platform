package com.demo.customer.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class Support {
    /**
     * Legacy/internal-service credential.
     *
     * This is intentionally separate from OAuth2/JWT authentication used by the
     * modern API. Keep it while legacy callers still depend on X-Service-Key.
     */
    @Value("${security.internal-key:}")
    String internalKey;

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

    /**
     * Validates the legacy X-Service-Key credential.
     *
     * Do not use this as the authentication mechanism for modern OAuth2/JWT APIs.
     */
    public void validateLegacyInternalKey(String key) {
        if (internalKey == null || internalKey.length() < 24 || key == null ||
                !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_CREDENTIAL");
        }
    }
}
