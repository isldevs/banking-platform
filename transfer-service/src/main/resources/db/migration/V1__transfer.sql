CREATE TABLE transfers
(
    id                     UUID PRIMARY KEY,
    transfer_reference     VARCHAR(100)   NOT NULL UNIQUE,
    source_account_id      UUID           NOT NULL,
    destination_account_id UUID           NOT NULL,
    amount                 NUMERIC(20, 2) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    status                 VARCHAR(32)    NOT NULL,
    requested_by           UUID           NOT NULL,
    client_reference       TEXT,
    failure_code           VARCHAR(120),
    failure_reason         TEXT,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_transfers_pending ON transfers (status, created_at);
CREATE TABLE transfer_items
(
    id          UUID PRIMARY KEY,
    transfer_id UUID           NOT NULL REFERENCES transfers (id),
    account_id  UUID           NOT NULL,
    item_type   VARCHAR(10)    NOT NULL,
    amount      NUMERIC(20, 2) NOT NULL
);
CREATE TABLE transfer_status_history
(
    id          UUID PRIMARY KEY,
    transfer_id UUID        NOT NULL REFERENCES transfers (id),
    old_status  VARCHAR(32),
    new_status  VARCHAR(32) NOT NULL,
    reason      TEXT,
    changed_by  VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE idempotency_keys
(
    id              UUID PRIMARY KEY,
    client_id       UUID         NOT NULL,
    client_key      VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    transfer_id     UUID         NOT NULL REFERENCES transfers (id),
    response_status INT,
    expires_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW() + INTERVAL '30 days',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, client_key)
);
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    attempt_count  INT          NOT NULL DEFAULT 0,
    available_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, event_type)
);
CREATE INDEX idx_outbox_pending ON outbox_events (status, available_at);
