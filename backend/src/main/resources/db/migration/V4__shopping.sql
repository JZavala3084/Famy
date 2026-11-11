-- Módulo shopping: listas e ítems. Los ítems borrados quedan con deleted_at para alimentar las sugerencias.
CREATE TABLE shopping_list (
    id          UUID PRIMARY KEY,
    family_id   UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    icon        VARCHAR(32),
    position    INTEGER     NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    created_by  UUID        NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_shopping_list_family ON shopping_list (family_id, updated_at);

CREATE TABLE shopping_item (
    id         UUID PRIMARY KEY,
    list_id    UUID         NOT NULL REFERENCES shopping_list (id) ON DELETE CASCADE,
    family_id  UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    quantity   NUMERIC(10, 3) CHECK (quantity IS NULL OR quantity > 0),
    unit       VARCHAR(20),
    category   VARCHAR(40),
    note       VARCHAR(500),
    checked_at TIMESTAMPTZ,
    checked_by UUID REFERENCES app_user (id),
    position   INTEGER      NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_by UUID         NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_shopping_item_list ON shopping_item (list_id, position) WHERE deleted_at IS NULL;
CREATE INDEX idx_shopping_item_family ON shopping_item (family_id, updated_at);
CREATE INDEX idx_shopping_item_name ON shopping_item (family_id, lower(name));
