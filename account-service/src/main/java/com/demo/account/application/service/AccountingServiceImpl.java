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
import com.demo.account.domain.model.AccountOpeningRequestEntity;
import com.demo.account.domain.model.AccountOperationEntity;
import com.demo.account.domain.model.AccountProductEntity;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AccountingServiceImpl implements AccountingService {
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "KHR");
  private static final Set<String> SUPPORTED_FUNDING_CHANNELS =
      Set.of("BANK_TRANSFER", "CASH", "CARD", "CHEQUE", "DIRECT_DEBIT");

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;
  private final AccountHolderRepository holders;
  private final AccountOpeningRequestRepository openingRequests;
  private final AccountOperationRepository operations;
  private final AccountProductRepository products;
  private final AccountTransactionRepository transactions;
  private final FundingInstructionRepository fundingInstructions;
  private final LedgerEntryRepository ledgerEntries;
  private final AccountMapper mapper;
  private final Support support;
  private final RestClient remote;
  private final String customerUrl;
  private final FundingProperties fundingProperties;
  private final SettlementAccountProperties settlementProperties;

  public AccountingServiceImpl(
      AccountRepository accounts,
      AccountBalanceRepository balances,
      AccountHolderRepository holders,
      AccountOpeningRequestRepository openingRequests,
      AccountOperationRepository operations,
      AccountProductRepository products,
      AccountTransactionRepository transactions,
      FundingInstructionRepository fundingInstructions,
      LedgerEntryRepository ledgerEntries,
      AccountMapper mapper,
      Support support,
      FundingProperties fundingProperties,
      SettlementAccountProperties settlementProperties,
      @Value("${services.customer-url}") String customerUrl) {
    this.accounts = accounts;
    this.balances = balances;
    this.holders = holders;
    this.openingRequests = openingRequests;
    this.operations = operations;
    this.products = products;
    this.transactions = transactions;
    this.fundingInstructions = fundingInstructions;
    this.ledgerEntries = ledgerEntries;
    this.mapper = mapper;
    this.support = support;
    this.fundingProperties = fundingProperties;
    this.settlementProperties = settlementProperties;
    this.customerUrl = customerUrl;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(4));
    this.remote = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  @Transactional(readOnly = true)
  public OperationResponse operation(UUID id) {
    return mapper.toResponse(
        operations
            .findById(id)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND")));
  }

  @Override
  @Transactional
  public AccountResponse open(OpenAccount request, Jwt jwt, String idempotencyKey) {
    String key = normalizeIdempotencyKey(idempotencyKey);
    UUID principalId = UUID.fromString(jwt.getSubject());
    // Serialize first attempts and retries for this principal/key across service instances.
    // Keep the lock until the outer @Transactional commit or rollback.
    openingRequests.lockForOpening(principalId, key);
    AccountOpeningRequestEntity previous =
        openingRequests.findByPrincipalIdAndIdempotencyKey(principalId, key).orElse(null);
    if (previous != null) {
      validateIdempotentRetry(previous, request);
      // Idempotent retries must enforce the same account ownership policy as regular reads.
      return read(previous.getAccountId(), jwt);
    }

    String productCode = normalize(request.productCode());
    String currency = normalize(request.currency());
    if (!SUPPORTED_CURRENCIES.contains(currency))
      throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY");

    AccountProductEntity product =
        products
            .findByProductCodeAndCurrencyAndStatus(productCode, currency, "ACTIVE")
            .orElseThrow(
                () -> Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_PRODUCT_UNAVAILABLE"));
    BigDecimal initialDeposit = money(request.initialDeposit(), currency, true);

    if (initialDeposit.compareTo(product.getMinimumOpeningBalance()) < 0)
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INITIAL_DEPOSIT_BELOW_MINIMUM");
    if (product.isInitialDepositRequired()
        && initialDeposit.compareTo(product.getInitialDepositAmount()) < 0)
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INITIAL_DEPOSIT_REQUIRED");
    if (initialDeposit.signum() > 0 && request.fundingChannel() == null)
      throw Support.fail(HttpStatus.BAD_REQUEST, "FUNDING_CHANNEL_REQUIRED");

    String fundingChannel =
        request.fundingChannel() == null ? null : normalize(request.fundingChannel());
    if (fundingChannel != null && !SUPPORTED_FUNDING_CHANNELS.contains(fundingChannel))
      throw Support.fail(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FUNDING_CHANNEL");
    if (initialDeposit.signum() == 0 && fundingChannel != null)
      throw Support.fail(HttpStatus.BAD_REQUEST, "FUNDING_CHANNEL_WITHOUT_AMOUNT");
    if (initialDeposit.signum() > 0) settlementAccountId(fundingChannel, currency);

    Map<?, ?> customer;
    try {
      customer =
          remote
              .get()
              .uri(customerUrl + "/internal/customers/" + request.customerId() + "/status")
              .header("X-Service-Key", support.internalKey)
              .retrieve()
              .body(Map.class);
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
    account.setAccountNumber(
        "AC" + id.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
    account.setCustomerId(request.customerId());
    account.setOwnerUserId(owner);
    account.setCurrency(currency);
    account.setStatus(initialDeposit.signum() > 0 ? "PENDING_FUNDING" : "ACTIVE");
    account.setAccountType(product.getAccountType());
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

    AccountOpeningRequestEntity opening = new AccountOpeningRequestEntity();
    opening.setId(UUID.randomUUID());
    opening.setPrincipalId(principalId);
    opening.setIdempotencyKey(key);
    opening.setCustomerId(request.customerId());
    opening.setAccountId(id);
    opening.setProductCode(productCode);
    opening.setCurrency(currency);
    opening.setRequestedInitialDeposit(initialDeposit);
    opening.setFundingChannel(fundingChannel);
    opening.setCreatedAt(Instant.now());
    openingRequests.save(opening);

    if (initialDeposit.signum() > 0) {
      FundingInstructionEntity instruction = new FundingInstructionEntity();
      instruction.setId(UUID.randomUUID());
      instruction.setAccountId(id);
      instruction.setAmount(initialDeposit);
      instruction.setCurrency(currency);
      instruction.setChannel(fundingChannel);
      instruction.setStatus("PENDING_SETTLEMENT");
      // Account identity keeps funding instructions distinct when clients reuse keys.
      instruction.setIdempotencyKey(id + ":INITIAL_DEPOSIT");
      instruction.setCreatedAt(Instant.now());
      fundingInstructions.save(instruction);
    }

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
    return accounts
        .findTop100ByOwnerUserIdOrderByCreatedAtDesc(UUID.fromString(jwt.getSubject()))
        .stream()
        .map(a -> response(a, findBalance(a.getId())))
        .toList();
  }

  @Override
  @Transactional
  public AccountResponse freeze(UUID id) {
    // Serialize lifecycle changes against financial posting for the same account.
    AccountEntity account =
        accounts
            .findByIdForUpdate(id)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
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
    if (!fundingProperties.enabled()) throw Support.fail(HttpStatus.FORBIDDEN, "FUNDING_DISABLED");
    if (request == null || request.amount() == null || request.amount().signum() <= 0)
      throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_FUNDING_AMOUNT");

    AccountEntity destination = findAccount(id);
    if (!"CUSTOMER".equals(destination.getAccountType()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "FUNDING_DESTINATION_INVALID");
    if (!"ACTIVE".equals(destination.getStatus()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");

    UUID sourceAccount = fundingSourceAccountId(destination.getCurrency());
    post(
        new TransferCommand(
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
  public LedgerResponse ledger(UUID operationId, Jwt jwt) {
    AccountOperationEntity operation =
        operations
            .findById(operationId)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND"));
    // An operation ID is not permission to inspect another customer's financial history.
    if (!isLedgerParticipant(operation, jwt))
      throw Support.fail(HttpStatus.FORBIDDEN, "NOT_LEDGER_PARTICIPANT");
    List<LedgerEntryEntity> entries =
        ledgerEntries.findByTransactionIdOrderByEntryType(operationId);
    if (entries.isEmpty()) throw Support.fail(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND");
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
  @Transactional
  public AccountResponse postFundingInstruction(
      String key, UUID instructionId, String externalReference) {
    support.internal(key);
    String normalizedExternalReference = normalizeExternalReference(externalReference);

    FundingInstructionEntity instruction =
        fundingInstructions
            .findByIdForUpdate(instructionId)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "FUNDING_INSTRUCTION_NOT_FOUND"));
    if ("POSTED".equals(instruction.getStatus())) {
      if (!normalizedExternalReference.equals(instruction.getExternalReference()))
        throw Support.fail(HttpStatus.CONFLICT, "EXTERNAL_REFERENCE_MISMATCH");
      return readInternal(instruction.getAccountId());
    }
    if (!"PENDING_SETTLEMENT".equals(instruction.getStatus()))
      throw Support.fail(HttpStatus.CONFLICT, "FUNDING_INSTRUCTION_NOT_POSTABLE");

    // Acquire both settlement and customer account locks in the same canonical order
    // as ordinary transfers. Locking the destination first could deadlock against
    // a concurrent transfer from the settlement account into this account.
    UUID sourceAccount = settlementAccountId(instruction.getChannel(), instruction.getCurrency());
    if (sourceAccount.equals(instruction.getAccountId()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SETTLEMENT_ACCOUNT");
    AccountEntity[] locked = lockAccountsInOrder(sourceAccount, instruction.getAccountId());
    AccountEntity destination =
        locked[0].getId().equals(instruction.getAccountId()) ? locked[0] : locked[1];
    if (!"CUSTOMER".equals(destination.getAccountType()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "FUNDING_DESTINATION_INVALID");
    if (!"PENDING_FUNDING".equals(destination.getStatus()))
      throw Support.fail(HttpStatus.CONFLICT, "ACCOUNT_NOT_PENDING_FUNDING");
    if (!instruction.getCurrency().equals(destination.getCurrency()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
    if (instruction.getAmount() == null || instruction.getAmount().signum() <= 0)
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FUNDING_AMOUNT");

    // post() deliberately requires ACTIVE accounts. Transition the opening account
    // inside this same transaction; if posting fails, the entire transaction rolls back.
    destination.setStatus("ACTIVE");
    post(
        new TransferCommand(
            instruction.getId(),
            sourceAccount,
            destination.getId(),
            instruction.getAmount(),
            instruction.getCurrency(),
            "SYSTEM"));

    instruction.setExternalReference(normalizedExternalReference);
    instruction.setOperationId(instruction.getId());
    instruction.setStatus("POSTED");
    instruction.setPostedAt(Instant.now());
    fundingInstructions.save(instruction);

    return readInternal(destination.getId());
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

  private UUID settlementAccountId(String channel, String currency) {
    Map<String, UUID> byCurrency = settlementProperties.sourceAccountIds().get(channel);
    if (byCurrency == null)
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "SETTLEMENT_CHANNEL_UNSUPPORTED");
    UUID accountId = byCurrency.get(currency);
    if (accountId == null)
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "SETTLEMENT_CURRENCY_UNSUPPORTED");
    return accountId;
  }

  private void validateIdempotentRetry(AccountOpeningRequestEntity previous, OpenAccount request) {
    String productCode = normalize(request.productCode());
    String currency = normalize(request.currency());
    BigDecimal amount = request.initialDeposit() == null ? null : request.initialDeposit();
    String channel = request.fundingChannel() == null ? null : normalize(request.fundingChannel());
    if (!previous.getCustomerId().equals(request.customerId())
        || !previous.getProductCode().equals(productCode)
        || !previous.getCurrency().equals(currency)
        || amount == null
        || previous.getRequestedInitialDeposit().compareTo(amount) != 0
        || !java.util.Objects.equals(previous.getFundingChannel(), channel))
      throw Support.fail(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
  }

  private String normalizeIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > 120)
      throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
    return key.trim();
  }

  private String normalizeExternalReference(String externalReference) {
    if (externalReference == null
        || externalReference.isBlank()
        || externalReference.trim().length() > 120)
      throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_EXTERNAL_REFERENCE");
    return externalReference.trim();
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private AccountResponse readInternal(UUID id) {
    AccountEntity account = findAccount(id);
    return response(account, findBalance(id));
  }

  private AccountResponse response(AccountEntity account, AccountBalanceEntity balance) {
    return mapper.toResponse(account, balance);
  }

  private AccountEntity findAccount(UUID id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
  }

  private AccountBalanceEntity findBalance(UUID id) {
    return balances
        .findById(id)
        .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_BALANCE_NOT_FOUND"));
  }

  private boolean isLedgerParticipant(AccountOperationEntity operation, Jwt jwt) {
    if (jwt == null) return false;
    if (isAdmin(jwt)) return true;
    AccountEntity source = findAccount(operation.getSourceAccountId());
    if (jwt.getSubject().equals(String.valueOf(source.getOwnerUserId()))) return true;
    AccountEntity destination = findAccount(operation.getDestinationAccountId());
    return jwt.getSubject().equals(String.valueOf(destination.getOwnerUserId()));
  }

  private boolean isAdmin(Jwt jwt) {
    return jwt != null
        && jwt.getClaimAsStringList("roles") != null
        && jwt.getClaimAsStringList("roles").contains("ADMIN");
  }

  private void assertOwner(AccountEntity account, Jwt jwt) {
    if (jwt == null
        || (!jwt.getSubject().equals(String.valueOf(account.getOwnerUserId())) && !isAdmin(jwt)))
      throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
  }

  static BigDecimal money(BigDecimal value, String currency, boolean allowZero) {
    if (value == null
        || currency == null
        || !SUPPORTED_CURRENCIES.contains(currency)
        || (!allowZero && value.signum() <= 0)
        || (allowZero && value.signum() < 0))
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

  private AccountEntity[] lockAccountsInOrder(UUID leftId, UUID rightId) {
    UUID first = leftId.compareTo(rightId) < 0 ? leftId : rightId;
    UUID second = first.equals(leftId) ? rightId : leftId;
    AccountEntity firstAccount =
        accounts
            .findByIdForUpdate(first)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
    AccountEntity secondAccount =
        accounts
            .findByIdForUpdate(second)
            .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
    return new AccountEntity[] {firstAccount, secondAccount};
  }

  @Transactional
  public void post(TransferCommand command) {
    if (command.transferId() == null
        || command.sourceAccountId() == null
        || command.destinationAccountId() == null
        || command.requestedBy() == null)
      throw Support.fail(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_FIELDS");
    if (command.sourceAccountId().equals(command.destinationAccountId()))
      throw Support.fail(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT");

    BigDecimal amount = money(command.amount(), command.currency(), false);
    AccountEntity[] locked =
        lockAccountsInOrder(command.sourceAccountId(), command.destinationAccountId());
    AccountEntity source =
        locked[0].getId().equals(command.sourceAccountId()) ? locked[0] : locked[1];
    AccountEntity destination =
        locked[0].getId().equals(command.destinationAccountId()) ? locked[0] : locked[1];
    AccountBalanceEntity sourceBalance = findBalance(source.getId());
    AccountBalanceEntity destinationBalance = findBalance(destination.getId());

    var existing = operations.findById(command.transferId());
    if (existing.isPresent()) {
      AccountOperationEntity prior = existing.get();
      if (!prior.getSourceAccountId().equals(command.sourceAccountId())
          || !prior.getDestinationAccountId().equals(command.destinationAccountId())
          || prior.getAmount().compareTo(amount) != 0
          || !prior.getCurrency().equals(command.currency())
          || !prior.getRequestedBy().equals(command.requestedBy()))
        throw Support.fail(HttpStatus.CONFLICT, "OPERATION_ID_REUSED");
      return;
    }

    if (!"ACTIVE".equals(source.getStatus()) || !"ACTIVE".equals(destination.getStatus()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE");
    if (!command.currency().equals(source.getCurrency())
        || !command.currency().equals(destination.getCurrency()))
      throw Support.fail(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
    if (!"SYSTEM".equals(source.getAccountType())
        && !command.requestedBy().equals(String.valueOf(source.getOwnerUserId())))
      throw Support.fail(HttpStatus.FORBIDDEN, "NOT_ACCOUNT_OWNER");
    if (!"SYSTEM".equals(source.getAccountType())
        && sourceBalance.getAvailableBalance().compareTo(amount) < 0)
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
    tx.setAmount(money(c.amount(), c.currency(), false));
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
    entry.setAmount(money(c.amount(), c.currency(), false));
    entry.setCurrency(c.currency());
    ledgerEntries.save(entry);
  }
}
