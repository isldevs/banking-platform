package com.demo.customer.common;

import com.demo.customer.infrastructure.security.LegacySecurityProperties;
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

    private final LegacySecurityProperties security;

    public Support(LegacySecurityProperties security) {
        this.security = security;
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

    /**
     * Validates the temporary legacy X-Service-Key credential.
     * Modern APIs must use OAuth2/JWT instead.
     */
    public void validateLegacyInternalKey(String providedKey) {
        String configuredKey = security.getInternalKey();
        if (configuredKey == null || configuredKey.length() < 24) {
            throw new IllegalStateException("Legacy internal security key is not configured or too short");
        }
        if (providedKey == null || providedKey.isBlank()
                || !MessageDigest.isEqual(
                        configuredKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_CREDENTIAL");
        }
    }
}
