package com.demo.account.infrastructure.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.UUID;

@Validated
@ConfigurationProperties(prefix = "account.settlement")
public record SettlementAccountProperties(
        @NotEmpty Map<String, @NotNull Map<String, @NotNull UUID>> sourceAccountIds) {
}
