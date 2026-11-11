-- Cambios que solo ven los adultos (pagos; los hijos no ven pagos ni presupuesto, decisión P-4).
ALTER TABLE change_log
    ADD COLUMN audience VARCHAR(10) NOT NULL DEFAULT 'FAMILY' CHECK (audience IN ('FAMILY', 'ADULTS'));
