package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.service.AccountingServiceImpl;
import com.demo.account.common.Support;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.AccountTransactionEntity;
import com.demo.account.domain.model.FundingInstructionEntity;
import com.demo.account.domain.model.LedgerEntryEntity;
import com.demo.account.infrastructure.config.FundingProperties;
import com.demo.account.infrastructure.config.SettlementAccountProperties;
import com.demo.account.infrastructure.persistence.AccountBalanceRepository;
import com.demo.account.infrastructure.persistence.AccountHolderRepository;
import com.demo.account.infrastructure.persistence.AccountOpeningRequestRepository;
import com.demo.account.infrastructure.persistence.AccountOperationRepository;
import com.demo.account.infrastructure.persistence.AccountProductRepository;
import com.demo.account.infrastructure.persistence.AccountRepository;
import com.demo.account.infrastructure.persistence.AccountTransactionRepository;
import com.demo.account.infrastructure.persistence.FundingInstructionRepository;
import com.demo.account.infrastructure.persistence.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountFundingSettlementTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
  private final AccountHolderRepository holders = mock(AccountHolderRepository.class);
  private final AccountOpeningRequestRepository openingRequests =
      mock(AccountOpeningRequestRepository.class);
  private final AccountOperationRepository operations = mock(AccountOperationRepository.class);
  private final AccountProductRepository products = mock(AccountProductRepository.class);
  private final AccountTransactionRepository transactions =
      mock(AccountTransactionRepository.class);
  private final FundingInstructionRepository fundingInstructions =
      mock(FundingInstructionRepository.class);
  private final LedgerEntryRepository ledgerEntries = mock(LedgerEntryRepository.class);
  private final AccountMapper mapper = mock(AccountMapper.class);
  private final Support support = mock(Support.class);
  private final FundingProperties fundingProperties = mock(FundingProperties.class);
  private final SettlementAccountProperties settlementProperties =
      mock(SettlementAccountProperties.class);

  private AccountingServiceImpl service;
  private UUID instructionId;
  private UUID sourceId;
  private UUID destinationId;
  private FundingInstructionEntity instruction;
  private AccountEntity source;
  private AccountEntity destination;
  private AccountBalanceEntity sourceBalance;
  private AccountBalanceEntity destinationBalance;

  @BeforeEach
  void setUp() {
    service =
        new AccountingServiceImpl(
            accounts,
            balances,
            holders,
            openingRequests,
            operations,
            products,
            transactions,
            fundingInstructions,
            ledgerEntries,
            mapper,
            support,
            fundingProperties,
            settlementProperties,
            "http://localhost:8082");

    instructionId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    sourceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    destinationId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    instruction = new FundingInstructionEntity();
    instruction.setId(instructionId);
    instruction.setAccountId(destinationId);
    instruction.setAmount(new BigDecimal("100.00"));
    instruction.setCurrency("KHR");
    instruction.setChannel("BANK_TRANSFER");
    instruction.setStatus("PENDING_SETTLEMENT");
    instruction.setIdempotencyKey("open-1:INITIAL_DEPOSIT");

    source = account(sourceId, "SYSTEM", "ACTIVE", "KHR");
    destination = account(destinationId, "CUSTOMER", "PENDING_FUNDING", "KHR");

    sourceBalance = balance(sourceId, BigDecimal.ZERO);
    destinationBalance = balance(destinationId, BigDecimal.ZERO);

    when(fundingInstructions.findByIdForUpdate(instructionId)).thenReturn(Optional.of(instruction));
    when(settlementProperties.sourceAccountIds())
        .thenReturn(Map.of("BANK_TRANSFER", Map.of("KHR", sourceId)));
    when(accounts.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
    when(accounts.findByIdForUpdate(destinationId)).thenReturn(Optional.of(destination));
    when(accounts.findById(destinationId)).thenReturn(Optional.of(destination));
    when(balances.findById(sourceId)).thenReturn(Optional.of(sourceBalance));
    when(balances.findById(destinationId)).thenReturn(Optional.of(destinationBalance));
    when(operations.findById(instructionId)).thenReturn(Optional.empty());
  }

  @Test
  void postsInitialFundingAndActivatesAccountAtomically() {
    service.postFundingInstruction("service-key", instructionId, "BANK-REF-001");

    assertEquals("ACTIVE", destination.getStatus());
    assertEquals(1L, destination.getVersion());
    assertEquals("POSTED", instruction.getStatus());
    assertEquals("BANK-REF-001", instruction.getExternalReference());
    assertEquals(instructionId, instruction.getOperationId());
    assertEquals(new BigDecimal("-100.00"), sourceBalance.getLedgerBalance());
    assertEquals(new BigDecimal("100.00"), destinationBalance.getLedgerBalance());
    verify(operations).save(any(AccountOperationEntity.class));
    verify(transactions).save(any(AccountTransactionEntity.class));
    verify(ledgerEntries).save(any(LedgerEntryEntity.class));
    verify(fundingInstructions).save(instruction);
  }

  @Test
  void repeatedSettlementWithSameExternalReferenceIsIdempotent() {
    instruction.setStatus("POSTED");
    instruction.setExternalReference("BANK-REF-001");
    instruction.setOperationId(instructionId);

    service.postFundingInstruction("service-key", instructionId, "BANK-REF-001");

    verify(operations, never()).save(any(AccountOperationEntity.class));
    verify(transactions, never()).save(any(AccountTransactionEntity.class));
    verify(ledgerEntries, never()).save(any(LedgerEntryEntity.class));
  }

  @Test
  void repeatedSettlementWithDifferentExternalReferenceIsRejected() {
    instruction.setStatus("POSTED");
    instruction.setExternalReference("BANK-REF-001");
    instruction.setOperationId(instructionId);

    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.postFundingInstruction("service-key", instructionId, "BANK-REF-002"));

    verify(operations, never()).save(any(AccountOperationEntity.class));
  }

  @Test
  void settlementCannotPostAlreadyActiveAccount() {
    destination.setStatus("ACTIVE");

    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.postFundingInstruction("service-key", instructionId, "BANK-REF-001"));

    assertEquals("ACTIVE", destination.getStatus());
    assertEquals("PENDING_SETTLEMENT", instruction.getStatus());
    verify(operations, never()).save(any(AccountOperationEntity.class));
  }

  private static AccountEntity account(UUID id, String type, String status, String currency) {
    AccountEntity account = new AccountEntity();
    account.setId(id);
    account.setAccountType(type);
    account.setStatus(status);
    account.setCurrency(currency);
    account.setVersion(0);
    account.setOwnerUserId(UUID.randomUUID());
    return account;
  }

  private static AccountBalanceEntity balance(UUID accountId, BigDecimal amount) {
    AccountBalanceEntity balance = new AccountBalanceEntity();
    balance.setAccountId(accountId);
    balance.setLedgerBalance(amount);
    balance.setAvailableBalance(amount);
    return balance;
  }
}
