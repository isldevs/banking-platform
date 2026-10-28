# Database Ownership and Reconciliation

Each business service owns its PostgreSQL database and its Flyway migrations. No service may query another service's tables directly.

| Database | Owner | Current application tables |
|---|---|---|
| auth | auth-service | users, credentials, roles, permissions, user/role mappings, OAuth2 authorization data, legacy refresh/login data |
| customer | customer-service | customers, addresses, KYC and status history |
| account | account-service | accounts, account_holders, account_balances, account_operations, account_transactions, ledger_entries, account_products, account_opening_requests, funding_instructions |
| transfer | transfer-service | transfers, transfer items/status history, idempotency records, outbox events |
| notification | notification-service | notifications and processed-event state |
| audit | audit-service | audit events and processed-event state |
| config | Config Service | no application database; configuration is bundled on the classpath |

## Account database

Account Service is the financial accounting boundary. Its current migrations include:

```text
V1__accounts_ledger.sql
V2__account_products_and_funding.sql
V3__currency_reference_data.sql
V4__scope_account_opening_idempotency.sql
```

The account database also contains system clearing accounts used by the local/demo settlement configuration. These are demonstration infrastructure accounts, not a model for creating money in a real bank.

### Account-opening data model

`account_products` defines product rules such as currency, account type, initial-deposit requirements, and minimum balances.

`account_opening_requests` makes openings idempotent per authenticated principal and client key (`UNIQUE(principal_id, idempotency_key)`), after Flyway V4 backfills existing requests. The application takes a PostgreSQL transaction-scoped advisory lock based on principal and key before checking existing requests. This serializes same-key first attempts and retries at the database transaction boundary; historical admin-initiated openings need special migration review.

`funding_instructions` records the initial funding request and settlement lifecycle, including channel, amount, external settlement reference, operation ID, and posting timestamps.

## Ledger invariant

Every financial operation must produce balanced debit and credit ledger entries.

```sql
SELECT transaction_id,
       SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS total_debit,
       SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS total_credit
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END)
    <> SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END);
```

Expected result: **zero rows**.

## Operation uniqueness

The account operation ID is the idempotent financial operation identity.

```sql
SELECT operation_id, COUNT(*)
FROM account_operations
GROUP BY operation_id
HAVING COUNT(*) > 1;
```

Expected result: **zero rows**.

## Funding-instruction reconciliation

Useful checks include:

```sql
SELECT status, COUNT(*)
FROM funding_instructions
GROUP BY status;

SELECT id, account_id, amount, currency, external_reference
FROM funding_instructions
WHERE status = 'PENDING_SETTLEMENT';
```

Pending instructions should be reconciled against the external settlement system in a real integration.

## Transfer outbox

```sql
SELECT status, COUNT(*)
FROM outbox_events
GROUP BY status;
```

Transfer records in ambiguous states should be reconciled using the stable financial operation identity rather than issuing a new money movement.

## Transaction boundaries

- **Account Service:** validates and locks the required accounts/instructions, creates the financial operation and paired ledger entries, updates balances, and commits the funding/transfer state atomically.
- **Transfer Service:** owns transfer workflow and idempotency; account-service financial operations use a stable operation/transfer identity so retries do not create duplicate postings.
- **Notification/Audit:** process Kafka events with durable processed-event state so at-least-once delivery does not create duplicate side effects.

For architecture and API behavior, see `docs/wiki/Architecture.md` and `docs/wiki/API-Reference.md`.
