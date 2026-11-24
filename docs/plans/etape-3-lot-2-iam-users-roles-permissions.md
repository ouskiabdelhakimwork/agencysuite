# Étape 3, lot 2 : utilisateurs, rôles et permissions (module `iam`)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User stories : socle de US-201 (rôle attribué à un utilisateur) et préparation de US-202 (permissions par rôle). L'invitation elle-même (création du compte Keycloak) est le lot 5 ; l'adaptation des permissions par l'agence (US-202, P2) viendra plus tard.
> Références : ADR-08 (droits dans l'application, pas dans Keycloak), ADR-06 (multi-tenant), cahier des charges section 3 (rôles).

## 1. Objectif

1. Un jeton valide ne suffit plus : l'utilisateur doit **exister et être actif dans notre base**.
   - Realm `agencysuite` : une ligne `user_account` ACTIVE dans l'agence du jeton, retrouvée par le `sub` du jeton.
   - Realm `platform` : une ligne `platform_user` ACTIVE, retrouvée par le `sub`.
   - Sinon : 403 `access-denied` (même réponse pour « inconnu » et « désactivé »). C'est le comportement « fermé par défaut ».
2. Chaque requête d'agence connaît l'utilisateur courant et ses **permissions** (union des permissions de ses rôles).
3. Les contrôleurs peuvent exiger une permission avec `@RequiresPermission(PermissionCode.X)` (403 `permission-denied` sinon), sur le modèle de `@RequiresModule`.
4. Endpoints : `GET /api/v1/me` (agence) et `GET /api/v1/backoffice/me` (éditeur).

## 2. Décisions de modèle (écart avec 04-modele-de-donnees.md, validé dans le chat projet)

1. **Rôles système partagés, non copiés par agence.** Les 4 rôles par défaut et leurs permissions sont des **données de référence** (sans `agency_id`), comme `plan` et `module`. On ne copie rien à la création d'une agence : on évite d'écrire des lignes d'agence depuis le périmètre plateforme (avec `@TenantId`, Hibernate y mettrait l'identifiant fictif de la plateforme). Ajouter une permission à un rôle = une migration, valable pour toutes les agences. L'adaptation par agence (US-202) se fera plus tard par une table de surcharges `agency_role_permission`, hors de ce lot.
2. **Utilisateurs de l'éditeur dans une table séparée** `platform_user`, au lieu de `user_account` avec `agency_id` nul. `@TenantId` n'accepte pas de valeur nulle, et la RLS reste simple.
3. **Pas de périmètre succursale ou équipe** (US-205, P3) : pas de colonne `scope` dans ce lot.

## 3. Migration `V4__create_iam_tables.sql`

### 3.1 Tables de référence (sans `agency_id`, sans RLS, lecture seule pour `agencysuite_app`)

```
permission              code VARCHAR(50) PK, module_code VARCHAR(30) NULL REFERENCES module(code)
system_role             code VARCHAR(30) PK
system_role_permission  role_code REFERENCES system_role, permission_code REFERENCES permission,
                        PRIMARY KEY (role_code, permission_code)
```

- `module_code` : la permission n'a de sens que si ce module est souscrit (ex. `ticketing.write` → TICKETING). Information seulement, le contrôle du module reste `@RequiresModule`.
- La V3 donne des droits par défaut sur les futures tables : **révoque** explicitement INSERT, UPDATE, DELETE sur ces 3 tables pour `agencysuite_app` et garde SELECT.

Données initiales. Seules les permissions déjà nommées dans les documents du projet sont créées. Chaque étape suivante ajoutera les siennes par migration, avec leur répartition dans les rôles.

| Permission | Module | MANAGER | SALES | OPERATIONS | ACCOUNTANT |
|---|---|---|---|---|---|
| `user.manage` (utilisateurs et rôles) | — | ✓ | | | |
| `audit.read` (journal d'audit) | — | ✓ | | | |
| `privacy.manage` (droits des personnes) | — | ✓ | | | |
| `identity.read` (pièces d'identité) | — | ✓ | ✓ | ✓ | |
| `ticketing.write` (billetterie) | TICKETING | ✓ | ✓ | ✓ | |
| `billing.issue` (émettre une facture) | — | ✓ | | | ✓ |

Rôles : `MANAGER` (gérant, administrateur de l'agence), `SALES` (commercial), `OPERATIONS` (opérations), `ACCOUNTANT` (comptable). Les noms affichés sont dans les traductions (`role.<CODE>.name`), pas en base.

Règle : `MANAGER` reçoit **toutes** les permissions. Un test le vérifie, pour qu'une migration future ne l'oublie pas.

### 3.2 Tables d'agence (RLS obligatoire, politique `tenant_isolation` habituelle)

```
user_account  id UUID PK, agency_id NOT NULL REFERENCES agency(id),
              external_idp_id VARCHAR(36) NOT NULL,           -- sub Keycloak
              email VARCHAR(254) NOT NULL, full_name VARCHAR(200),
              status VARCHAR(20) NOT NULL CHECK (status IN ('INVITED','ACTIVE','DISABLED')),
              last_login_at TIMESTAMPTZ, created_at, updated_at, version
              CONSTRAINT uk_user_account_external_idp_id UNIQUE (external_idp_id)
              index unique (agency_id, lower(email))

user_role     id UUID PK, agency_id NOT NULL REFERENCES agency(id),
              user_id UUID NOT NULL REFERENCES user_account(id),
              role_code VARCHAR(30) NOT NULL REFERENCES system_role(code),
              created_at, updated_at, version
              CONSTRAINT uk_user_role UNIQUE (user_id, role_code)
```

- Entités `UserAccount` et `UserRole` héritant de `AgencyScopedEntity`. `user_role` a un identifiant propre : une collection d'éléments Hibernate n'écrirait pas `agency_id`.
- `sub` unique sur toute la plateforme : un utilisateur appartient à une seule agence en V1.
- Statut INVITED : prévu pour le lot 5 ; dans ce lot, **seul ACTIVE donne accès**.

### 3.3 Table de l'éditeur

```
platform_user  id UUID PK, external_idp_id VARCHAR(36) NOT NULL UNIQUE,
               email VARCHAR(254) NOT NULL, full_name VARCHAR(200),
               role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN')),
               status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','DISABLED')),
               last_login_at TIMESTAMPTZ, created_at, updated_at, version
```

- RLS activée avec la politique `platform_only` : `USING (app_platform_access()) WITH CHECK (app_platform_access())`. Une requête d'agence ne voit jamais l'équipe éditeur.
- Entité `PlatformUser` héritant de `BaseEntity` (pas de `@TenantId`).
- Un seul rôle `ADMIN` en V1 : tous les droits du back-office. Un rôle « support en lecture seule » ira au backlog.

## 4. Code

### 4.1 `shared` (types partagés, aucun accès aux données)

- `shared/security/PermissionCode` : enum dont chaque valeur porte son code (`USER_MANAGE("user.manage")`…), aligné sur la table `permission`.
- `shared/security/RequiresPermission` : annotation (méthode ou classe, la méthode l'emporte), sur le modèle de `RequiresModule`.
- `shared/security/CurrentUser` : record (`UUID userId`, `Set<PermissionCode> permissions`) ; `shared/security/UserContext` : `ScopedValue`, avec `current()` et `require()`, sur le modèle de `TenantContext`. Aucun e-mail ni nom dedans.

### 4.2 `iam`

- `iam/domain` : `UserAccount`, `UserRole`, `PlatformUser`, enums de statut.
- `iam/infrastructure` : repositories ; lecture de l'utilisateur **et** de ses permissions en une requête (jointure `user_role` → `system_role_permission`).
- `iam/application/CurrentUserResolver` :
  - `Optional<CurrentUser> resolveAgencyUser(String sub)` : appelé **dans le périmètre de l'agence** ; ne trouve donc jamais un utilisateur d'une autre agence ;
  - `boolean isActivePlatformUser(String sub)` : appelé dans le périmètre plateforme.
- `TenantScopeFilter` (lot 1), modifié :
  - agence : après la vérification `isOperational`, résoudre l'utilisateur ; absent ou non ACTIVE → 403 `access-denied` ; sinon ouvrir `UserContext` autour de la suite de la chaîne ;
  - plateforme : après l'ouverture du périmètre, vérifier `platform_user` ACTIVE ; sinon 403 `access-denied`.
  - Pas d'écriture en base à chaque requête : `last_login_at` sera mis à jour au lot 4 (événement de connexion), pas ici.
- `iam/infrastructure/web/RequiresPermissionInterceptor` : enregistré **après** l'intercepteur `@RequiresModule` (un module non souscrit répond `module-not-enabled` avant toute question de permission). Sans `UserContext` → 403 `access-denied` ; permission absente → 403 `urn:agencysuite:problem:permission-denied` avec la propriété `permission` (code).
- `iam/api/MeController` : `GET /api/v1/me` →
  ```json
  { "id": "...", "email": "...", "fullName": "...",
    "agency": { "id": "...", "name": "..." },
    "roles": ["MANAGER"], "permissions": ["audit.read", "..."] }
  ```
  Le nom de l'agence vient d'une nouvelle méthode `PlatformApi.agencyName(AgencyId)` (`Optional<String>`, même règle de périmètre que les autres méthodes).
- `iam/api/BackofficeMeController` : `GET /api/v1/backoffice/me` → `{ id, email, fullName, role }`.
- Gestionnaire d'erreurs de `iam` pour `permission-denied` (même format que `PlatformProblemHandler`).
- `package-info.java` de `iam` : `allowedDependencies` inchangé (`shared`, `platform`).

### 4.3 Traductions

- Serveur (`messages*.properties`) : `problem.permission-denied.*`.
- Interface (`public/i18n/*.json`) : `role.MANAGER.name`, `role.SALES.name`, `role.OPERATIONS.name`, `role.ACCOUNTANT.name` (FR : Gérant, Commercial, Opérations, Comptable ; AR et EN à proposer, AR à faire relire). Le test de parité doit rester vert.

## 5. Tests

| Classe | Cas |
|---|---|
| `TenantIsolationTest` (étendu) | `user_account`, `user_role` et `platform_user` ont une politique RLS ; en périmètre agence, `platform_user` est vide |
| `ReferenceDataTest` (nouveau) | `PermissionCode` correspond exactement à la table `permission` ; `MANAGER` a toutes les permissions ; `agencysuite_app` ne peut pas écrire dans `permission`, `system_role`, `system_role_permission` |
| `CurrentUserResolverTest` (nouveau, intégration) | Utilisateur ACTIVE → permissions = union de ses rôles ; INVITED ou DISABLED → vide ; **utilisateur de l'agence B introuvable depuis le périmètre de A, même avec son `sub`** |
| `TenantScopeFilterTest` (étendu) | Jeton d'agence avec `sub` inconnu → 403 `access-denied` ; `sub` d'un utilisateur DISABLED → 403 ; `sub` d'un utilisateur d'une autre agence que l'Organization du jeton → 403 ; jeton `platform` sans `platform_user` → 403 ; `platform_user` DISABLED → 403 |
| `RequiresPermissionTest` (nouveau) | Contrôleur de test (dans `src/test`) annoté `@RequiresPermission(AUDIT_READ)` : MANAGER → 200 ; SALES → 403 `permission-denied` avec `permission` ; module non souscrit + permission absente → `module-not-enabled` en premier |
| `MeApiTest` (nouveau) | `/api/v1/me` : données de l'utilisateur et de son agence uniquement, rôles et permissions corrects ; `/api/v1/backoffice/me` avec un jeton `platform` |
| Tests existants | Adaptés : `TestTokens` pose un `sub` ; `PlatformTestData` (ou une nouvelle aide `IamTestData`) crée l'utilisateur ACTIVE correspondant (MANAGER par défaut) et le `platform_user` des jetons éditeur |
| `ModularityTests` | Reste vert |

## 6. Amorçage en local (`deploy/README.md`)

Ajoute une procédure, sans aucun identifiant réel dans le fichier :

1. obtenir le `sub` d'un utilisateur Keycloak (console Keycloak > *Users* > identifiant de l'utilisateur) ;
2. insérer la ligne `user_account` (ACTIVE) et son rôle MANAGER par `psql` avec le compte propriétaire des tables ;
3. même chose pour le premier `platform_user` (ADMIN) du realm `platform`.

Indique que cette insertion manuelle est **provisoire** : le lot 5 créera les comptes par invitation.

## 7. Hors périmètre

- Invitation, création du compte Keycloak, statut INVITED → ACTIVE, renvoi d'invitation : lot 5.
- Écrans Angular (connexion, `/me`, affichage selon permissions) : lot 3.
- Journal d'audit et `last_login_at` : lot 4.
- Double authentification exigée par une agence : lot à part, après vérification de ce que Keycloak met dans le jeton (`acr`/`amr`). Ne pas ajouter de colonne `mfa_required` maintenant.
- Surcharges de permissions par agence (US-202) et périmètre équipe/succursale (US-205).
- Cache des permissions : dette notée (trois lectures par requête : agence, utilisateur avec ses permissions, module).

## 8. Vérification

- `.\mvnw.cmd verify` au vert depuis `backend\`, et `npx ng test --watch=false` depuis `frontend\` (traductions des rôles).
- Contrôles manuels (Keycloak local, procédure de la section 6) :
  1. utilisateur Keycloak membre de l'Organization mais **sans** `user_account` → `GET /api/v1/me` répond 403 ;
  2. après insertion (ACTIVE, MANAGER) → 200 avec les 6 permissions ;
  3. passé en DISABLED par SQL → 403 ;
  4. `GET /api/v1/backoffice/me` avec un jeton `platform` et un `platform_user` ACTIVE → 200. Si le jeton `platform` ne peut pas être obtenu (double authentification, paramètre `totp` non vérifié), note-le simplement : ce contrôle sera fait au lot 3.

## 9. Compte rendu attendu

`revue/etape3-lot2-compte-rendu.md` et le diff :

```
git add -N backend frontend deploy
git diff -- backend frontend deploy > revue/etape3-lot2.diff
```

Contenu habituel : build et nombre de tests (avant/après), fichiers, écarts et pourquoi, points à valider (répartition des permissions par rôle, textes arabes), contrôles manuels.

Message de commit proposé (sans ligne d'attribution) :

```
feat(iam): users, system roles and permissions with @RequiresPermission and /me endpoints (US-201)
```

**Ne commite pas** avant l'accord du développeur.
