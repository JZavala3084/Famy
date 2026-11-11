-- Módulo chore: tareas del hogar con repetición (RRULE), rotación entre miembros, historial y puntos.
CREATE TABLE chore (
    id                  UUID PRIMARY KEY,
    family_id           UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    title               VARCHAR(120) NOT NULL,
    description         VARCHAR(1000),
    due_at              TIMESTAMPTZ,
    rrule               VARCHAR(255),
    -- DTSTART de la recurrencia: la fecha límite cuando se definió la regla.
    recurrence_start    TIMESTAMPTZ,
    points              INTEGER      NOT NULL DEFAULT 0 CHECK (points BETWEEN 0 AND 1000),
    rotation            BOOLEAN      NOT NULL DEFAULT false,
    rotation_order      UUID[]       NOT NULL DEFAULT '{}',
    current_assignee_id UUID REFERENCES app_user (id),
    -- Para tareas sin repetición (o cuya repetición terminó): cuándo quedó hecha.
    completed_at        TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          UUID         NOT NULL REFERENCES app_user (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CHECK (rrule IS NULL OR (due_at IS NOT NULL AND recurrence_start IS NOT NULL))
);
CREATE INDEX idx_chore_family ON chore (family_id, updated_at);
CREATE INDEX idx_chore_due ON chore (family_id, due_at) WHERE deleted_at IS NULL AND completed_at IS NULL;

CREATE TABLE chore_completion (
    id                    UUID PRIMARY KEY,
    chore_id              UUID        NOT NULL REFERENCES chore (id) ON DELETE CASCADE,
    family_id             UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    completed_by          UUID        NOT NULL REFERENCES app_user (id),
    recorded_by           UUID        NOT NULL REFERENCES app_user (id),
    completed_at          TIMESTAMPTZ NOT NULL,
    occurrence_date       DATE        NOT NULL,
    -- Puntos al momento de completarla (el marcador no cambia si después se editan los puntos de la tarea).
    points                INTEGER     NOT NULL,
    -- Estado anterior de la tarea, para poder deshacer la última marca.
    previous_due_at       TIMESTAMPTZ,
    previous_assignee_id  UUID
);
CREATE INDEX idx_chore_completion_chore ON chore_completion (chore_id, completed_at DESC);
CREATE INDEX idx_chore_completion_family ON chore_completion (family_id, completed_at);
