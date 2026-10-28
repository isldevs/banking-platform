package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.service.AccountingServiceImpl;
import com.demo.account.common.Support;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountOpeningRequestEntity;
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

class AccountOpeningIdempotencyAuthorizationTest {
  private static final UUID ACCOUNT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
  private static final UUID CUSTOMER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID OWNER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_USER_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
  private final AccountOpeningRequestRepository openingRequests =
      mock(AccountOpeningRequestRepository.class);
  private final AccountMapper mapper = mock(AccountMapper.class);
  private AccountingServiceImpl service;
  private AccountEntity account;
  private AccountBalanceEntity balance;

  @BeforeEach
  void setUp() {
    service =
        new AccountingServiceImpl(
            accounts,
            balances,
            mock(AccountHolderRepository.class),
            openingRequests,
            mock(AccountOperationRepository.class),
            mock(AccountProductRepository.class),
            mock(AccountTransactionRepository.class),
            mock(FundingInstructionRepository.class),
            mock(LedgerEntryRepository.class),
            mapper,
            mock(Support.class),
            mock(FundingProperties.class),
            mock(SettlementAccountProperties.class),
            "http://localhost:8082");

    AccountOpeningRequestEntity previous = new AccountOpeningRequestEntity();
    previous.setIdempotencyKey("open-key");
    previous.setAccountId(ACCOUNT_ID);
    previous.setCustomerId(CUSTOMER_ID);
    previous.setProductCode("SAVINGS");
    previous.setCurrency("USD");
    previous.setRequestedInitialDeposit(BigDecimal.ZERO);
    when(openingRequests.findById("open-key")).thenReturn(Optional.of(previous));

    account = new AccountEntity();
    account.setId(ACCOUNT_ID);
    account.setOwnerUserId(OWNER_ID);
    when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

    balance = new AccountBalanceEntity();
    balance.setAccountId(ACCOUNT_ID);
  }

  @Test
  void retryByAnotherCustomerCannotReadExistingAccount() {
    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () -> service.open(request(), jwt(OTHER_USER_ID, false), "open-key"));

    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(balances, mapper);
  }

  @Test
  void retryByAccountOwnerReturnsOriginalAccount() {
    AccountResponse expected = response();
    when(balances.findById(ACCOUNT_ID)).thenReturn(Optional.of(balance));
    when(mapper.toResponse(account, balance)).thenReturn(expected);

    AccountResponse actual = service.open(request(), jwt(OWNER_ID, false), "open-key");

    assertSame(expected, actual);
  }

  @Test
  void retryByAdminPreservesExistingAdminReadPolicy() {
    AccountResponse expected = response();
    when(balances.findById(ACCOUNT_ID)).thenReturn(Optional.of(balance));
    when(mapper.toResponse(account, balance)).thenReturn(expected);

    AccountResponse actual = service.open(request(), jwt(OTHER_USER_ID, true), "open-key");

    assertSame(expected, actual);
  }

  private static OpenAccount request() {
    return new OpenAccount(CUSTOMER_ID, "SAVINGS", "USD", BigDecimal.ZERO, null);
  }

  private static Jwt jwt(UUID subject, boolean admin) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .subject(subject.toString())
        .claim("roles", admin ? List.of("ADMIN") : List.of("USER"))
        .build();
  }

  private static AccountResponse response() {
    return new AccountResponse(
        ACCOUNT_ID,
        "AC00000000000001",
        CUSTOMER_ID,
        OWNER_ID,
        "USD",
        "ACTIVE",
        "SAVINGS",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null);
  }
}
