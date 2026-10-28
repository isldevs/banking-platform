package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.service.AccountingServiceImpl;
import com.demo.account.common.Support;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOperationEntity;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AccountLedgerAuthorizationTest {
  private static final UUID OPERATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID DEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID SOURCE_OWNER = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID DEST_OWNER = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID STRANGER = UUID.fromString("60000000-0000-0000-0000-000000000001");

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccountOperationRepository operations = mock(AccountOperationRepository.class);
  private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
  private final AccountMapper mapper = mock(AccountMapper.class);
  private AccountingServiceImpl service;
  private AccountOperationEntity operation;
  private List<LedgerEntryEntity> postedEntries;
  private LedgerResponse response;

  @BeforeEach
  void setUp() {
    service = new AccountingServiceImpl(
        accounts,
        mock(AccountBalanceRepository.class),
        mock(AccountHolderRepository.class),
        mock(AccountOpeningRequestRepository.class),
        operations,
        mock(AccountProductRepository.class),
        mock(AccountTransactionRepository.class),
        mock(FundingInstructionRepository.class),
        entries,
        mapper,
        mock(Support.class),
        mock(FundingProperties.class),
        mock(SettlementAccountProperties.class),
        "http://localhost:8082");

    operation = new AccountOperationEntity();
    operation.setOperationId(OPERATION_ID);
    operation.setSourceAccountId(SOURCE_ID);
    operation.setDestinationAccountId(DEST_ID);
    when(operations.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
    when(accounts.findById(SOURCE_ID)).thenReturn(Optional.of(account(SOURCE_ID, SOURCE_OWNER)));
    when(accounts.findById(DEST_ID)).thenReturn(Optional.of(account(DEST_ID, DEST_OWNER)));

    LedgerEntryEntity debit = new LedgerEntryEntity();
    debit.setEntryType("DEBIT");
    postedEntries = List.of(debit);
    response = new LedgerResponse(OPERATION_ID, List.of(), BigDecimal.ONE, BigDecimal.ONE, true);
    when(entries.findByTransactionIdOrderByEntryType(OPERATION_ID)).thenReturn(postedEntries);
    when(mapper.toLedger(operation, postedEntries)).thenReturn(response);
  }

  @Test
  void sourceAccountOwnerMayReadLedger() {
    assertSame(response, service.ledger(OPERATION_ID, jwt(SOURCE_OWNER, false)));
  }

  @Test
  void destinationAccountOwnerMayReadLedger() {
    assertSame(response, service.ledger(OPERATION_ID, jwt(DEST_OWNER, false)));
  }

  @Test
  void adminMayReadLedger() {
    assertSame(response, service.ledger(OPERATION_ID, jwt(STRANGER, true)));
  }

  @Test
  void unrelatedUserCannotReadLedgerOrFetchEntries() {
    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> service.ledger(OPERATION_ID, jwt(STRANGER, false)));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(entries, mapper);
  }

  @Test
  void unauthenticatedCallerCannotReadLedger() {
    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> service.ledger(OPERATION_ID, null));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(entries, mapper);
  }

  private static AccountEntity account(UUID id, UUID owner) {
    AccountEntity account = new AccountEntity();
    account.setId(id);
    account.setOwnerUserId(owner);
    return account;
  }

  private static Jwt jwt(UUID subject, boolean admin) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .subject(subject.toString())
        .claim("roles", admin ? List.of("ADMIN") : List.of("USER"))
        .build();
  }
}
