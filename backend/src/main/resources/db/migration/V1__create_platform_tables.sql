-- Etape 2, lot 1 : tables de la plateforme (module platform).
-- Regles : identifiants UUID v7 generes par l'application ; horodatages en TIMESTAMPTZ (UTC) ;
-- montants en NUMERIC(19,4) ; valeurs d'enum en VARCHAR avec contrainte CHECK.
-- Une migration publiee n'est jamais modifiee : toute evolution passe par une nouvelle migration.

-- Paliers du socle (donnees de reference, cle naturelle)
CREATE TABLE plan (
    code             VARCHAR(30)   PRIMARY KEY,
    max_users        INTEGER       CHECK (max_users IS NULL OR max_users > 0),  -- NULL = illimite
    storage_quota_gb INTEGER       NOT NULL CHECK (storage_quota_gb > 0),
    price_monthly    NUMERIC(19,4) CHECK (price_monthly IS NULL OR price_monthly >= 0),  -- NULL = prix a definir
    price_yearly     NUMERIC(19,4) CHECK (price_yearly IS NULL OR price_yearly >= 0),
    currency         VARCHAR(3)    NOT NULL DEFAULT 'MAD'
);

-- Catalogue des modules activables (donnees de reference, cle naturelle).
-- Les noms affiches sont dans les fichiers de traduction (cle module.<CODE>), pas en base.
CREATE TABLE module (
    code          VARCHAR(30)   PRIMARY KEY,
    phase         SMALLINT      NOT NULL CHECK (phase BETWEEN 1 AND 3),
    price_monthly NUMERIC(19,4) CHECK (price_monthly IS NULL OR price_monthly >= 0),
    currency      VARCHAR(3)    NOT NULL DEFAULT 'MAD'
);

-- L'agence est le tenant : elle ne porte pas d'agency_id.
CREATE TABLE agency (
    id               UUID          PRIMARY KEY,
    name             VARCHAR(200)  NOT NULL,
    legal_name       VARCHAR(200),
    ice              VARCHAR(15),
    if_number        VARCHAR(20),
    rc               VARCHAR(30),
    patente          VARCHAR(30),
    address          VARCHAR(500),
    primary_color    VARCHAR(7)    CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    subdomain        VARCHAR(63)   NOT NULL,
    default_locale   VARCHAR(5)    NOT NULL DEFAULT 'fr' CHECK (default_locale IN ('fr', 'ar', 'en')),
    default_currency VARCHAR(3)    NOT NULL DEFAULT 'MAD',
    timezone         VARCHAR(50)   NOT NULL DEFAULT 'Africa/Casablanca',
    kind             VARCHAR(20)   NOT NULL CHECK (kind IN ('CUSTOMER', 'DEMO')),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CLOSING', 'CLOSED')),
    demo_reset_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL,
    CONSTRAINT uk_agency_subdomain UNIQUE (subdomain)
);

-- Abonnement de l'agence au socle (un seul abonnement actif a la fois)
CREATE TABLE subscription (
    id         UUID        PRIMARY KEY,
    agency_id  UUID        NOT NULL REFERENCES agency (id),
    plan_code  VARCHAR(30) NOT NULL REFERENCES plan (code),
    period     VARCHAR(10) NOT NULL CHECK (period IN ('MONTHLY', 'YEARLY')),
    status     VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED')),
    start_date DATE        NOT NULL,
    end_date   DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT ck_subscription_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_subscription_agency ON subscription (agency_id);
CREATE UNIQUE INDEX uk_subscription_one_active ON subscription (agency_id) WHERE status = 'ACTIVE';

-- Modules souscrits par l'agence (active ou desactive depuis le back-office ; les donnees restent)
CREATE TABLE agency_module (
    agency_id   UUID        NOT NULL REFERENCES agency (id),
    module_code VARCHAR(30) NOT NULL REFERENCES module (code),
    enabled     BOOLEAN     NOT NULL,
    enabled_at  TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ,
    PRIMARY KEY (agency_id, module_code)
);
