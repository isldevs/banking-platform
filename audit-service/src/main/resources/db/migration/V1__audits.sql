CREATE TABLE audit_events
(
    id             UUID PRIMARY KEY,
    event_id       UUID UNIQUE  NOT NULL,
    actor_id       UUID,
    action         VARCHAR(100) NOT NULL,
    resource_type  VARCHAR(50)  NOT NULL,
    resource_id    UUID         NOT NULL,
    old_value      JSONB,
    new_value      JSONB,
    ip_address     VARCHAR(80),
    user_agent     TEXT,
    correlation_id VARCHAR(128),
    trace_id       VARCHAR(128),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_resource ON audit_events (resource_type, resource_id);
CREATE TABLE processed_events
(
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
