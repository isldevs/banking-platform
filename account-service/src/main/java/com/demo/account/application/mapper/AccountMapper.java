package com.demo.account.application.mapper;

import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.FundingResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.FundingInstructionEntity;
import com.demo.account.domain.model.LedgerEntryEntity;
import com.demo.account.infrastructure.persistence.FundingInstructionRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class AccountMapper {
    @Autowired
    protected FundingInstructionRepository fundingInstructions;

    @Mapping(target = "type", source = "account.accountType")
    @Mapping(target = "availableBalance", source = "balance.availableBalance")
    @Mapping(target = "ledgerBalance", source = "balance.ledgerBalance")
    @Mapping(target = "funding", expression = "java(toFunding(account.getId()))")
    public abstract AccountResponse toResponse(AccountEntity account, AccountBalanceEntity balance);

    @Mapping(target = "transferId", source = "operationId")
    public abstract OperationResponse toResponse(AccountOperationEntity operation);

    @Mapping(target = "operationId", source = "operation.operationId")
    @Mapping(target = "entries", source = "entries")
    @Mapping(target = "totalDebit", expression = "java(total(entries, \"DEBIT\"))")
    @Mapping(target = "totalCredit", expression = "java(total(entries, \"CREDIT\"))")
    @Mapping(target = "balanced", expression = "java(total(entries, \"DEBIT\").compareTo(total(entries, \"CREDIT\")) == 0)")
    public abstract LedgerResponse toLedger(AccountOperationEntity operation, List<LedgerEntryEntity> entries);

    public abstract LedgerResponse.LedgerEntryResponse toEntry(LedgerEntryEntity entry);

    protected FundingResponse toFunding(UUID accountId) {
        return fundingInstructions.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .map(this::toFundingResponse)
                .orElse(null);
    }

    protected FundingResponse toFundingResponse(FundingInstructionEntity instruction) {
        return new FundingResponse(
                instruction.getId(),
                instruction.getStatus(),
                instruction.getAmount(),
                instruction.getCurrency(),
                instruction.getChannel(),
                instruction.getExternalReference());
    }

    protected BigDecimal total(List<LedgerEntryEntity> entries, String type) {
        return entries.stream().filter(e -> type.equals(e.getEntryType()))
                .map(LedgerEntryEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
