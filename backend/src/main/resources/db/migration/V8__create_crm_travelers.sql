-- Etape 5, lot 2 : voyageurs (module crm) et rattachement des voyageurs aux clients.
--
-- Champs repris du modele de donnees (04-modele-de-donnees.md, section 4), a ajuster par
-- migration apres l'atelier avec l'agence pilote. Aucune piece d'identite ici (ni numero de
-- passeport, ni numero de CIN, ni fichier) : elles arrivent a l'etape 4 (table identity_document
-- chiffree).
--
-- nationality : ISO 3166-1 alpha-2 en majuscules, comme customer.country. La MRZ des passeports
-- utilise les codes alpha-3 de l'OACI : la conversion sera faite par l'OCR (etape 4).
-- birth_date : en clair, lisible et modifiable avec la permission identity.read seulement
-- (RG-14, a valider) ; jamais critere de recherche.
-- name_key : "nom prenom" en minuscules, sans accents, espaces reduits ; calcule par le domaine a
-- chaque ecriture, sert a la recherche et aux doublons, jamais renvoye par l'API.
-- email : minuscules ; phone et emergency_contact_phone : E.164.
-- anonymized_at : reserve a l'etape 13b (droits des personnes), jamais rempli a ce lot.
-- created_by / updated_by : auteur de la ligne, sans cle etrangere (AuthoredAgencyEntity).

CREATE TABLE traveler (
    id                      UUID          PRIMARY KEY,
    agency_id               UUID          NOT NULL REFERENCES agency (id),
    last_name               VARCHAR(100)  NOT NULL,
    first_name              VARCHAR(100)  NOT NULL,
    last_name_ar            VARCHAR(100),
    first_name_ar           VARCHAR(100),
    name_key                VARCHAR(210)  NOT NULL,
    gender                  VARCHAR(20)   CHECK (gender IN ('FEMALE', 'MALE', 'UNSPECIFIED')),
    birth_date              DATE,
    birth_place             VARCHAR(100),
    nationality             VARCHAR(2)    CHECK (nationality ~ '^[A-Z]{2}$'),
    email                   VARCHAR(254),
    phone                   VARCHAR(20),
    dietary                 VARCHAR(200),
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    notes                   VARCHAR(2000),
    archived_at             TIMESTAMPTZ,
    anonymized_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    -- Cible de la cle composee des liens (un lien reste dans l'agence du voyageur)
    CONSTRAINT uk_traveler_id_agency UNIQUE (id, agency_id),
    CONSTRAINT ck_traveler_birth_date CHECK (birth_date IS NULL OR birth_date > DATE '1900-01-01')
);
-- Recherche et tri par name_key, doublons probables (name_key, email, phone)
CREATE INDEX ix_traveler_agency_name_key ON traveler (agency_id, name_key);
CREATE INDEX ix_traveler_agency_email ON traveler (agency_id, email);
CREATE INDEX ix_traveler_agency_phone ON traveler (agency_id, phone);

ALTER TABLE traveler ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON traveler
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Un voyageur rattache a zero, un ou plusieurs clients de la meme agence. Pas de "relation"
-- (salarie, famille...) a ce lot. Suppression reelle d'un lien (ce n'est pas une donnee de
-- facturation) ; pas de version.
CREATE TABLE customer_traveler (
    agency_id   UUID         NOT NULL REFERENCES agency (id),
    customer_id UUID         NOT NULL,
    traveler_id UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    CONSTRAINT pk_customer_traveler PRIMARY KEY (customer_id, traveler_id),
    CONSTRAINT fk_customer_traveler_customer_same_agency
        FOREIGN KEY (customer_id, agency_id) REFERENCES customer (id, agency_id),
    CONSTRAINT fk_customer_traveler_traveler_same_agency
        FOREIGN KEY (traveler_id, agency_id) REFERENCES traveler (id, agency_id)
);
CREATE INDEX ix_customer_traveler_traveler ON customer_traveler (traveler_id);

ALTER TABLE customer_traveler ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_traveler
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Droits : SELECT, INSERT, UPDATE, DELETE pour agencysuite_app par les privileges par defaut de V3.
