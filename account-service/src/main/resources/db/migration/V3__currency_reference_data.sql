CREATE TABLE currencies
(
    code        VARCHAR(3) PRIMARY KEY,
    numeric_code VARCHAR(3),
    minor_units SMALLINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_currencies_minor_units CHECK (minor_units BETWEEN 0 AND 9)
);

INSERT INTO currencies (code, numeric_code, minor_units)
VALUES
    ('USD', '840', 2),
    ('KHR', '116', 2),
    ('EUR', '978', 2),
    ('GBP', '826', 2),
    ('JPY', '392', 0),
    ('SGD', '702', 2),
    ('THB', '764', 2)
ON CONFLICT (code) DO NOTHING;
