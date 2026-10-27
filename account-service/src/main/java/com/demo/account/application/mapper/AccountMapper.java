package com.demo.account.application.mapper;

import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.FundingInstructionEntity;
import com.demo.account.domain.model.LedgerEntryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {
    @Mapping(target = "type", source = "account.accountType")
    @Mapping(target = "availableBalance", source = "balance.availableBalance")
    @Mapping(target = "ledgerBalance", source = "balance.ledgerBalance")
    @Mapping(target = "funding", source = "funding")
    AccountResponse toResponse(AccountEntity account,
                               AccountBalanceEntity balance,
                               FundingInstructionEntity funding);

    @Mapping(target = "transferId", source = "operationId")
    OperationResponse toResponse(AccountOperationEntity operation);

    @Mapping(target = "operationId", source = "operation.operationId")
    @Mapping(target = "entries", source = "entries")
    @Mapping(target = "totalDebit", expression = "java(total(entries, \"DEBIT\"))")
    @Mapping(target = "totalCredit", expression = "java(total(entries, \"CREDIT\"))")
    @Mapping(target = "balanced", expression = "java(total(entries, \"DEBIT\").compareTo(total(entries, \"CREDIT\")) == 0)")
    LedgerResponse toLedger(AccountOperationEntity operation, List<LedgerEntryEntity> entries);

    LedgerResponse.LedgerEntryResponse toEntry(LedgerEntryEntity entry);

    default BigDecimal total(List<LedgerEntryEntity> entries, String type) {
        return entries.stream().filter(e -> type.equals(e.getEntryType()))
                .map(LedgerEntryEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
