package com.demo.account.application.service;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import com.demo.account.application.mapper.AccountMapper;
import com.demo.account.application.port.in.AccountingService;
import com.demo.account.common.Support;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    @Transactional
    public AccountResponse open(OpenAccount request, Jwt jwt) {
        if (request.currency() == null || !Set.of("USD", "KHR").contains(request.currency()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY");

        Map<?, ?> customer;
        try {
            customer = remote.get().uri(customerUrl + "/internal/customers/" + request.customerId() + "/status")
                    .header("X-Service-Key", support.internalKey)
                    .retrieve().body(Map.class);
        } catch (Exception ex) {
            throw Support.fail(HttpStatus.BAD_GATEWAY, "CUSTOMER_VALIDATION_UNAVAILABLE");
        }
        if (customer == null || !"ACTIVE".equals(customer.get("status")))
            throw Support.fail(HttpStatus.CONFLICT, "CUSTOMER_NOT_ACTIVE");
        UUID owner = UUID.fromString((String) customer.get("user_id"));
        if (!jwt.getSubject().equals(owner.toString()) && !isAdmin(jwt))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_CUSTOMER_OWNER");

        UUID id = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setAccountNumber("AC" + id.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        account.setCustomerId(request.customerId());
        account.setOwnerUserId(owner);
        account.setCurrency(request.currency());
        account.setStatus("ACTIVE");
        account.setAccountType("CUSTOMER");
        accounts.save(account);

        AccountBalanceEntity balance = new AccountBalanceEntity();
        balance.setAccountId(id);
        balance.setLedgerBalance(BigDecimal.ZERO);
        balance.setAvailableBalance(BigDecimal.ZERO);
        balance.setUpdatedAt(Instant.now());
        balances.save(balance);

        AccountHolderEntity holder = new AccountHolderEntity();
        holder.setId(new AccountHolderEntity.Id(id, request.customerId()));
        holders.save(holder);
        return response(account, balance);
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
    public List<AccountResponse> list(Jwt jwt) {
        return accounts.findTop100ByOwnerUserIdOrderByCreatedAtDesc(UUID.fromString(jwt.getSubject())).stream()
                .map(a -> response(a, findBalance(a.getId()))).toList();
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

        UUID treasury = treasuryAccountId(destination.getCurrency());
        post(new TransferCommand(
                UUID.randomUUID(),
                treasury,
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

    private UUID treasuryAccountId(String currency) {
        return switch (currency) {
            case "USD" -> fundingProperties.usdTreasuryAccountId();
            case "KHR" -> fundingProperties.khrTreasuryAccountId();
            default -> throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "FUNDING_CURRENCY_UNSUPPORTED");
        };
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
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_BALANCE_NOT_FOUND"));
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
    }

    private void assertOwner(AccountEntity account, Jwt jwt) {
        if (jwt == null || (!jwt.getSubject().equals(String.valueOf(account.getOwnerUserId())) && !isAdmin(jwt)))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
    }

    private BigDecimal money(BigDecimal value, String currency) {
        if (value == null || currency == null || !Set.of("USD", "KHR").contains(currency) || value.signum() <= 0)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_OR_CURRENCY");
        int scale = "KHR".equals(currency) ? 0 : 2;
        if (value.stripTrailingZeros().scale() > scale)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_MONEY_SCALE");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_MONEY_SCALE");
        }
    }

    @Transactional
    public void post(TransferCommand command) {
        if (command.transferId() == null || command.sourceAccountId() == null || command.destinationAccountId() == null || command.requestedBy() == null)
            throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_FIELDS");
        if (command.sourceAccountId().equals(command.destinationAccountId()))
            throw Support.fail(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT");

        BigDecimal amount = money(command.amount(), command.currency());
        UUID first = command.sourceAccountId().compareTo(command.destinationAccountId()) < 0 ? command.sourceAccountId() : command.destinationAccountId();
        UUID second = first.equals(command.sourceAccountId()) ? command.destinationAccountId() : command.sourceAccountId();
        AccountEntity firstAccount = accounts.findByIdForUpdate(first)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        AccountEntity secondAccount = accounts.findByIdForUpdate(second)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        AccountEntity source = firstAccount.getId().equals(command.sourceAccountId()) ? firstAccount : secondAccount;
        AccountEntity destination = firstAccount.getId().equals(command.destinationAccountId()) ? firstAccount : secondAccount;
        AccountBalanceEntity sourceBalance = findBalance(source.getId());
        AccountBalanceEntity destinationBalance = findBalance(destination.getId());

        var existing = operations.findById(command.transferId());
        if (existing.isPresent()) {
            AccountOperationEntity prior = existing.get();
            if (!prior.getSourceAccountId().equals(command.sourceAccountId()) || !prior.getDestinationAccountId().equals(command.destinationAccountId())
                    || prior.getAmount().compareTo(amount) != 0 || !prior.getCurrency().equals(command.currency()) || !prior.getRequestedBy().equals(command.requestedBy()))
                throw Support.fail(HttpStatus.CONFLICT, "OPERATION_ID_REUSED");
            return;
        }

        if (!"ACTIVE".equals(source.getStatus()) || !"ACTIVE".equals(destination.getStatus()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");
        if (!command.currency().equals(source.getCurrency()) || !command.currency().equals(destination.getCurrency()))
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        if (!"SYSTEM".equals(source.getAccountType()) && !command.requestedBy().equals(String.valueOf(source.getOwnerUserId())))
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
        if (!"SYSTEM".equals(source.getAccountType()) && sourceBalance.getAvailableBalance().compareTo(amount) < 0)
            throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE");
        if ("SYSTEM".equals(source.getAccountType()) && !"SYSTEM".equals(command.requestedBy()))
            throw Support.fail(HttpStatus.FORBIDDEN, "RESERVED_ACCOUNT");

        AccountOperationEntity operation = new AccountOperationEntity();
        operation.setOperationId(command.transferId());
        operation.setSourceAccountId(command.sourceAccountId());
        operation.setDestinationAccountId(command.destinationAccountId());
        operation.setAmount(amount);
        operation.setCurrency(command.currency());
        operation.setRequestedBy(command.requestedBy());
        operation.setStatus("COMMITTED");
        operations.save(operation);

        sourceBalance.setLedgerBalance(sourceBalance.getLedgerBalance().subtract(amount));
        sourceBalance.setAvailableBalance(sourceBalance.getAvailableBalance().subtract(amount));
        sourceBalance.setUpdatedAt(Instant.now());
        destinationBalance.setLedgerBalance(destinationBalance.getLedgerBalance().add(amount));
        destinationBalance.setAvailableBalance(destinationBalance.getAvailableBalance().add(amount));
        destinationBalance.setUpdatedAt(Instant.now());
        balances.save(sourceBalance);
        balances.save(destinationBalance);

        saveTransaction(command, "DEBIT");
        saveTransaction(command, "CREDIT");
        saveLedger(command, "DEBIT");
        saveLedger(command, "CREDIT");
        source.setVersion(source.getVersion() + 1);
        destination.setVersion(destination.getVersion() + 1);
        accounts.save(source);
        accounts.save(destination);
    }

    private void saveTransaction(TransferCommand c, String type) {
        AccountTransactionEntity tx = new AccountTransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setOperationId(c.transferId());
        tx.setAccountId("DEBIT".equals(type) ? c.sourceAccountId() : c.destinationAccountId());
        tx.setTransactionType(type);
        tx.setAmount(money(c.amount(), c.currency()));
        tx.setCurrency(c.currency());
        tx.setReferenceId(c.transferId());
        transactions.save(tx);
    }

    private void saveLedger(TransferCommand c, String type) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setTransactionId(c.transferId());
        entry.setAccountId("DEBIT".equals(type) ? c.sourceAccountId() : c.destinationAccountId());
        entry.setEntryType(type);
        entry.setAmount(money(c.amount(), c.currency()));
        entry.setCurrency(c.currency());
        ledgerEntries.save(entry);
    }
}
