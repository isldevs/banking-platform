package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.service.AccountingServiceImpl;
import com.demo.account.common.Support;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.AccountTransactionEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AccountPostingInvariantsTest {
  private static final UUID SOURCE_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID DESTINATION_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OPERATION_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000001");

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
  private final AccountOperationRepository operations = mock(AccountOperationRepository.class);
  private final AccountTransactionRepository transactions =
      mock(AccountTransactionRepository.class);
  private final LedgerEntryRepository ledgerEntries = mock(LedgerEntryRepository.class);

  private AccountingServiceImpl service;
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
            mock(AccountHolderRepository.class),
            mock(AccountOpeningRequestRepository.class),
            operations,
            mock(AccountProductRepository.class),
            transactions,
            mock(FundingInstructionRepository.class),
            ledgerEntries,
            mock(AccountMapper.class),
            mock(Support.class),
            mock(FundingProperties.class),
            mock(SettlementAccountProperties.class),
            "http://localhost:8082");

    source = account(SOURCE_ID, USER_ID);
    destination = account(DESTINATION_ID, UUID.randomUUID());
    sourceBalance = balance(SOURCE_ID, "200.00");
    destinationBalance = balance(DESTINATION_ID, "50.00");

    when(accounts.findByIdForUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
    when(accounts.findByIdForUpdate(DESTINATION_ID)).thenReturn(Optional.of(destination));
    when(balances.findById(SOURCE_ID)).thenReturn(Optional.of(sourceBalance));
    when(balances.findById(DESTINATION_ID)).thenReturn(Optional.of(destinationBalance));
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.empty());
  }

  @Test
  void successfulPostingBalancesLedgerAndPreservesTotalCustomerFunds() {
    service.post(command("40.00"));

    assertEquals(new BigDecimal("160.00"), sourceBalance.getLedgerBalance());
    assertEquals(new BigDecimal("160.00"), sourceBalance.getAvailableBalance());
    assertEquals(new BigDecimal("90.00"), destinationBalance.getLedgerBalance());
    assertEquals(new BigDecimal("90.00"), destinationBalance.getAvailableBalance());
    assertEquals(
        new BigDecimal("250.00"),
        sourceBalance.getLedgerBalance().add(destinationBalance.getLedgerBalance()));
    assertEquals(1L, source.getVersion());
    assertEquals(1L, destination.getVersion());

    ArgumentCaptor<AccountOperationEntity> operation =
        ArgumentCaptor.forClass(AccountOperationEntity.class);
    verify(operations).save(operation.capture());
    assertEquals(OPERATION_ID, operation.getValue().getOperationId());
    assertEquals("COMMITTED", operation.getValue().getStatus());

    ArgumentCaptor<LedgerEntryEntity> ledger =
        ArgumentCaptor.forClass(LedgerEntryEntity.class);
    verify(ledgerEntries, times(2)).save(ledger.capture());
    List<LedgerEntryEntity> entries = ledger.getAllValues();
    assertEquals(
        List.of("DEBIT", "CREDIT"),
        entries.stream().map(LedgerEntryEntity::getEntryType).toList());
    assertEquals(
        List.of(SOURCE_ID, DESTINATION_ID),
        entries.stream().map(LedgerEntryEntity::getAccountId).toList());
    assertEquals(new BigDecimal("40.00"), entries.get(0).getAmount());
    assertEquals(entries.get(0).getAmount(), entries.get(1).getAmount());
    assertEquals(OPERATION_ID, entries.get(0).getTransactionId());
    assertEquals(OPERATION_ID, entries.get(1).getTransactionId());

    ArgumentCaptor<AccountTransactionEntity> posting =
        ArgumentCaptor.forClass(AccountTransactionEntity.class);
    verify(transactions, times(2)).save(posting.capture());
    assertEquals(
        List.of("DEBIT", "CREDIT"),
        posting.getAllValues().stream().map(AccountTransactionEntity::getTransactionType).toList());

    InOrder locking = inOrder(accounts);
    locking.verify(accounts).findByIdForUpdate(SOURCE_ID);
    locking.verify(accounts).findByIdForUpdate(DESTINATION_ID);
  }

  @Test
  void retryOfCommittedOperationDoesNotPostAgain() {
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.of(prior("40.00")));

    service.post(command("40.00"));

    assertEquals(new BigDecimal("200.00"), sourceBalance.getLedgerBalance());
    assertEquals(new BigDecimal("50.00"), destinationBalance.getLedgerBalance());
    verify(operations, never()).save(any(AccountOperationEntity.class));
    verifyNoInteractions(transactions, ledgerEntries);
  }

  @Test
  void reuseOfCommittedOperationIdWithDifferentAmountIsRejected() {
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.of(prior("40.00")));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.post(command("41.00")));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(operations, never()).save(any(AccountOperationEntity.class));
    verifyNoInteractions(transactions, ledgerEntries);
  }

  @Test
  void insufficientFundsCannotProduceLedgerEntries() {
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.post(command("200.01")));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    assertEquals(new BigDecimal("200.00"), sourceBalance.getLedgerBalance());
    verify(operations, never()).save(any(AccountOperationEntity.class));
    verifyNoInteractions(transactions, ledgerEntries);
  }

  @Test
  void lockingOrderIsIndependentOfTransferDirection() {
    destination.setOwnerUserId(USER_ID);

    service.post(
        new TransferCommand(
            OPERATION_ID,
            DESTINATION_ID,
            SOURCE_ID,
            new BigDecimal("40.00"),
            "USD",
            USER_ID.toString()));

    InOrder locking = inOrder(accounts);
    locking.verify(accounts).findByIdForUpdate(SOURCE_ID);
    locking.verify(accounts).findByIdForUpdate(DESTINATION_ID);
  }

  private static TransferCommand command(String amount) {
    return new TransferCommand(
        OPERATION_ID,
        SOURCE_ID,
        DESTINATION_ID,
        new BigDecimal(amount),
        "USD",
        USER_ID.toString());
  }

  private static AccountOperationEntity prior(String amount) {
    AccountOperationEntity operation = new AccountOperationEntity();
    operation.setOperationId(OPERATION_ID);
    operation.setSourceAccountId(SOURCE_ID);
    operation.setDestinationAccountId(DESTINATION_ID);
    operation.setAmount(new BigDecimal(amount));
    operation.setCurrency("USD");
    operation.setRequestedBy(USER_ID.toString());
    operation.setStatus("COMMITTED");
    return operation;
  }

  private static AccountEntity account(UUID id, UUID owner) {
    AccountEntity account = new AccountEntity();
    account.setId(id);
    account.setOwnerUserId(owner);
    account.setStatus("ACTIVE");
    account.setAccountType("CUSTOMER");
    account.setCurrency("USD");
    return account;
  }

  private static AccountBalanceEntity balance(UUID id, String amount) {
    AccountBalanceEntity balance = new AccountBalanceEntity();
    balance.setAccountId(id);
    balance.setLedgerBalance(new BigDecimal(amount));
    balance.setAvailableBalance(new BigDecimal(amount));
    return balance;
  }
}
