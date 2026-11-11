-- Módulo realtime: registro de cambios para recuperar lo perdido al reconectar (GET /changes?since=cursor).
CREATE TABLE change_log (
    id          BIGSERIAL PRIMARY KEY,
    family_id   UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    entity      VARCHAR(40) NOT NULL,
    entity_id   UUID        NOT NULL,
    op          CHAR(1)     NOT NULL CHECK (op IN ('C', 'U', 'D')),
    version     BIGINT      NOT NULL,
    actor_id    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload     JSONB       NOT NULL
);
CREATE INDEX idx_change_log_family ON change_log (family_id, id);
CREATE INDEX idx_change_log_occurred ON change_log (occurred_at);

-- Hasta qué cursor se purgó (los clientes con un cursor anterior deben recargar todo).
CREATE TABLE change_log_watermark (
    id           SMALLINT PRIMARY KEY CHECK (id = 1),
    purged_up_to BIGINT NOT NULL DEFAULT 0
);
INSERT INTO change_log_watermark (id, purged_up_to) VALUES (1, 0);
