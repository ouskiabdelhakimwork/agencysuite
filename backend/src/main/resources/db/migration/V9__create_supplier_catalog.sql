-- Etape 5, lot 3 : fournisseurs, prestations et tarifs d'achat (module supplier, US-601).
--
-- Champs repris du modele de donnees (04-modele-de-donnees.md, section 5).
-- Pas de prix de vente conseille (sale_price_suggested du modele) : la marge et le prix de vente
-- arrivent avec les devis (etape 6). Pas de table allotment (contingents, US-602, P3).
--
-- Montants : NUMERIC(19,4) + devise ISO 4217 (ADR-15), jamais arrondis a la saisie : un montant
-- qui a plus de decimales que sa devise est refuse par l'application.
-- Textes traduisibles saisis par l'agence (product.name, product.description) : JSONB
-- {"fr": "...", "ar": "...", "en": "..."}, cles controlees par l'application.
-- name_key / search_key : cles de recherche sans accents (SearchKeys), jamais renvoyees par l'API.
-- email : minuscules ; phone : E.164.
-- created_by / updated_by : auteur de la ligne, sans cle etrangere (AuthoredAgencyEntity).

CREATE TABLE supplier (
    id                UUID          PRIMARY KEY,
    agency_id         UUID          NOT NULL REFERENCES agency (id),
    type              VARCHAR(30)   NOT NULL CHECK (type IN ('HOTEL', 'RIAD', 'TRANSPORT', 'AIRLINE', 'DMC',
                          'RESTAURANT', 'VENUE', 'GUIDE', 'ACTIVITY', 'TECHNICAL', 'INSURER', 'OTHER')),
    name              VARCHAR(200)  NOT NULL,
    name_key          VARCHAR(200)  NOT NULL,
    ice               VARCHAR(15),
    email             VARCHAR(254),
    phone             VARCHAR(20),
    contact_name      VARCHAR(200),
    address_line      VARCHAR(300),
    city              VARCHAR(100),
    country           VARCHAR(2)    NOT NULL DEFAULT 'MA' CHECK (country ~ '^[A-Z]{2}$'),
    default_currency  VARCHAR(3)    NOT NULL DEFAULT 'MAD' CHECK (default_currency ~ '^[A-Z]{3}$'),
    payment_terms     VARCHAR(500),
    notes             VARCHAR(2000),
    archived_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    -- Cible de la cle composee des prestations (une prestation reste dans l'agence du fournisseur)
    CONSTRAINT uk_supplier_id_agency UNIQUE (id, agency_id)
);
-- Recherche et tri par name_key, doublons probables (ice, email, phone)
CREATE INDEX ix_supplier_agency_name_key ON supplier (agency_id, name_key);
CREATE INDEX ix_supplier_agency_ice ON supplier (agency_id, ice);
CREATE INDEX ix_supplier_agency_email ON supplier (agency_id, email);
CREATE INDEX ix_supplier_agency_phone ON supplier (agency_id, phone);

ALTER TABLE supplier ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON supplier
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Prestation d'un fournisseur. search_key : cles de recherche du nom dans toutes ses langues.
-- cancellation_policy : texte interne, non structure a ce lot.
CREATE TABLE product (
    id                   UUID          PRIMARY KEY,
    agency_id            UUID          NOT NULL REFERENCES agency (id),
    supplier_id          UUID          NOT NULL,
    type                 VARCHAR(30)   NOT NULL CHECK (type IN ('ACCOMMODATION', 'TRANSFER', 'EXCURSION',
                             'ROOM_HIRE', 'CATERING', 'FLIGHT', 'OTHER')),
    name                 JSONB         NOT NULL,
    description          JSONB,
    search_key           VARCHAR(700)  NOT NULL,
    cancellation_policy  VARCHAR(2000),
    archived_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    -- Cible de la cle composee des tarifs (un tarif reste dans l'agence de sa prestation)
    CONSTRAINT uk_product_id_agency UNIQUE (id, agency_id),
    CONSTRAINT fk_product_supplier_same_agency
        FOREIGN KEY (supplier_id, agency_id) REFERENCES supplier (id, agency_id)
);
CREATE INDEX ix_product_supplier ON product (supplier_id);
CREATE INDEX ix_product_agency_search_key ON product (agency_id, search_key);

ALTER TABLE product ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON product
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Tarif d'achat d'une prestation, par periode (dates incluses) et par base, dans la devise du
-- contrat (par defaut celle du fournisseur). Pas de tarifs enfants, supplements ni reductions.
--
-- Non-chevauchement : pour une meme prestation et une meme base, deux tarifs non archives n'ont
-- jamais de periodes communes. Controle par l'application sous verrou pessimiste de la ligne
-- product (PESSIMISTIC_WRITE), car une contrainte d'exclusion demande l'extension btree_gist, dont
-- la disponibilite chez l'hebergeur n'est pas confirmee.
-- TODO(etape-12): remplacer ou doubler ce controle par une contrainte
--   EXCLUDE USING gist (product_id WITH =, basis WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
--   WHERE (archived_at IS NULL)
-- si l'extension btree_gist est disponible en production.
CREATE TABLE rate (
    id                 UUID           PRIMARY KEY,
    agency_id          UUID           NOT NULL REFERENCES agency (id),
    product_id         UUID           NOT NULL,
    basis              VARCHAR(20)    NOT NULL CHECK (basis IN ('SINGLE', 'DOUBLE', 'TRIPLE', 'QUADRUPLE',
                           'PER_PERSON', 'PER_UNIT')),
    valid_from         DATE           NOT NULL,
    valid_to           DATE           NOT NULL,
    purchase_amount    NUMERIC(19, 4) NOT NULL CHECK (purchase_amount > 0),
    purchase_currency  VARCHAR(3)     NOT NULL CHECK (purchase_currency ~ '^[A-Z]{3}$'),
    label              VARCHAR(100),
    archived_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT ck_rate_period CHECK (valid_to >= valid_from),
    CONSTRAINT fk_rate_product_same_agency
        FOREIGN KEY (product_id, agency_id) REFERENCES product (id, agency_id)
);
-- Controle de chevauchement et liste des tarifs d'une prestation
CREATE INDEX ix_rate_product_basis_valid_from ON rate (product_id, basis, valid_from);

ALTER TABLE rate ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rate
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Droits : SELECT, INSERT, UPDATE, DELETE pour agencysuite_app par les privileges par defaut de V3.
-- Permission d'ecriture supplier.write : creee en V7 (MANAGER, OPERATIONS) ; lecture ouverte a tous.
