-- Etape 5, lot 4 : demandes clients (module crm), US-701.
--
-- Champs repris du modele de donnees (04-modele-de-donnees.md, section 6, table "request").
-- Ecart assume : table nommee customer_request (request est trop generique cote Java et SQL) ;
-- l'API garde le mot simple (/api/v1/requests). Le modele de donnees sera mis a jour au bilan.
--
-- Un client existant est obligatoire (cle composee : la demande reste dans l'agence de son client).
-- Le responsable est un utilisateur de la meme agence (cle composee, comme customer.owner_user_id) ;
-- il sert a l'affichage et au filtrage, jamais a restreindre l'acces.
-- source : meme liste et meme CHECK que customer.source (enum CustomerSource).
-- Statuts : NEW, QUOTING, QUOTE_SENT, WON, LOST ; transitions controlees par l'application.
-- Une demande perdue a toujours une raison, une autre jamais (ck_request_lost).
-- budget : NUMERIC(19,4) + devise ISO 4217 (ADR-15), jamais arrondi a la saisie.
-- search_key : SearchKeys.words de la destination, jamais renvoyee par l'API.
-- created_by / updated_by : auteur de la ligne, sans cle etrangere (AuthoredAgencyEntity).
-- Pas de suppression : une demande abandonnee passe en LOST (PROJECT_CANCELLED).

CREATE TABLE customer_request (
    id                 UUID           PRIMARY KEY,
    agency_id          UUID           NOT NULL REFERENCES agency (id),
    customer_id        UUID           NOT NULL,
    owner_user_id      UUID           NOT NULL,
    channel            VARCHAR(20)    NOT NULL CHECK (channel IN ('FORM', 'EMAIL', 'WHATSAPP', 'PHONE', 'WALK_IN')),
    source             VARCHAR(30)    CHECK (source IN ('WEBSITE', 'WHATSAPP', 'PHONE', 'EMAIL', 'WALK_IN',
                                                        'REFERRAL', 'SOCIAL_MEDIA', 'TRADE_SHOW', 'OTHER')),
    status             VARCHAR(20)    NOT NULL DEFAULT 'NEW'
                                      CHECK (status IN ('NEW', 'QUOTING', 'QUOTE_SENT', 'WON', 'LOST')),
    status_changed_at  TIMESTAMPTZ    NOT NULL,
    lost_reason        VARCHAR(30)    CHECK (lost_reason IN ('PRICE', 'COMPETITOR', 'DATES_UNAVAILABLE',
                                                             'PROJECT_CANCELLED', 'NO_RESPONSE', 'OTHER')),
    lost_comment       VARCHAR(500),
    destination        VARCHAR(200),
    departure_date     DATE,
    return_date        DATE,
    dates_note         VARCHAR(200),
    adults             SMALLINT       NOT NULL DEFAULT 1 CHECK (adults BETWEEN 0 AND 999),
    children           SMALLINT       NOT NULL DEFAULT 0 CHECK (children BETWEEN 0 AND 999),
    budget_amount      NUMERIC(19, 4) CHECK (budget_amount > 0),
    budget_currency    VARCHAR(3)     CHECK (budget_currency ~ '^[A-Z]{3}$'),
    description        VARCHAR(4000),
    search_key         VARCHAR(500)   NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT ck_request_people CHECK (adults + children >= 1),
    CONSTRAINT ck_request_dates
        CHECK (return_date IS NULL OR departure_date IS NULL OR return_date >= departure_date),
    CONSTRAINT ck_request_budget CHECK ((budget_amount IS NULL) = (budget_currency IS NULL)),
    CONSTRAINT ck_request_lost CHECK ((status = 'LOST') = (lost_reason IS NOT NULL)),
    CONSTRAINT fk_request_customer_same_agency
        FOREIGN KEY (customer_id, agency_id) REFERENCES customer (id, agency_id),
    CONSTRAINT fk_request_owner_same_agency
        FOREIGN KEY (owner_user_id, agency_id) REFERENCES user_account (id, agency_id)
);
-- Liste par statut et date, "Mes demandes", demandes d'un client, recherche par destination
CREATE INDEX ix_request_agency_status_created ON customer_request (agency_id, status, created_at DESC);
CREATE INDEX ix_request_agency_owner_status ON customer_request (agency_id, owner_user_id, status);
CREATE INDEX ix_request_customer ON customer_request (customer_id);
CREATE INDEX ix_request_agency_search_key ON customer_request (agency_id, search_key);

ALTER TABLE customer_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_request
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

-- Droits : SELECT, INSERT, UPDATE, DELETE pour agencysuite_app par les privileges par defaut de V3.
-- Ecriture reservee a crm.write (V7) ; lecture ouverte a tous les utilisateurs de l'agence.
