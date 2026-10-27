package com.demo.account.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "account.funding")
public record FundingProperties(
        boolean enabled,
        @NotNull UUID usdTreasuryAccountId,
        @NotNull UUID khrTreasuryAccountId) {
}
