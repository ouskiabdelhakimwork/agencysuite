-- Etape 3, lot 4 : journal d'audit (module iam), en ajout seul.
--
-- Une entree est ecrite dans la transaction de l'action tracee : les deux sont enregistrees,
-- ou aucune. Exception justifiee a la regle multi-tenant : l'entite AuditEntry n'herite pas de
-- AgencyScopedEntity, car une action du back-office (perimetre plateforme) ecrit une entree pour
-- l'agence ciblee. agency_id est donc explicite ; la RLS ci-dessous reste la barriere.
--
-- details : codes techniques seulement (JSONB), jamais d'e-mail, de nom ni de numero de document.
-- ip : adresse distante de la requete (donnee personnelle), lisible seulement en base.
-- actor_id : user_account.id ou platform_user.id selon actor_type, donc sans cle etrangere.
CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY,
    agency_id   UUID         NOT NULL REFERENCES agency (id),
    actor_type  VARCHAR(20)  NOT NULL CHECK (actor_type IN ('AGENCY_USER', 'PLATFORM_USER', 'SYSTEM')),
    actor_id    UUID,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   VARCHAR(100),
    details     JSONB        NOT NULL DEFAULT '{}',
    ip          VARCHAR(45),
    occurred_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_audit_log_actor CHECK ((actor_type = 'SYSTEM') = (actor_id IS NULL))
);
CREATE INDEX ix_audit_log_agency_time ON audit_log (agency_id, occurred_at DESC);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_log
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Ajout seul : V3 donne par defaut SELECT, INSERT, UPDATE, DELETE sur les nouvelles tables.
-- La purge selon la duree de conservation (a valider) sera une tache dediee, pas l'application.
REVOKE UPDATE, DELETE ON audit_log FROM agencysuite_app;
GRANT SELECT, INSERT ON audit_log TO agencysuite_app;

-- Reporte du lot 2 : un role ne peut etre rattache qu'a un utilisateur de la meme agence.
ALTER TABLE user_account ADD CONSTRAINT uk_user_account_id_agency UNIQUE (id, agency_id);
ALTER TABLE user_role ADD CONSTRAINT fk_user_role_user_same_agency
    FOREIGN KEY (user_id, agency_id) REFERENCES user_account (id, agency_id);
