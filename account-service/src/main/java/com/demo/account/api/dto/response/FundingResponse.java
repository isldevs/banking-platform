package com.demo.account.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record FundingResponse(UUID instructionId,
                              String status,
                              BigDecimal amount,
                              String currency,
                              String channel,
                              String externalReference) {
}
