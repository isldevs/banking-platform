package com.demo.account.api.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(UUID transferId, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, String currency, String requestedBy) {
}
