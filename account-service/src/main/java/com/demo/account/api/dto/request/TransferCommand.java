package com.demo.account.api.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransferCommand(UUID transferId, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, String currency, String requestedBy) {
}
