package com.demo.account.common;

import com.demo.account.infrastructure.security.LegacySecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Shared application helpers.
 *
 * The legacy X-Service-Key remains supported for compatibility, but its
 * configuration is type-safe and isolated under security.legacy.
 */
@Component
public class Support {

    /**
     * Kept public for existing internal callers that forward the service key.
     * New code should prefer dedicated security components.
     */
    public final String internalKey;

    public Support(LegacySecurityProperties security) {
        String key = security.getInternalKey();
        this.internalKey = key == null ? "" : key;
    }

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

    public void validateLegacyInternalKey(String providedKey) {
        if (internalKey.length() < 24) {
            throw new IllegalStateException("Legacy internal security key is not configured or too short");
        }
        if (providedKey == null || providedKey.isBlank()
                || !MessageDigest.isEqual(
                        internalKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_CREDENTIAL");
        }
    }

    /**
     * Backward-compatible name for existing internal endpoints.
     */
    public void internal(String key) {
        validateLegacyInternalKey(key);
    }
}
