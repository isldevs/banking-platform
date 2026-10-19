# Database Ownership and Accounting Demonstration

Flyway executes `src/main/resources/db/migration/V1__...sql` separately in each service. PostgreSQL init script
provisions databases, DB users and dedicated credentials. Each app connects only to its owned database.

| Database     | Owned tables                                                                                              |
|--------------|-----------------------------------------------------------------------------------------------------------|
| config       | configurations                                                                                            |
| auth         | users, user_credentials, roles, permissions, user_roles, role_permissions, refresh_tokens, login_attempts |
| customer     | customers, customer_addresses, customer_kyc, customer_status_history                                      |
| account      | accounts, account_holders, account_balances, account_operations, account_transactions, ledger_entries     |
| transfer     | transfers, transfer_items, transfer_status_history, idempotency_keys, outbox_events                       |
| notification | notifications, processed_events                                                                           |
| audit        | audit_events, processed_events                                                                            |

## SQL reconciliation queries (inside Postgres)

```sql
-- Account DB: every posting must have balanced debits and credits.
SELECT transaction_id,
       SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE 0 END) AS total_debit,
       SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE 0 END) AS total_credit
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE 0 END)
    <> SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE 0 END);
-- Should return zero rows.

-- Account DB: prove only one financial operation per transferId.
SELECT operation_id,COUNT(*) FROM account_operations GROUP BY operation_id HAVING COUNT(*)>1;
-- Should return zero rows (PK enforced).

-- Transfer DB: outbox backlog.
SELECT status,COUNT(*) FROM outbox_events GROUP BY status;

-- Transfer DB: transfers needing reconciliation.
SELECT id,status,created_at FROM transfers WHERE status IN('PENDING','PENDING_RECONCILIATION');
```

Funds are created only through the demo-only offsetting clearing account. It is allowed to have a negative balance as a
controlled source of demonstration funds, **not** a valid real-bank funding process.

## Transaction boundaries

- `account-service`: lock source and destination in stable order; verify ownership/status/currency/balance; insert
  account operation; create account transactions and paired ledger entries; update balances; all under one PostgreSQL
  transaction.
- `transfer-service`: persist PENDING and idempotency record; call account operation by stable transfer UUID; COMMITTED
  reply transitions to COMPLETED, with outbox event in one transfer database transaction; on ambiguous result, reconcile
  with retry of identical financial operation ID.
- `notification-service` and `audit-service`: insert processed event ID and side effect under their **own** transactions
  to tolerate at-least-once delivery.
