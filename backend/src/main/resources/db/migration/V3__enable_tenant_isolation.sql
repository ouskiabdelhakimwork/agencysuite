-- Etape 2, lot 2 : isolation des agences dans PostgreSQL (Row Level Security, ADR-06).
--
-- Principe : l'application ne lit et n'ecrit jamais avec le compte proprietaire des tables.
-- A chaque connexion, elle prend le role agencysuite_app (SET ROLE) et indique l'agence
-- courante dans la variable de session app.current_agency. Les politiques ci-dessous ne
-- laissent alors passer que les lignes de cette agence, meme si une requete oublie son filtre.
-- Le back-office editeur positionne app.platform_access = 'on' pour voir toutes les agences.
--
-- Regle pour toute nouvelle table portant agency_id : ENABLE ROW LEVEL SECURITY + politique
-- tenant_isolation, dans la meme migration (verifie par TenantIsolationTest).

-- 1. Role applicatif : sans connexion directe (NOLOGIN), donc sans mot de passe a gerer,
--    sans super-utilisateur ni contournement des politiques.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'agencysuite_app') THEN
        CREATE ROLE agencysuite_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

-- Le compte de connexion de l'application doit pouvoir prendre ce role.
GRANT agencysuite_app TO CURRENT_USER;

-- 2. Droits du role applicatif
GRANT USAGE ON SCHEMA public TO agencysuite_app;
GRANT SELECT ON plan, module TO agencysuite_app;                                   -- references : lecture seule
GRANT SELECT, INSERT, UPDATE, DELETE ON agency, subscription, agency_module TO agencysuite_app;
-- Tables creees plus tard par les migrations : memes droits par defaut
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO agencysuite_app;

-- 3. Fonctions lues par les politiques (variables de session positionnees par l'application)
CREATE FUNCTION app_current_agency() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.current_agency', true), '')::uuid $$;

CREATE FUNCTION app_platform_access() RETURNS boolean
    LANGUAGE sql STABLE
AS $$ SELECT COALESCE(current_setting('app.platform_access', true), '') = 'on' $$;

-- 4. Politiques : une agence ne voit et n'ecrit que ses lignes ; la plateforme voit tout.
--    Sans agence positionnee, app_current_agency() vaut NULL : aucune ligne ne passe.
ALTER TABLE agency ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON agency
    USING (app_platform_access() OR id = app_current_agency())
    WITH CHECK (app_platform_access() OR id = app_current_agency());

ALTER TABLE subscription ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON subscription
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());

ALTER TABLE agency_module ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON agency_module
    USING (app_platform_access() OR agency_id = app_current_agency())
    WITH CHECK (app_platform_access() OR agency_id = app_current_agency());
