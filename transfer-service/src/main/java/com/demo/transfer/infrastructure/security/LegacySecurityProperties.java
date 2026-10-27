package com.demo.transfer.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the temporary legacy X-Service-Key authentication path.
 * Modern endpoints use OAuth2/JWT instead.
 */
@Component
@ConfigurationProperties(prefix = "security.legacy")
public class LegacySecurityProperties {

    private String internalKey;

    public String getInternalKey() {
        return internalKey;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }
}
