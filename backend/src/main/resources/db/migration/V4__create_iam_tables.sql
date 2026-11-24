-- Etape 3, lot 2 : utilisateurs, roles et permissions (module iam).
--
-- Les droits sont geres dans l'application, pas dans Keycloak (ADR-08) : Keycloak authentifie,
-- la base dit qui est actif et ce qu'il peut faire.
--
-- Roles systeme partages par toutes les agences (donnees de reference, sans agency_id) : rien
-- n'est copie a la creation d'une agence. Ajouter une permission = une nouvelle migration, avec
-- sa repartition dans les roles. Le role MANAGER recoit toujours toutes les permissions
-- (verifie par ReferenceDataTest). Les noms affiches sont dans les traductions (role.<CODE>.name).

-- 1. Donnees de reference (sans agency_id, sans RLS, lecture seule pour agencysuite_app)
CREATE TABLE permission (
    code        VARCHAR(50) PRIMARY KEY,
    module_code VARCHAR(30) REFERENCES module (code)  -- module souscrit requis (information seulement)
);

CREATE TABLE system_role (
    code VARCHAR(30) PRIMARY KEY
);

CREATE TABLE system_role_permission (
    role_code       VARCHAR(30) NOT NULL REFERENCES system_role (code),
    permission_code VARCHAR(50) NOT NULL REFERENCES permission (code),
    PRIMARY KEY (role_code, permission_code)
);

-- V3 donne par defaut SELECT, INSERT, UPDATE, DELETE sur les nouvelles tables : on ne garde que la lecture.
REVOKE INSERT, UPDATE, DELETE ON permission, system_role, system_role_permission FROM agencysuite_app;
GRANT SELECT ON permission, system_role, system_role_permission TO agencysuite_app;

INSERT INTO permission (code, module_code) VALUES
    ('user.manage',     NULL),
    ('audit.read',      NULL),
    ('privacy.manage',  NULL),
    ('identity.read',   NULL),
    ('ticketing.write', 'TICKETING'),
    ('billing.issue',   NULL);

INSERT INTO system_role (code) VALUES
    ('MANAGER'),
    ('SALES'),
    ('OPERATIONS'),
    ('ACCOUNTANT');

INSERT INTO system_role_permission (role_code, permission_code)
SELECT 'MANAGER', code FROM permission;

INSERT INTO system_role_permission (role_code, permission_code) VALUES
    ('SALES',      'identity.read'),
    ('SALES',      'ticketing.write'),
    ('OPERATIONS', 'identity.read'),
    ('OPERATIONS', 'ticketing.write'),
    ('ACCOUNTANT', 'billing.issue');

-- 2. Utilisateurs des agences (RLS obligatoire)
-- external_idp_id : identifiant "sub" de l'utilisateur dans Keycloak, unique sur toute la
-- plateforme (un utilisateur appartient a une seule agence en V1).
CREATE TABLE user_account (
    id              UUID         PRIMARY KEY,
    agency_id       UUID         NOT NULL REFERENCES agency (id),
    external_idp_id VARCHAR(36)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    full_name       VARCHAR(200),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT uk_user_account_external_idp_id UNIQUE (external_idp_id)
);
CREATE UNIQUE INDEX uk_user_account_agency_email ON user_account (agency_id, lower(email));

ALTER TABLE user_account ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_account
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Roles attribues (identifiant propre : une collection d'elements Hibernate n'ecrirait pas agency_id)
CREATE TABLE user_role (
    id         UUID        PRIMARY KEY,
    agency_id  UUID        NOT NULL REFERENCES agency (id),
    user_id    UUID        NOT NULL REFERENCES user_account (id),
    role_code  VARCHAR(30) NOT NULL REFERENCES system_role (code),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_code)
);
CREATE INDEX ix_user_role_agency ON user_role (agency_id);

ALTER TABLE user_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_role
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- 3. Equipe de l'editeur (back-office) : jamais visible depuis une agence
CREATE TABLE platform_user (
    id              UUID         PRIMARY KEY,
    external_idp_id VARCHAR(36)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    full_name       VARCHAR(200),
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN')),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT uk_platform_user_external_idp_id UNIQUE (external_idp_id)
);

ALTER TABLE platform_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY platform_only ON platform_user
    USING (app_platform_access())
    WITH CHECK (app_platform_access());
