CREATE TABLE account_products
(
    product_code            VARCHAR(40) PRIMARY KEY,
    account_type            VARCHAR(20) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    initial_deposit_required BOOLEAN NOT NULL DEFAULT FALSE,
    initial_deposit_amount  NUMERIC(20, 2) NOT NULL DEFAULT 0,
    minimum_opening_balance NUMERIC(20, 2) NOT NULL DEFAULT 0,
    minimum_operating_balance NUMERIC(20, 2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_account_products_initial_amount CHECK (initial_deposit_amount >= 0),
    CONSTRAINT ck_account_products_min_opening CHECK (minimum_opening_balance >= 0),
    CONSTRAINT ck_account_products_min_operating CHECK (minimum_operating_balance >= 0),
    CONSTRAINT uq_account_products_currency UNIQUE (product_code, currency)
);

CREATE INDEX idx_account_products_currency_status
    ON account_products (currency, status);

CREATE TABLE account_opening_requests
(
    idempotency_key VARCHAR(120) PRIMARY KEY,
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL UNIQUE REFERENCES accounts (id),
    product_code VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    requested_initial_deposit NUMERIC(20, 2) NOT NULL DEFAULT 0,
    funding_channel VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE funding_instructions
(
    id                 UUID PRIMARY KEY,
    account_id         UUID NOT NULL REFERENCES accounts (id),
    amount             NUMERIC(20, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3) NOT NULL,
    channel            VARCHAR(30) NOT NULL,
    status             VARCHAR(30) NOT NULL,
    idempotency_key    VARCHAR(120) NOT NULL UNIQUE,
    external_reference VARCHAR(120),
    operation_id       UUID REFERENCES account_operations (operation_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_at          TIMESTAMPTZ,
    failed_at          TIMESTAMPTZ
);

CREATE INDEX idx_funding_instructions_account_status
    ON funding_instructions (account_id, status);
CREATE INDEX idx_funding_instructions_external_reference
    ON funding_instructions (external_reference);

INSERT INTO account_products (
    product_code,
    account_type,
    currency,
    initial_deposit_required,
    initial_deposit_amount,
    minimum_opening_balance,
    minimum_operating_balance
)
VALUES
    ('BASIC-USD', 'CUSTOMER', 'USD', FALSE, 0, 0, 0),
    ('BASIC-KHR', 'CUSTOMER', 'KHR', FALSE, 0, 0, 0);
