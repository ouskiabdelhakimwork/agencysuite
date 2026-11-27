-- Etape 5 : permissions d'ecriture des clients (crm.write) et des fournisseurs (supplier.write).
--
-- Migration separee de V6 : ce sont des donnees de reference transverses (supplier.write sert au
-- lot 3, module supplier), regroupees pour toute l'etape ; V6 reste propre au schema du module crm.
--
-- La lecture des clients est ouverte a tout utilisateur de l'agence, sans permission.
-- Repartition (hypothese a valider avec l'agence pilote) :
--   crm.write      : MANAGER, SALES, OPERATIONS
--   supplier.write : MANAGER, OPERATIONS
-- MANAGER recoit toujours toutes les permissions (verifie par ReferenceDataTest).

INSERT INTO permission (code, module_code) VALUES
    ('crm.write',      NULL),
    ('supplier.write', NULL);

INSERT INTO system_role_permission (role_code, permission_code) VALUES
    ('MANAGER',    'crm.write'),
    ('MANAGER',    'supplier.write'),
    ('SALES',      'crm.write'),
    ('OPERATIONS', 'crm.write'),
    ('OPERATIONS', 'supplier.write');
