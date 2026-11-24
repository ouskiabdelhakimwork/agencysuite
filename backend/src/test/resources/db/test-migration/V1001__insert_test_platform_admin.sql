-- Tests uniquement : membre ADMIN actif de l'editeur, dont le sub est porte par les jetons
-- "platform" simules (TestTokens.PLATFORM_ADMIN_SUB). Donnees fictives.
INSERT INTO platform_user (id, external_idp_id, email, full_name, role, status, created_at, updated_at, version)
VALUES ('7e57ad41-0000-7000-8000-000000000001', '7e57ad41-0000-4000-8000-000000000001',
        'admin@example.test', 'Admin Test', 'ADMIN', 'ACTIVE', now(), now(), 0);
