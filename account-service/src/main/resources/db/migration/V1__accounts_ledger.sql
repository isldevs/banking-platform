CREATE TABLE accounts
(
    id             UUID PRIMARY KEY,
    account_number VARCHAR(60) NOT NULL UNIQUE,
    customer_id    UUID,
    owner_user_id  UUID,
    currency       VARCHAR(3)  NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    account_type   VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_accounts_owner ON accounts (owner_user_id);
CREATE TABLE account_holders
(
    account_id  UUID        NOT NULL REFERENCES accounts (id),
    customer_id UUID        NOT NULL,
    holder_type VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (account_id, customer_id)
);
CREATE TABLE account_balances
(
    account_id        UUID PRIMARY KEY REFERENCES accounts (id),
    ledger_balance    NUMERIC(20, 2) NOT NULL DEFAULT 0,
    available_balance NUMERIC(20, 2) NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE TABLE account_operations
(
    operation_id           UUID PRIMARY KEY,
    source_account_id      UUID           NOT NULL REFERENCES accounts (id),
    destination_account_id UUID           NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(20, 2) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    requested_by           VARCHAR(80)    NOT NULL,
    status                 VARCHAR(30)    NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE TABLE account_transactions
(
    id               UUID PRIMARY KEY,
    operation_id     UUID           NOT NULL REFERENCES account_operations (operation_id),
    account_id       UUID           NOT NULL REFERENCES accounts (id),
    transaction_type VARCHAR(20)    NOT NULL,
    amount           NUMERIC(20, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    reference_id     UUID           NOT NULL,
    status           VARCHAR(30)    NOT NULL DEFAULT 'COMPLETED',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (operation_id, account_id)
);
CREATE TABLE ledger_entries
(
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES account_operations (operation_id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    entry_type     VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(20, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledger_tx ON ledger_entries (transaction_id);
INSERT INTO accounts(id, account_number, currency, status, account_type)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEMO-CLEARING-USD', 'USD', 'ACTIVE', 'SYSTEM'),
       ('00000000-0000-0000-0000-000000000002', 'DEMO-CLEARING-KHR', 'KHR', 'ACTIVE', 'SYSTEM');
INSERT INTO account_balances(account_id)
VALUES ('00000000-0000-0000-0000-000000000001'),
       ('00000000-0000-0000-0000-000000000002');
