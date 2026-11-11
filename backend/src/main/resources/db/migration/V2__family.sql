-- Módulo family: familias, membresías, invitaciones y solicitudes de ingreso.
CREATE TABLE family (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(80)  NOT NULL,
    avatar_url         VARCHAR(500),
    currency           VARCHAR(3)      NOT NULL DEFAULT 'USD',
    timezone           VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    owner_id           UUID         NOT NULL REFERENCES app_user (id),
    members_can_invite BOOLEAN      NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE family_member (
    family_id UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES app_user (id),
    role      VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'CHILD')),
    nickname  VARCHAR(80),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ,
    version   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (family_id, user_id)
);
CREATE INDEX idx_family_member_user ON family_member (user_id) WHERE left_at IS NULL;
-- Una familia tiene exactamente un OWNER activo.
CREATE UNIQUE INDEX uq_family_single_owner ON family_member (family_id) WHERE role = 'OWNER' AND left_at IS NULL;

CREATE TABLE family_invitation (
    id                UUID PRIMARY KEY,
    family_id         UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    code              VARCHAR(8)     NOT NULL UNIQUE,
    token_hash        VARCHAR(64)    NOT NULL UNIQUE,
    role              VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'CHILD')),
    email             VARCHAR(254) CHECK (email = lower(email)),
    max_uses          INTEGER CHECK (max_uses IS NULL OR max_uses > 0),
    uses              INTEGER     NOT NULL DEFAULT 0,
    requires_approval BOOLEAN     NOT NULL DEFAULT true,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    created_by        UUID        NOT NULL REFERENCES app_user (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_family_invitation_family ON family_invitation (family_id, created_at);

CREATE TABLE join_request (
    id            UUID PRIMARY KEY,
    family_id     UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES app_user (id),
    invitation_id UUID REFERENCES family_invitation (id) ON DELETE SET NULL,
    role          VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'CHILD')),
    status        VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    decided_by    UUID REFERENCES app_user (id),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_join_request_pending ON join_request (family_id, user_id) WHERE status = 'PENDING';
CREATE INDEX idx_join_request_user ON join_request (user_id) WHERE status = 'PENDING';
