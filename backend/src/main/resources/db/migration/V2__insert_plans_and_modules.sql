-- Donnees de reference : paliers du socle et catalogue des modules (01-cahier-des-charges.md, section 10).
-- Les prix restent NULL tant qu'ils ne sont pas definis (etude de marche a faire).

INSERT INTO plan (code, max_users, storage_quota_gb) VALUES
    ('STARTER',  3,    5),
    ('PRO',      10,   50),
    ('BUSINESS', NULL, 500);

INSERT INTO module (code, phase) VALUES
    ('TRAVEL',    1),
    ('TICKETING', 1),
    ('GIFTBOX',   1),
    ('TRANSPORT', 2),
    ('VISA',      2),
    ('MICE',      2),
    ('PORTAL',    2),
    ('OMRA_HAJJ', 3),
    ('INBOUND',   3),
    ('FLEET',     3);
