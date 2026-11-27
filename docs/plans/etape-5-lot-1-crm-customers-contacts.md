# Étape 5, lot 1 : clients et contacts (backend, module `crm`)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User story : US-401 (créer une fiche client particulier ou entreprise, avec ICE, IF et RC).
> Références :
> - 04-modele-de-donnees.md §4 (`customer`, `contact`) ;
> - décisions de l'étape 5 du 27/11/2026 : demandes dans `crm`, permissions `crm.write` et `supplier.write`, audit ciblé, tout le monde voit tous les clients ;
> - ADR-06 (multi-tenant) et ADR-38 (audit).
>
> Périmètre : **backend uniquement**. Les écrans arrivent au lot 5.
> Les champs viennent du modèle de données. Ils seront ajustés par migration après l'atelier avec l'agence pilote : ne sur-spécifie rien.

## 1. Objectif

1. Nouveau module **`crm`**, qui contient les clients (particuliers et entreprises) et leurs contacts.
2. Recherche, pagination, création, modification avec verrouillage optimiste, archivage et restauration.
3. Normalisation des téléphones (format international) et des e-mails, et **alerte de doublon probable** avant création.
4. Colonnes `created_by` et `updated_by`, et socle réutilisable pour toutes les tables métier des étapes suivantes.
5. Audit de l'archivage et de la restauration, via un **événement générique** qui évite toute dépendance de `iam` vers `crm`.

## 2. Décisions de conception (à respecter)

### 2.1 Module et dépendances

- `ma.agencysuite.crm`, `package-info.java` : `@ApplicationModule(allowedDependencies = {"shared"})`.
- **Aucune** dépendance vers `iam`. `@RequiresPermission` vient de `shared`, et l'utilisateur courant de `UserContext` et `ActorContext`.
- Pas de `@RequiresModule` : le CRM fait partie du socle, il n'est pas activable.
- Mets à jour le tableau des modules dans le chat projet : rien à faire dans le code. Le chat projet s'en charge.

### 2.2 Auteur des lignes (`created_by`, `updated_by`)

- Nouvelle super-classe `shared/persistence/AuthoredAgencyEntity extends AgencyScopedEntity`, avec deux colonnes :
  - `created_by UUID NULL` ;
  - `updated_by UUID NULL`.
- Les deux sont remplies dans `@PrePersist` et `@PreUpdate` à partir de `ActorContext` (identifiant de l'acteur), et valent `NULL` pour `SYSTEM`.
- **Pas de clé étrangère** : l'auteur peut être un `user_account` ou un `platform_user`.
- Les tables existantes ne changent pas. Seules les nouvelles tables métier héritent de cette classe.

### 2.3 Audit depuis un module métier

`AuditAction` est un enum interne à `iam`, et `crm` ne doit pas dépendre de `iam`. On ajoute donc dans `shared/audit/` un événement générique :

```java
public record AuditableEvent(AgencyId agencyId, String action, String entityType,
                             String entityId, Map<String, Object> details) { }
```

- `iam` écoute `AuditableEvent` (écouteur **synchrone, dans la transaction**, comme au lot 4) et convertit `action` en `AuditAction` avec `valueOf`. Un code inconnu est une erreur de programmation : exception, et l'action est annulée.
- Ajoute à `AuditAction` : `CUSTOMER_ARCHIVED` et `CUSTOMER_RESTORED`.
- Règle inchangée : `details` ne contient que des codes techniques. Ici, `{"type":"COMPANY"}`, jamais le nom ni l'e-mail.
- Le frontend (`AUDIT_ACTIONS` et les libellés `audit.action.*`) sera mis à jour au lot 5. D'ici là, le journal affiche « Autre action » pour ces deux codes : c'est accepté.

### 2.4 Permissions

- Migration : nouvelles permissions `crm.write` et `supplier.write`.
- Répartition (hypothèse à valider avec l'agence pilote) :
  - `crm.write` : MANAGER, SALES, OPERATIONS ;
  - `supplier.write` : MANAGER, OPERATIONS ;
  - MANAGER reçoit toujours tout (le test existant le vérifie).
- `PermissionCode` : ajoute `CRM_WRITE` et `SUPPLIER_WRITE`. `supplier.write` est créée dès maintenant pour regrouper les permissions de l'étape dans une seule migration. Elle servira au lot 3.
- **Lecture** des clients ouverte à tout utilisateur de l'agence, sans permission particulière.

### 2.5 Visibilité

Tous les utilisateurs de l'agence voient tous les clients. `owner_user_id` sert à l'affichage et au filtrage, jamais à restreindre l'accès (US-205 plus tard).

## 3. Migration `V6__create_crm_customers.sql`

```
customer   id UUID PK, agency_id NOT NULL REFERENCES agency(id),
           type VARCHAR(20) NOT NULL CHECK (type IN ('INDIVIDUAL','COMPANY')),
           name VARCHAR(200) NOT NULL,                 -- raison sociale, ou prénom et nom
           ice VARCHAR(15) NULL, if_number VARCHAR(20) NULL, rc VARCHAR(50) NULL,
           email VARCHAR(254) NULL,                    -- minuscules
           phone VARCHAR(20) NULL,                     -- E.164, ex. +212612345678
           address_line VARCHAR(300) NULL, city VARCHAR(100) NULL,
           country CHAR(2) NOT NULL DEFAULT 'MA',      -- ISO 3166-1 alpha-2
           preferred_locale VARCHAR(5) NOT NULL DEFAULT 'fr' CHECK (preferred_locale IN ('fr','ar','en')),
           sector VARCHAR(100) NULL,
           source VARCHAR(30) NULL CHECK (source IN ('WEBSITE','WHATSAPP','PHONE','EMAIL','WALK_IN',
                                                     'REFERRAL','SOCIAL_MEDIA','TRADE_SHOW','OTHER')),
           owner_user_id UUID NULL,
           notes VARCHAR(2000) NULL,
           archived_at TIMESTAMPTZ NULL,
           created_at, updated_at, version, created_by, updated_by
           CHECK : (type = 'COMPANY') OR (ice IS NULL AND if_number IS NULL AND rc IS NULL)
INDEX (agency_id, lower(name)) ; INDEX (agency_id, email) ; INDEX (agency_id, phone) ; INDEX (agency_id, ice)
CONSTRAINT fk_customer_owner_same_agency : (owner_user_id, agency_id) → user_account (id, agency_id)

contact    id UUID PK, agency_id NOT NULL,
           customer_id UUID NOT NULL,
           full_name VARCHAR(200) NOT NULL, job_title VARCHAR(100) NULL,
           email VARCHAR(254) NULL, phone VARCHAR(20) NULL,
           is_primary BOOLEAN NOT NULL DEFAULT false,
           created_at, updated_at, version, created_by, updated_by
CONSTRAINT fk_contact_customer_same_agency : (customer_id, agency_id) → customer (id, agency_id)
UNIQUE (id, agency_id) sur customer (pour la clé composée)
Index unique partiel : un seul contact is_primary = true par client
RLS tenant_isolation sur les deux tables ; GRANT habituels ; test de garde vert
```

Plus, dans la même migration ou une migration V7 séparée (à ton choix, en le justifiant) : les permissions `crm.write` et `supplier.write` et leur répartition dans `system_role_permission`.

Écarts assumés avec le modèle de données :
- `address` est découpé en `address_line`, `city` et `country` ;
- `preferred_locale` est ajouté, pour les futurs messages WhatsApp et PDF ;
- `tags` et `opportunity` sont reportés.

Signale tout autre écart.

## 4. Règles métier

| Règle | Détail |
|---|---|
| Type | `INDIVIDUAL` ou `COMPANY`, modifiable. Passer de COMPANY à INDIVIDUAL efface ICE, IF et RC, et c'est refusé (409 `customer-has-contacts`) si le client a des contacts |
| Contacts | Seulement pour un client `COMPANY` (sinon 409 `contacts-company-only`) |
| ICE | Facultatif, exactement 15 chiffres, comme pour l'agence (format à confirmer, RG-19) |
| E-mail | Facultatif, format vérifié, stocké en minuscules et sans espaces |
| Téléphone | Facultatif. Analysé avec **libphonenumber** (Google), région par défaut `MA` (`0612345678` → `+212612345678`), stocké en E.164. Numéro invalide → 400 `validation-failed` sur le champ. Vérifie la dernière version stable de l'artefact `com.googlecode.libphonenumber:libphonenumber` avant de l'ajouter |
| Pays | Code ISO à 2 lettres en majuscules, vérifié contre la liste de `java.util.Locale.getISOCountries()` |
| Responsable | `ownerUserId` facultatif ; doit être un utilisateur de l'agence (sinon 400, garanti aussi par la clé composée) |
| Doublon probable | À la **création**, s'il existe dans l'agence un client non archivé avec le **même e-mail**, le **même téléphone** ou le **même ICE** → 409 `possible-duplicate`, avec la liste des candidats (`id`, `name`, critère commun : `EMAIL`, `PHONE` ou `ICE`). Le client renvoie la même requête avec `"confirmDuplicate": true` pour créer quand même. Même contrôle à la modification si l'un de ces champs change |
| Archivage | `archived_at` renseigné ; le client disparaît des listes par défaut ; il reste lisible par son `id` ; restauration possible. **Pas de suppression définitive** (droits des personnes : étape 13b). Audit `CUSTOMER_ARCHIVED` et `CUSTOMER_RESTORED` |
| Verrouillage optimiste | La modification envoie `version` ; version périmée → 409 `stale-version`, détail traduit « La fiche a été modifiée par quelqu'un d'autre ; rechargez-la ». Gestionnaire dédié à `ObjectOptimisticLockingFailureException` (exception précise, pas un gestionnaire générique), dans `shared` |
| Contact principal | Mettre un contact en `isPrimary` retire ce statut à l'ancien, dans la même transaction |

## 5. API

Lecture : tout utilisateur de l'agence. Écriture : `@RequiresPermission(CRM_WRITE)`.

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/customers` | Paramètres : `q`, `type`, `ownerUserId`, `archived` (défaut `false`), `page`, `size` (défaut 25, max 100), `sort` = `name` (défaut), `-createdAt` ou `-updatedAt`. `q` cherche, sans tenir compte de la casse, dans le nom, l'e-mail, le téléphone et l'ICE. Le téléphone est cherché aussi sous sa forme normalisée. Réponse paginée : `items` (`id`, `type`, `name`, `email`, `phone`, `city`, `ownerUserId`, `archived`) et les informations de page |
| GET | `/api/v1/customers/{id}` | Fiche complète, avec `contacts`, `version`, `createdAt`, `updatedAt` |
| POST | `/api/v1/customers` | 201 + `Location` ; 409 `possible-duplicate` (section 4) |
| PUT | `/api/v1/customers/{id}` | Remplace les champs modifiables ; `version` obligatoire |
| POST | `/api/v1/customers/{id}/archive` | 200 ; idempotent |
| POST | `/api/v1/customers/{id}/restore` | 200 ; idempotent |
| POST | `/api/v1/customers/{id}/contacts` | 201 |
| PUT | `/api/v1/customers/{id}/contacts/{contactId}` | Avec `version` |
| DELETE | `/api/v1/customers/{id}/contacts/{contactId}` | 204 (suppression réelle : un contact n'est pas un client facturé) |

- Un `id` d'une autre agence → **404**, jamais 403. Un contact qui n'appartient pas au client de l'URL → 404.
- La recherche `q` est un simple `lower(...) LIKE` : pas d'insensibilité aux accents ni de recherche approchée dans ce lot (ADR-22 et recherche globale à l'étape 13b). Ajoute un `TODO(etape-13b)`.
- Erreurs problem+json traduites FR/AR/EN : `possible-duplicate`, `stale-version`, `contacts-company-only`, `customer-has-contacts`, `not-found` (réutilisé).
- OpenAPI : les nouveaux endpoints apparaissent dans `/v3/api-docs`.

## 6. Tests

| Classe | Cas |
|---|---|
| `TenantIsolationTest` (étendu) | `customer` et `contact` ont leur politique RLS ; clés composées : un contact ou un responsable d'une autre agence est rejeté par la base |
| `CustomerApiTest` | Création particulier et entreprise ; ICE refusé pour un particulier ; téléphone `0612345678` → `+212612345678` ; téléphone invalide → 400 ; e-mail en majuscules → minuscules ; pagination, tri, filtres `type`, `ownerUserId`, `archived` ; `q` sur nom, e-mail, téléphone (forme locale et internationale), ICE ; ACCOUNTANT lit (200) mais ne crée pas (403 `permission-denied`) |
| `CustomerDuplicateTest` | Même e-mail, téléphone ou ICE → 409 avec les candidats et le critère ; `confirmDuplicate: true` → 201 ; un client archivé n'est pas un candidat ; un client d'une **autre agence** avec le même e-mail n'est **jamais** proposé |
| `CustomerConcurrencyTest` | Deux modifications avec la même `version` → la seconde reçoit 409 `stale-version` |
| `CustomerArchiveTest` | Archivage et restauration idempotents ; archivé absent de la liste par défaut, présent avec `archived=true` ; audit `CUSTOMER_ARCHIVED` / `CUSTOMER_RESTORED` avec acteur, `details` sans nom ni e-mail ; archiver deux fois → une seule entrée |
| `ContactApiTest` | Contacts réservés aux entreprises ; un seul principal ; passage COMPANY → INDIVIDUAL refusé si des contacts existent ; contact d'un autre client ou d'une autre agence → 404 |
| `AuthoredEntityTest` | `created_by` et `updated_by` = identifiant de l'utilisateur courant ; `updated_by` change à la modification, pas `created_by` |
| `AuditableEventTest` | Un `AuditableEvent` avec un code inconnu annule la transaction de l'action |
| `ReferenceDataTest` | Toujours vert avec les deux nouvelles permissions ; MANAGER les a ; SALES a `crm.write` mais pas `supplier.write` |
| `ModularityTests` | `crm` ne dépend que de `shared` ; `iam` ne dépend pas de `crm` |

Tests ciblés pendant le développement, un seul `verify` complet à la fin.

## 7. Contrôles manuels (par le développeur, avec le jeton de `claim-check` dans la console du navigateur, comme au lot 5)

1. Créer une entreprise fictive (« Atlas Pharma Test », ICE à 15 chiffres, téléphone `0522000000`) → 201 ; le téléphone revient en `+212522000000`.
2. Recréer un client avec le même téléphone → 409 avec le candidat ; avec `confirmDuplicate: true` → 201.
3. Ajouter deux contacts, dont un principal ; changer de principal.
4. Rechercher `atlas`, puis `0522000000`.
5. Archiver, vérifier qu'il disparaît de la liste, le restaurer ; dans le journal (`/api/v1/audit-events`), deux entrées (affichées « Autre action » jusqu'au lot 5).
6. En arabe (`Accept-Language: ar`), une erreur `possible-duplicate` a son titre et son détail en arabe.

Prépare les extraits de code pour la console dans le compte rendu, comme au lot 5 de l'étape 3.

## 8. Hors périmètre

Voyageurs (lot 2), fournisseurs (lot 3), demandes (lot 4), écrans (lots 5 et 6), import Excel (étape 7), fusion de doublons et historique du client (P2), opportunités et étiquettes, recherche approchée, droits des personnes et suppression définitive (13b).

## 9. Compte rendu attendu

`revue/etape5-lot1-compte-rendu.md` et le diff :

```
git add -N backend
git diff -- backend > revue/etape5-lot1.diff
```

Contenu habituel : build et tests (avant et après), fichiers, écarts et pourquoi, points à valider (liste des sources, répartition des permissions, champs), extraits de code pour la console.

Message de commit proposé (sans ligne d'attribution) :

```
feat(crm): customers and contacts with duplicate detection, archiving and authored entities (US-401)
```

**Ne commite pas** avant l'accord du développeur.
