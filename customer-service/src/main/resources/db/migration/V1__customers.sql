CREATE TABLE customers
(
    id              UUID PRIMARY KEY,
    customer_number VARCHAR(40) UNIQUE NOT NULL,
    user_id         UUID               NOT NULL,
    first_name      VARCHAR(100)       NOT NULL,
    last_name       VARCHAR(100)       NOT NULL,
    date_of_birth   DATE               NOT NULL,
    phone           VARCHAR(40),
    email           VARCHAR(255),
    status          VARCHAR(30)        NOT NULL DEFAULT 'PENDING_KYC',
    created_at      TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customer_user ON customers (user_id);
CREATE TABLE customer_addresses
(
    id           UUID PRIMARY KEY,
    customer_id  UUID        NOT NULL REFERENCES customers (id),
    address_line TEXT,
    city         VARCHAR(120),
    country      VARCHAR(120),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE customer_kyc
(
    id                  UUID PRIMARY KEY,
    customer_id         UUID        NOT NULL UNIQUE REFERENCES customers (id),
    kyc_level           VARCHAR(30) NOT NULL DEFAULT 'BASIC',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verified_at         TIMESTAMPTZ,
    verified_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE customer_status_history
(
    id          UUID PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    old_status  VARCHAR(30),
    new_status  VARCHAR(30) NOT NULL,
    reason      TEXT,
    changed_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
