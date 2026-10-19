CREATE TABLE configurations
(
    id           UUID PRIMARY KEY,
    service_name VARCHAR(120) NOT NULL,
    environment  VARCHAR(30)  NOT NULL,
    config_key   VARCHAR(200) NOT NULL,
    config_value TEXT         NOT NULL,
    version      INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (service_name, environment, config_key, version)
);
CREATE INDEX idx_config_scope ON configurations (service_name, environment);
INSERT INTO configurations(id, service_name, environment, config_key, config_value, version)
VALUES (gen_random_uuid(), 'transfer-service', 'local', 'transfer.max-amount.usd', '10000.00', 1),
       (gen_random_uuid(), 'account-service', 'local', 'account.currency.default', 'USD', 1);
