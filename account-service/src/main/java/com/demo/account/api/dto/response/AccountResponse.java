package com.demo.account.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    UUID customerId,
    UUID ownerUserId,
    String currency,
    String status,
    String type,
    BigDecimal availableBalance,
    BigDecimal ledgerBalance,
    FundingResponse funding) {}
