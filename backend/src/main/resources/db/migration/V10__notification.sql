-- Módulo notification: centro de notificaciones, recordatorios programados y push web (VAPID).
CREATE TABLE notification (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id  UUID REFERENCES family (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(500),
    link       VARCHAR(200),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_preference (
    user_id     UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- Tipos sin push (siguen apareciendo en el centro de notificaciones).
    muted_types VARCHAR(20)[] NOT NULL DEFAULT '{}',
    -- Horario de silencio en la zona del usuario (puede cruzar la medianoche).
    quiet_from  TIME,
    quiet_to    TIME,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Suscripciones Web Push de cada navegador.
CREATE TABLE push_subscription (
    id              UUID PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint        VARCHAR(1000) NOT NULL UNIQUE,
    p256dh          VARCHAR(200)  NOT NULL,
    auth            VARCHAR(100)  NOT NULL,
    user_agent      VARCHAR(200),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ
);
CREATE INDEX idx_push_subscription_user ON push_subscription (user_id);

-- Recordatorios ya enviados (clave estable: tipo, id, ocurrencia y anticipación), para no repetirlos.
CREATE TABLE reminder_log (
    reminder_key VARCHAR(200) PRIMARY KEY,
    family_id    UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    fire_at      TIMESTAMPTZ NOT NULL,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reminder_log_sent ON reminder_log (sent_at);

-- Hasta dónde revisó el job de recordatorios (una fila; FOR UPDATE SKIP LOCKED evita dos ejecuciones a la vez).
CREATE TABLE reminder_checkpoint (
    id            SMALLINT PRIMARY KEY CHECK (id = 1),
    checked_up_to TIMESTAMPTZ NOT NULL
);
INSERT INTO reminder_checkpoint (id, checked_up_to) VALUES (1, now());
