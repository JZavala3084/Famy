-- Módulo calendar: eventos familiares con repetición (RRULE), participantes y recordatorios.
CREATE TABLE calendar_event (
    id               UUID PRIMARY KEY,
    family_id        UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    title            VARCHAR(120) NOT NULL,
    description      VARCHAR(2000),
    location         VARCHAR(200),
    -- Primera ocurrencia (DTSTART). Los de todo el día empiezan a medianoche en la zona de la familia.
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    all_day          BOOLEAN      NOT NULL DEFAULT false,
    rrule            VARCHAR(255),
    color            VARCHAR(7),
    participant_ids  UUID[]       NOT NULL DEFAULT '{}',
    -- Minutos antes del inicio en que se avisa a los participantes (máx. 7 días).
    reminder_minutes INTEGER[]    NOT NULL DEFAULT '{}',
    -- Ocurrencias canceladas de un evento que se repite (EXDATE).
    excluded_starts  TIMESTAMPTZ[] NOT NULL DEFAULT '{}',
    deleted_at       TIMESTAMPTZ,
    created_by       UUID         NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CHECK (ends_at >= starts_at)
);
CREATE INDEX idx_calendar_event_family ON calendar_event (family_id, updated_at);
CREATE INDEX idx_calendar_event_start ON calendar_event (family_id, starts_at) WHERE deleted_at IS NULL;
