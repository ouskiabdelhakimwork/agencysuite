-- Etape 5, lot 1 : clients et contacts (module crm), US-401.
--
-- Champs repris du modele de donnees (04-modele-de-donnees.md, section 4), a ajuster par
-- migration apres l'atelier avec l'agence pilote. Ecarts assumes : adresse decoupee en
-- address_line / city / country, preferred_locale ajoute, tags et opportunites reportes.
--
-- created_by / updated_by : auteur de la ligne (user_account.id ou platform_user.id, NULL pour le
-- systeme), donc sans cle etrangere (AuthoredAgencyEntity).
-- email : minuscules ; phone : E.164 (+212612345678) ; country : ISO 3166-1 alpha-2 en majuscules
-- (VARCHAR et non CHAR, convention ddl-auto validate). Pas de suppression definitive d'un client :
-- archived_at (droits des personnes a l'etape 13b).

CREATE TABLE customer (
    id               UUID          PRIMARY KEY,
    agency_id        UUID          NOT NULL REFERENCES agency (id),
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('INDIVIDUAL', 'COMPANY')),
    name             VARCHAR(200)  NOT NULL,
    ice              VARCHAR(15),
    if_number        VARCHAR(20),
    rc               VARCHAR(50),
    email            VARCHAR(254),
    phone            VARCHAR(20),
    address_line     VARCHAR(300),
    city             VARCHAR(100),
    country          VARCHAR(2)    NOT NULL DEFAULT 'MA' CHECK (country ~ '^[A-Z]{2}$'),
    preferred_locale VARCHAR(5)    NOT NULL DEFAULT 'fr' CHECK (preferred_locale IN ('fr', 'ar', 'en')),
    sector           VARCHAR(100),
    source           VARCHAR(30)   CHECK (source IN ('WEBSITE', 'WHATSAPP', 'PHONE', 'EMAIL', 'WALK_IN',
                                                     'REFERRAL', 'SOCIAL_MEDIA', 'TRADE_SHOW', 'OTHER')),
    owner_user_id    UUID,
    notes            VARCHAR(2000),
    archived_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ck_customer_legal_ids_company_only
        CHECK (type = 'COMPANY' OR (ice IS NULL AND if_number IS NULL AND rc IS NULL)),
    -- Cible de la cle composee des contacts (un contact reste dans l'agence de son client)
    CONSTRAINT uk_customer_id_agency UNIQUE (id, agency_id),
    -- Le responsable est un utilisateur de la meme agence
    CONSTRAINT fk_customer_owner_same_agency
        FOREIGN KEY (owner_user_id, agency_id) REFERENCES user_account (id, agency_id)
);
-- Recherche et tri par nom (lower(name) LIKE / ORDER BY), doublons probables (email, phone, ice)
CREATE INDEX ix_customer_agency_name ON customer (agency_id, lower(name));
CREATE INDEX ix_customer_agency_email ON customer (agency_id, email);
CREATE INDEX ix_customer_agency_phone ON customer (agency_id, phone);
CREATE INDEX ix_customer_agency_ice ON customer (agency_id, ice);

ALTER TABLE customer ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

CREATE TABLE contact (
    id          UUID          PRIMARY KEY,
    agency_id   UUID          NOT NULL REFERENCES agency (id),
    customer_id UUID          NOT NULL,
    full_name   VARCHAR(200)  NOT NULL,
    job_title   VARCHAR(100),
    email       VARCHAR(254),
    phone       VARCHAR(20),
    is_primary  BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT fk_contact_customer_same_agency
        FOREIGN KEY (customer_id, agency_id) REFERENCES customer (id, agency_id)
);
CREATE INDEX ix_contact_customer ON contact (customer_id);
-- Un seul contact principal par client
CREATE UNIQUE INDEX uk_contact_one_primary ON contact (customer_id) WHERE is_primary;

ALTER TABLE contact ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contact
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Droits : SELECT, INSERT, UPDATE, DELETE pour agencysuite_app par les privileges par defaut de V3
-- (GRANT habituels). L'application ne supprime jamais un client (archivage) ; un contact, si.
