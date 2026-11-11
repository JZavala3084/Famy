-- Módulo identity: usuarios, identidades externas, refresh tokens, tokens de correo y auditoría.
CREATE TABLE app_user (
    id                UUID PRIMARY KEY,
    -- La aplicación guarda el correo normalizado en minúsculas; el CHECK lo garantiza.
    email             VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    email_verified_at TIMESTAMPTZ,
    password_hash     VARCHAR(255),
    display_name      VARCHAR(80)  NOT NULL,
    avatar_url        VARCHAR(500),
    color             VARCHAR(7),
    birthday          DATE,
    timezone          VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    locale            VARCHAR(16)  NOT NULL DEFAULT 'es',
    currency          VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_identity (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider         VARCHAR(16)  NOT NULL CHECK (provider IN ('GOOGLE')),
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(254),
    linked_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_subject)
);
CREATE INDEX idx_user_identity_user ON user_identity (user_id);

CREATE TABLE refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)     NOT NULL UNIQUE,
    device_name     VARCHAR(100),
    family_chain_id UUID         NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    created_ip      VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_chain ON refresh_token (family_chain_id);

CREATE TABLE email_token (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose    VARCHAR(16) NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')),
    token_hash VARCHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_token_user ON email_token (user_id, purpose);

-- Sin FK a propósito: la auditoría se conserva aunque se borren usuarios o familias.
CREATE TABLE audit_log (
    id         UUID PRIMARY KEY,
    user_id    UUID,
    family_id  UUID,
    action     VARCHAR(64) NOT NULL,
    ip         VARCHAR(45),
    user_agent VARCHAR(255),
    details    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_user ON audit_log (user_id, created_at);
CREATE INDEX idx_audit_log_family ON audit_log (family_id, created_at);
