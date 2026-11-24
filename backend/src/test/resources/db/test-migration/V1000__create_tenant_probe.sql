-- Table sonde, presente uniquement en test : une entite metier minimale appartenant a une
-- agence, qui sert a verifier le mecanisme multi-tenant independamment des futurs modules.
-- Elle suit exactement la regle imposee aux vraies tables (agency_id + RLS + politique).
CREATE TABLE tenant_probe (
    id         UUID         PRIMARY KEY,
    agency_id  UUID         NOT NULL REFERENCES agency (id),
    label      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL
);

ALTER TABLE tenant_probe ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_probe
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());
