package com.demo.account.application.service;

import com.demo.account.api.dto.request.CreateAccountRequest;
import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.port.in.AccountingService;
import com.demo.account.domain.model.AccountBalanceEntity;
import com.demo.account.domain.model.AccountEntity;
import com.demo.account.domain.model.AccountHolderEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.AccountTransactionEntity;
import com.demo.account.domain.model.LedgerEntryEntity;
import com.demo.account.infrastructure.config.FundingProperties;
import com.demo.account.infrastructure.persistence.AccountBalanceRepository;
import com.demo.account.infrastructure.persistence.AccountHolderRepository;
import com.demo.account.infrastructure.persistence.AccountOperationRepository;
import com.demo.account.infrastructure.persistence.AccountRepository;
import com.demo.account.infrastructure.persistence.AccountTransactionRepository;
import com.demo.account.infrastructure.persistence.LedgerEntryRepository;
import com.demo.account.shared.Support;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
public class AccountingServiceImpl implements AccountingService {

    private final AccountRepository accounts;
    private final AccountBalanceRepository balances;
    private final AccountHolderRepository holders;
    private final AccountOperationRepository operations;
    private final AccountTransactionRepository transactions;
    private final LedgerEntryRepository ledgerEntries;
    private final AccountMapper mapper;
    private final Support support;
    private final RestClient remote;
    private final String customerUrl;
    private final FundingProperties fundingProperties;

    public AccountingServiceImpl(AccountRepository accounts,
                                 AccountBalanceRepository balances,
                                 AccountHolderRepository holders,
                                 AccountOperationRepository operations,
                                 AccountTransactionRepository transactions,
                                 LedgerEntryRepository ledgerEntries,
                                 AccountMapper mapper,
                                 Support support,
                                 FundingProperties fundingProperties,
                                 @Value("${services.customer-url}") String customerUrl) {
        this.accounts = accounts;
        this.balances = balances;
        this.holders = holders;
        this.operations = operations;
        this.transactions = transactions;
        this.ledgerEntries = ledgerEntries;
        this.mapper = mapper;
        this.support = support;
        this.fundingProperties = fundingProperties;
        this.customerUrl = customerUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.remote = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @Transactional(readOnly = true)
    public OperationResponse operation(UUID id) {
        return mapper.toResponse(operations.findById(id)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND")));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse read(UUID id, Jwt jwt) {
        AccountEntity account = findAccount(id);
        assertOwner(account, jwt);
        return response(account, findBalance(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> mine(Jwt jwt) {
        return accounts.findTop100ByOwnerUserIdOrderByCreatedAtDesc(UUID.fromString(jwt.getSubject())).stream()
                .map(a -> response(a, findBalance(a.getId()))).toList();
    }

    @Override
    @Transactional
    public AccountResponse create(CreateAccountRequest request, Jwt jwt) {
        UUID ownerUserId = UUID.fromString(jwt.getSubject());
        if (request == null || request.currency() == null || !Set.of("USD", "KHR").contains(request.currency()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY");
        if (request.type() == null || !Set.of("SAVINGS", "CURRENT").contains(request.type()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_TYPE");

        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setOwnerUserId(ownerUserId);
        account.setCurrency(request.currency());
        account.setAccountType("CUSTOMER");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        accounts.save(account);

        AccountHolderEntity holder = new AccountHolderEntity();
        holder.setId(UUID.randomUUID());
        holder.setAccountId(account.getId());
        holder.setCustomerId(UUID.fromString(request.customerId()));
        holders.save(holder);

        AccountBalanceEntity balance = new AccountBalanceEntity();
        balance.setAccountId(account.getId());
        balance.setAvailable(BigDecimal.ZERO);
        balance.setLedger(BigDecimal.ZERO);
        balances.save(balance);
        return response(account, balance);
    }

    @Override
    @Transactional
    public AccountResponse freeze(UUID id) {
        AccountEntity account = findAccount(id);
        if (!"CUSTOMER".equals(account.getAccountType()) || !"ACTIVE".equals(account.getStatus()))
            throw Support.fail(HttpStatus.CONFLICT, "ACCOUNT_FREEZE_INVALID");
        account.setStatus("FROZEN");
        account.setVersion(account.getVersion() + 1);
        accounts.save(account);
        return response(account, findBalance(id));
    }

    @Override
    @Transactional
    public AccountResponse fund(UUID id, Funding request) {
        if (!fundingProperties.enabled())
            throw Support.fail(HttpStatus.FORBIDDEN, "DEMO_FUNDING_DISABLED");
        if (request == null || request.amount() == null || request.amount().signum() <= 0)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_FUNDING_AMOUNT");

        AccountEntity destination = findAccount(id);
        if (!"CUSTOMER".equals(destination.getAccountType()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "FUNDING_DESTINATION_INVALID");
        if (!"ACTIVE".equals(destination.getStatus()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");

        UUID sourceAccount = fundingSourceAccountId(destination.getCurrency());
        post(new TransferCommand(
                UUID.randomUUID(),
                sourceAccount,
                destination.getId(),
                request.amount(),
                destination.getCurrency(),
                "SYSTEM"));
        return readInternal(destination.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerResponse ledger(UUID operationId) {
        AccountOperationEntity operation = operations.findById(operationId)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND"));
        List<LedgerEntryEntity> entries = ledgerEntries.findByTransactionIdOrderByEntryType(operationId);
        if (entries.isEmpty())
            throw Support.fail(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND");
        return mapper.toLedger(operation, entries);
    }

    @Override
    @Transactional
    public AccountResponse internalTransfer(String key, TransferCommand command) {
        support.internal(key);
        post(command);
        return readInternal(command.destinationAccountId());
    }

    @Override
    @Transactional(readOnly = true)
    public OperationResponse operation(String key, UUID id) {
        support.internal(key);
        return operation(id);
    }

    private UUID fundingSourceAccountId(String currency) {
        UUID sourceAccountId = fundingProperties.sourceAccountIds().get(currency);
        if (sourceAccountId == null)
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "FUNDING_CURRENCY_UNSUPPORTED");
        return sourceAccountId;
    }

    private AccountResponse readInternal(UUID id) {
        AccountEntity account = findAccount(id);
        return response(account, findBalance(id));
    }

    private AccountResponse response(AccountEntity account, AccountBalanceEntity balance) {
        return mapper.toResponse(account, balance);
    }

    private AccountEntity findAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
    }

    private AccountBalanceEntity findBalance(UUID id) {
        return balances.findById(id)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "BALANCE_NOT_FOUND"));
    }

    private void assertOwner(AccountEntity account, Jwt jwt) {
        if (!account.getOwnerUserId().equals(UUID.fromString(jwt.getSubject())))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
    }

    private BigDecimal money(BigDecimal value, String currency) {
        if (value == null || currency == null || !Set.of("USD", "KHR").contains(currency) || value.signum() <= 0)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_OR_CURRENCY");
        return value.setScale("KHR".equals(currency) ? 0 : 2);
    }

    private void post(TransferCommand command) {
        if (command == null || command.sourceAccountId() == null || command.destinationAccountId() == null)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER");
        if (command.sourceAccountId().equals(command.destinationAccountId()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_TRANSFER_NOT_ALLOWED");

        AccountEntity source = accounts.findByIdForUpdate(command.sourceAccountId())
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "SOURCE_ACCOUNT_NOT_FOUND"));
        AccountEntity destination = accounts.findByIdForUpdate(command.destinationAccountId())
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "DESTINATION_ACCOUNT_NOT_FOUND"));
        if (!source.getCurrency().equals(command.currency()) || !destination.getCurrency().equals(command.currency()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        if (!"ACTIVE".equals(source.getStatus()) || !"ACTIVE".equals(destination.getStatus()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");

        BigDecimal amount = money(command.amount(), command.currency());
        AccountBalanceEntity sourceBalance = findBalance(source.getId());
        AccountBalanceEntity destinationBalance = findBalance(destination.getId());
        if (sourceBalance.getAvailable().compareTo(amount) < 0)
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");

        sourceBalance.setAvailable(sourceBalance.getAvailable().subtract(amount));
        sourceBalance.setLedger(sourceBalance.getLedger().subtract(amount));
        destinationBalance.setAvailable(destinationBalance.getAvailable().add(amount));
        destinationBalance.setLedger(destinationBalance.getLedger().add(amount));
        balances.save(sourceBalance);
        balances.save(destinationBalance);

        AccountOperationEntity operation = new AccountOperationEntity();
        operation.setId(command.operationId());
        operation.setType("TRANSFER");
        operation.setStatus("POSTED");
        operation.setCurrency(command.currency());
        operation.setAmount(amount);
        operation.setCreatedBy(command.createdBy());
        operations.save(operation);

        AccountTransactionEntity transaction = new AccountTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setOperationId(operation.getId());
        transaction.setSourceAccountId(source.getId());
        transaction.setDestinationAccountId(destination.getId());
        transaction.setAmount(amount);
        transaction.setCurrency(command.currency());
        transactions.save(transaction);

        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setId(UUID.randomUUID());
        debit.setTransactionId(operation.getId());
        debit.setAccountId(source.getId());
        debit.setEntryType("DEBIT");
        debit.setAmount(amount);
        debit.setCurrency(command.currency());
        ledgerEntries.save(debit);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setId(UUID.randomUUID());
        credit.setTransactionId(operation.getId());
        credit.setAccountId(destination.getId());
        credit.setEntryType("CREDIT");
        credit.setAmount(amount);
        credit.setCurrency(command.currency());
        ledgerEntries.save(credit);
    }
}
