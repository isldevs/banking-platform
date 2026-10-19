CREATE TABLE users
(
    id                 UUID PRIMARY KEY,
    username           VARCHAR(100) UNIQUE NOT NULL,
    email              VARCHAR(255),
    status             VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT                 NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);
CREATE TABLE user_credentials
(
    id                  UUID PRIMARY KEY,
    user_id             UUID        NOT NULL UNIQUE REFERENCES users (id),
    password_hash       TEXT        NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE roles
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE permissions
(
    id          UUID PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE TABLE user_roles
(
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE TABLE role_permissions
(
    role_id       UUID NOT NULL REFERENCES roles (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);
CREATE TABLE refresh_tokens
(
    id          UUID PRIMARY KEY,
    user_id     UUID               NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ        NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    created_at  TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);
CREATE TABLE login_attempts
(
    id         UUID PRIMARY KEY,
    username   VARCHAR(100),
    user_id    UUID,
    success    BOOLEAN     NOT NULL,
    ip_address VARCHAR(80),
    user_agent VARCHAR(400),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_login_attempts_created ON login_attempts (created_at);
