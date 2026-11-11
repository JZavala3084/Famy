-- Módulo payment: pagos recurrentes (luz, agua, colegio...) y el registro de cada vencimiento pagado.
CREATE TABLE payment (
    id                 UUID PRIMARY KEY,
    family_id          UUID           NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    name               VARCHAR(120)   NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    currency           VARCHAR(3)     NOT NULL,
    -- Sin RRULE es un pago único.
    rrule              VARCHAR(255),
    first_due_on       DATE           NOT NULL,
    remind_days_before INTEGER        NOT NULL DEFAULT 3 CHECK (remind_days_before BETWEEN 0 AND 30),
    responsible_id     UUID REFERENCES app_user (id),
    -- Categoría de presupuesto donde se registrará el gasto (fase 6).
    category           VARCHAR(60),
    notes              VARCHAR(1000),
    deleted_at         TIMESTAMPTZ,
    created_by         UUID           NOT NULL REFERENCES app_user (id),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_family ON payment (family_id, updated_at);

-- Archivos subidos (comprobantes). El contenido vive en disco (FAMY_DATA_DIR/files), fuera de la base.
CREATE TABLE file_object (
    id           UUID PRIMARY KEY,
    family_id    UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    storage_key  VARCHAR(200) NOT NULL UNIQUE,
    filename     VARCHAR(200),
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    uploaded_by  UUID         NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Solo se guardan los vencimientos pagados: «pendiente» y «vencido» se calculan con la fecha.
CREATE TABLE payment_occurrence (
    id              UUID PRIMARY KEY,
    payment_id      UUID           NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    family_id       UUID           NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    due_on          DATE           NOT NULL,
    status          VARCHAR(10)    NOT NULL DEFAULT 'PAID' CHECK (status IN ('PAID')),
    paid_on         DATE           NOT NULL,
    paid_by         UUID           NOT NULL REFERENCES app_user (id),
    amount_paid     NUMERIC(14, 2) NOT NULL CHECK (amount_paid >= 0),
    note            VARCHAR(500),
    receipt_file_id UUID REFERENCES file_object (id) ON DELETE SET NULL,
    -- Gasto en Presupuesto (fase 6).
    transaction_id  UUID,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (payment_id, due_on)
);
CREATE INDEX idx_payment_occurrence_family ON payment_occurrence (family_id, due_on);
