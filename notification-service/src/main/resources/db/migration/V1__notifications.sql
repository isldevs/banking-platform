CREATE TABLE notifications
(
    id                UUID PRIMARY KEY,
    event_id          UUID         NOT NULL UNIQUE,
    customer_id       UUID,
    recipient_user_id UUID,
    channel           VARCHAR(20)  NOT NULL,
    type              VARCHAR(100) NOT NULL,
    subject           VARCHAR(255),
    message           TEXT,
    status            VARCHAR(20)  NOT NULL,
    attempt_count     INT          NOT NULL DEFAULT 0,
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at           TIMESTAMPTZ
);
CREATE TABLE processed_events
(
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications (recipient_user_id, created_at DESC);
