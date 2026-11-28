# Étape 5, lot 2 : voyageurs (backend, module `crm`)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> Références :
> - 04-modele-de-donnees.md §4 (`traveler`) ;
> - 01-cahier-des-charges.md §6.5 (OCR, données extraites) ;
> - 09-regles-metier-validees.md RG-14 (date de naissance en clair, lisible avec permission : **à valider**) ;
> - décisions du 28/11/2026 : un voyageur peut être rattaché à **plusieurs clients** ; un client particulier n'est **pas** automatiquement son propre voyageur ;
> - lot 1 (`AuthoredAgencyEntity`, `AuditableEvent`, `shared/contact`, `stale-version`, `possible-duplicate`).
>
> Périmètre : **backend uniquement**. Les écrans arrivent au lot 5.
> **Aucune pièce d'identité à ce lot** : ni numéro de passeport, ni numéro de CIN, ni fichier. Ils arrivent avec l'OCR (étape 4) et la table `identity_document` chiffrée.
> Les champs viennent du modèle de données ; ils seront ajustés par migration après l'atelier avec l'agence pilote.

## 1. Objectif

1. Fiche voyageur dans le module `crm` : identité en lettres latines et arabes, date de naissance, nationalité, coordonnées, régime alimentaire, personne à prévenir.
2. **Date de naissance protégée** : lisible et modifiable seulement avec la permission `identity.read`.
3. Rattachement d'un voyageur à **zéro, un ou plusieurs clients** de l'agence.
4. Recherche (y compris en arabe et sans tenir compte des accents), pagination, archivage, restauration, **alerte de doublon probable**.
5. Audit de l'archivage et de la restauration, sans aucune donnée personnelle dans `details`.

## 2. Décisions de conception (à respecter)

### 2.1 Module

- Même module `crm`, mêmes couches (`domain`, `application`, `infrastructure`, `api`). Aucune nouvelle dépendance : `crm` dépend toujours de `shared` seulement.
- `Traveler` hérite de `AuthoredAgencyEntity`.
- Réutilise `PhoneNumbers`, `EmailAddresses`, `@ValidPhoneNumber`, `@ValidCountryCode`, `AuditableEvent` et les gestionnaires d'erreurs existants. Le gestionnaire `possible-duplicate` du lot 1 est réutilisé : ne crée pas un second code.

### 2.2 Lien voyageur ↔ clients

- Table de liaison `customer_traveler` (clé primaire `(customer_id, traveler_id)`), avec clés étrangères **composées avec `agency_id`** vers `customer` et `traveler` : un lien ne peut jamais traverser deux agences.
- Le lien porte seulement l'agence, les deux identifiants et `created_at` / `created_by`. Pas de « relation » (salarié, famille…) à ce lot : on attend l'atelier.
- Un voyageur peut n'avoir **aucun** client (prospect, inscription MICE à venir).
- Rattacher un voyageur à un client **archivé** est refusé (409 `customer-archived`). Les liens existants restent quand on archive un client.
- Supprimer un lien est une suppression réelle (ce n'est pas une donnée de facturation).

### 2.3 Date de naissance et permission `identity.read`

- Il faut pouvoir tester **par programme** si l'utilisateur courant a une permission. Si `shared` le permet déjà (mécanisme utilisé par `@RequiresPermission`), réutilise-le. Sinon, ajoute dans `shared/security` une méthode de lecture minimale alimentée par la même source, et signale-le dans le compte rendu. N'invente pas de nouvelle source de permissions.
- **Lecture** sans `identity.read` : `birthDate` vaut `null` et la réponse contient `"birthDateHidden": true` quand une date existe. Avec la permission : la date et `"birthDateHidden": false`.
- **Écriture** :
  - sans `identity.read`, un `birthDate` non nul dans la requête → 403 `permission-denied` ; un `birthDate` absent ou nul **ne modifie pas** la date existante ;
  - avec `identity.read`, `PUT` remplace la date (nul l'efface).
- **Recherche** : la date de naissance n'est jamais un critère de recherche, ni un champ de la liste.
- **Doublons** : le critère « même nom et même date de naissance » n'est calculé que si l'appelant a envoyé une date (donc qu'il a la permission).
- Pas d'âge calculé exposé à ce lot (les tarifs enfants arriveront avec les devis).
- Pas d'audit de la lecture de la date de naissance (trop fréquent). La lecture des pièces d'identité sera auditée à l'étape 4.

### 2.4 Noms et recherche

- Champs latins `last_name`, `first_name` (obligatoires) ; champs arabes `last_name_ar`, `first_name_ar` (facultatifs).
- Colonne technique **`name_key`** calculée par le domaine à chaque écriture : « nom prénom » en minuscules, sans accents (`java.text.Normalizer`, forme NFD, puis retrait des marques diacritiques), espaces réduits. Exemple : `Él Fassi  Zineb` → `el fassi zineb`. Elle sert à la recherche et aux doublons ; elle n'est jamais renvoyée par l'API.
- `q` cherche avec `LIKE` (caractères `%` et `_` échappés, comme au lot 1) dans `name_key` (après la même normalisation de `q`), `last_name_ar`, `first_name_ar`, `email`, `phone`, et en égalité sur la forme E.164 de `q` si c'est un numéro valide.
- Pas de recherche approchée ni de translittération latin ↔ arabe : étape 13b / recherche globale (ADR-22).

### 2.5 Permissions

- Lecture des voyageurs : tout utilisateur de l'agence.
- Création, modification, archivage, restauration, liens : `crm.write`.
- Date de naissance : `identity.read` en plus (section 2.3). Aucune nouvelle permission, aucune migration de permissions.

## 3. Migration `V8__create_crm_travelers.sql`

```
traveler
  id UUID PK
  agency_id UUID NOT NULL REFERENCES agency(id)
  last_name VARCHAR(100) NOT NULL
  first_name VARCHAR(100) NOT NULL
  last_name_ar VARCHAR(100) NULL
  first_name_ar VARCHAR(100) NULL
  name_key VARCHAR(210) NOT NULL
  gender VARCHAR(20) NULL CHECK (gender IN ('FEMALE','MALE','UNSPECIFIED'))
  birth_date DATE NULL
  birth_place VARCHAR(100) NULL
  nationality VARCHAR(2) NULL CHECK (nationality ~ '^[A-Z]{2}$')
  email VARCHAR(254) NULL          -- minuscules
  phone VARCHAR(20) NULL           -- E.164
  dietary VARCHAR(200) NULL        -- texte libre : végétarien, sans gluten…
  emergency_contact_name VARCHAR(200) NULL
  emergency_contact_phone VARCHAR(20) NULL   -- E.164
  notes VARCHAR(2000) NULL
  archived_at TIMESTAMPTZ NULL
  anonymized_at TIMESTAMPTZ NULL   -- réservé à l'étape 13b, jamais rempli à ce lot
  created_at, updated_at, version, created_by, updated_by
  CONSTRAINT uk_traveler_id_agency UNIQUE (id, agency_id)
  CHECK (birth_date IS NULL OR birth_date > DATE '1900-01-01')

customer_traveler
  agency_id UUID NOT NULL REFERENCES agency(id)
  customer_id UUID NOT NULL
  traveler_id UUID NOT NULL
  created_at TIMESTAMPTZ NOT NULL
  created_by UUID NULL
  PRIMARY KEY (customer_id, traveler_id)
  CONSTRAINT fk_customer_traveler_customer_same_agency FOREIGN KEY (customer_id, agency_id) REFERENCES customer (id, agency_id)
  CONSTRAINT fk_customer_traveler_traveler_same_agency FOREIGN KEY (traveler_id, agency_id) REFERENCES traveler (id, agency_id)
```

- Index : `(agency_id, name_key)`, `(agency_id, email)`, `(agency_id, phone)`, `customer_traveler (traveler_id)`.
- RLS et politique `tenant_isolation` sur les **deux** tables, comme V6.
- `nationality` en ISO 3166-1 alpha-2 (cohérent avec `customer.country`). La MRZ des passeports utilise des codes alpha-3 de l'OACI : la conversion sera faite par l'OCR (étape 4). Note-le en tête de migration.
- `customer_traveler` n'est pas une entité versionnée : une entité JPA simple à clé composée (ou `@IdClass`), avec `agency_id` rempli comme ailleurs (`@TenantId`). Si l'héritage de `AgencyScopedEntity` ne convient pas à une clé composée, explique la solution retenue dans le compte rendu ; l'isolation doit rester garantie par `@TenantId` **et** la RLS.

## 4. Règles métier

| Règle | Détail |
|---|---|
| Normalisation | Espaces retirés ; e-mail en minuscules ; téléphones (voyageur et personne à prévenir) en E.164, région par défaut MA ; `name_key` recalculé à chaque écriture |
| Date de naissance | Pas dans le futur (400 `validation-failed`) ; postérieure au 01/01/1900 ; règles de lecture et d'écriture de la section 2.3 |
| Doublon probable | À la **création**, s'il existe dans l'agence un voyageur non archivé avec : le même `name_key` **et** la même date de naissance, ou le même e-mail, ou le même téléphone → 409 `possible-duplicate`, candidats `id`, `lastName`, `firstName`, critères `NAME_AND_BIRTH_DATE`, `EMAIL`, `PHONE`. `confirmDuplicate: true` pour créer quand même. Même contrôle à la modification si l'un de ces champs change. Le nom seul n'est **pas** un critère (trop d'homonymes) |
| Rattachement | Client inconnu ou d'une autre agence → 404 `not-found` ; client archivé → 409 `customer-archived` ; rattacher deux fois → idempotent (200, pas de doublon) ; détacher un lien absent → idempotent (204) |
| Archivage | `archived_at` ; absent des listes par défaut ; lisible par son `id` ; restauration ; idempotent ; audit `TRAVELER_ARCHIVED` et `TRAVELER_RESTORED`, `details` vide (`{}`) : ni nom, ni date, ni nationalité |
| Modification d'un voyageur archivé | Autorisée (même règle que les clients au lot 1) |
| Verrouillage optimiste | `version` obligatoire au `PUT` → 409 `stale-version` |

Ajoute `TRAVELER_ARCHIVED` et `TRAVELER_RESTORED` à `AuditAction`. Les libellés du frontend suivront au lot 5.

## 5. API

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/travelers` | `q`, `customerId`, `archived` (défaut `false`), `page`, `size` (25, max 100), `sort` = `name` (défaut, sur `name_key` puis `id`), `-createdAt`, `-updatedAt`. Liste : `id`, `lastName`, `firstName`, `lastNameAr`, `firstNameAr`, `nationality`, `email`, `phone`, `archived`. **Pas de date de naissance dans la liste** |
| GET | `/api/v1/travelers/{id}` | Fiche complète + `birthDateHidden` + `customers` (`id`, `name`, `type`, `archived`) |
| POST | `/api/v1/travelers` | `crm.write` ; corps avec `customerIds` facultatif (rattachement à la création, mêmes règles qu'en 4) ; 201 + `Location` ; 409 `possible-duplicate` |
| PUT | `/api/v1/travelers/{id}` | `crm.write` ; `version` ; remplace les champs (sauf la date sans `identity.read`, section 2.3) ; ne touche pas aux liens |
| POST | `/api/v1/travelers/{id}/archive` | `crm.write` ; 200 ; idempotent |
| POST | `/api/v1/travelers/{id}/restore` | `crm.write` ; 200 ; idempotent |
| PUT | `/api/v1/travelers/{id}/customers/{customerId}` | `crm.write` ; rattache ; idempotent ; 200 avec la fiche |
| DELETE | `/api/v1/travelers/{id}/customers/{customerId}` | `crm.write` ; détache ; 204 |

- `GET /api/v1/customers/{id}` du lot 1 **ne change pas** ; la liste des voyageurs d'un client s'obtient par `GET /api/v1/travelers?customerId=…`.
- Nouveau code d'erreur traduit FR/AR/EN : `customer-archived`. Réutilisés : `possible-duplicate`, `stale-version`, `not-found`, `permission-denied`, `validation-failed`.
- OpenAPI : groupe `travelers`, avec la règle de la date de naissance décrite sur les deux opérations d'écriture et sur la fiche.

## 6. Tests

| Test | Ce qu'il vérifie |
|---|---|
| `TravelerApiTest` | Création minimale (nom, prénom) ; noms arabes ; normalisation e-mail et téléphones ; nationalité ISO invalide → 400 ; date future → 400 ; pagination, tri, `archived`, `customerId` ; `q` : nom avec et sans accent, nom arabe, e-mail, téléphone (forme locale et E.164), `%` pris à la lettre ; `name_key` absent des réponses ; ACCOUNTANT lit mais ne crée pas (403) |
| `TravelerBirthDateTest` | Avec `identity.read` : lecture et écriture ; sans : `birthDate` null + `birthDateHidden: true`, envoi d'une date → 403, `PUT` sans date ne l'efface pas ; la liste ne contient jamais la date ; le critère de doublon `NAME_AND_BIRTH_DATE` |
| `TravelerDuplicateTest` | Même nom + même date → 409 ; même nom seul → 201 ; même e-mail ou téléphone → 409 ; `confirmDuplicate` → 201 ; voyageur archivé non candidat ; voyageur d'une **autre agence** jamais proposé ; modification sans changement des critères → pas de contrôle |
| `TravelerCustomerLinkTest` | Rattachement à plusieurs clients ; idempotence ; client archivé → 409 ; client inconnu ou d'une autre agence → 404 ; détachement ; `customerIds` à la création ; la fiche liste les clients ; archiver un client garde le lien |
| `TravelerArchiveTest` | Archivage et restauration idempotents ; audit avec acteur et `details` vide ; une seule entrée par changement d'état |
| `TravelerConcurrencyTest` | Version périmée → 409 `stale-version` |
| `TenantIsolationTest` (étendu) | Politiques RLS de `traveler` et `customer_traveler` ; un lien vers un client ou un voyageur d'une autre agence est refusé par la base |
| `ModularityTests` | `crm` dépend toujours de `shared` seulement |
| Normalisation | Test unitaire de `name_key` : accents, majuscules, espaces multiples, apostrophes (`Ait M'Barek`) |

Le rôle qui a `identity.read` est celui défini en V4 : lis `ReferenceDataTest` et utilise la répartition existante, ne la change pas.

## 7. Contrôles manuels (par le développeur, console du navigateur, fonction `api` du lot 1)

1. Créer un voyageur `{ lastName: 'El Fassi', firstName: 'Zineb', lastNameAr: 'الفاسي', firstNameAr: 'زينب', birthDate: '1990-05-14', nationality: 'MA', phone: '0612345678' }` avec `claim-check` (MANAGER) → 201, téléphone en `+212612345678`.
2. Le rattacher à « Atlas Pharma Test » puis à « Atlas Bis Test » → la fiche liste les deux clients ; `GET /api/v1/travelers?customerId=<atlas>` le trouve.
3. Rechercher `fassi`, `EL FASSI`, `él fassi` (avec accent) et `الفاسي` → le voyageur est trouvé à chaque fois.
4. Recréer `El Fassi Zineb` avec la même date → 409 `possible-duplicate`, critère `NAME_AND_BIRTH_DATE`.
5. Avec un utilisateur sans `identity.read` (rôle modifié dans psql, comme à l'étape 3) : la fiche montre `birthDate: null` et `birthDateHidden: true` ; envoyer une date → 403.
6. Archiver puis restaurer : deux entrées dans `/api/v1/audit-events` avec `details` vide.

## 8. Hors périmètre

Pièces d'identité et OCR (étape 4), mention d'information et consentement CNDP (US-405), droits des personnes et anonymisation (US-406, étape 13b), relation voyageur ↔ client, listes de groupes et rooming lists, âge et tarifs enfants, recherche approchée et translittération, import Excel (étape 7), écrans (lot 5).

## 9. Compte rendu attendu

Comme d'habitude, dans `revue/etape5-lot2-compte-rendu.md` et `revue/etape5-lot2.diff` (`git add -N backend` puis `git diff -- backend`) :
- build et tests (avant / après) ;
- fichiers créés et modifiés ;
- écarts au plan et pourquoi, en particulier la solution pour tester `identity.read` par programme et l'entité de liaison ;
- points à valider ;
- contrôles manuels de la section 7 avec les extraits de code pour la console ;
- contrôle des secrets et des données (aucun vrai nom, uniquement des données fictives) ;
- message de commit proposé, sans ligne d'attribution :

```
feat(crm): travelers with protected birth date, multi-customer links and duplicate detection
```
