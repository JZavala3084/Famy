-- Módulo celebration: cumpleaños, aniversarios y otras fechas anuales, con ideas de regalo.
CREATE TABLE celebration (
    id          UUID PRIMARY KEY,
    family_id   UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    type        VARCHAR(12) NOT NULL CHECK (type IN ('BIRTHDAY', 'ANNIVERSARY', 'OTHER')),
    person_name VARCHAR(80),
    -- Si es un miembro, la fecha sale de su perfil (app_user.birthday) y aquí solo van notas e ideas de regalo.
    user_id     UUID REFERENCES app_user (id) ON DELETE CASCADE,
    month       SMALLINT CHECK (month BETWEEN 1 AND 12),
    day         SMALLINT CHECK (day BETWEEN 1 AND 31),
    year        SMALLINT CHECK (year BETWEEN 1900 AND 2200),
    notes       VARCHAR(1000),
    gift_ideas  JSONB       NOT NULL DEFAULT '[]',
    deleted_at  TIMESTAMPTZ,
    created_by  UUID        NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,
    CHECK (user_id IS NOT NULL OR (person_name IS NOT NULL AND month IS NOT NULL AND day IS NOT NULL))
);
CREATE INDEX idx_celebration_family ON celebration (family_id, updated_at);
CREATE UNIQUE INDEX uq_celebration_member ON celebration (family_id, user_id)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;
