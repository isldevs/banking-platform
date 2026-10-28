package com.demo.account.infrastructure.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "account.funding")
public record FundingProperties(
    boolean enabled, @NotEmpty Map<String, @NotNull UUID> sourceAccountIds) {}
