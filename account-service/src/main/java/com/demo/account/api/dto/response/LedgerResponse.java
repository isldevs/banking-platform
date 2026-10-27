package com.demo.account.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LedgerResponse(UUID operationId, List<LedgerEntryResponse> entries,
                             BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced) {
    public record LedgerEntryResponse(UUID accountId, String entryType, BigDecimal amount, String currency) {}
}
