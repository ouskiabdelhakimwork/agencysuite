# Étape 3, lot 1 : validation des jetons Keycloak et périmètre agence

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> Références : ADR-08 (Keycloak, identité seule, un seul adaptateur), ADR-06 (multi-tenant), ADR-36 (accès provisoire au back-office, **supprimé par ce lot**).
> Périmètre : **backend et configuration Keycloak locale uniquement**. Le frontend (connexion OIDC) est le lot 3 : pendant ce lot, l'interface locale affichera le bandeau d'erreur des modules (401), c'est attendu.

## 0. Décisions prises pour l'étape 3 (rappel)

- Premier administrateur d'une agence : **invitation par e-mail** (lien Keycloak à durée limitée, le gérant choisit son mot de passe), avec un bouton « renvoyer l'invitation ». Aucun mot de passe n'est généré, affiché ni envoyé par l'éditeur. Cela sera livré au lot 5, pas dans ce lot.
- Double authentification : obligatoire pour le realm `platform` ; facultative pour les agences en V1, chaque agence pouvant la rendre obligatoire (lot 2 ou 5).
- Une seule adresse de connexion pour toutes les agences en V1 ; l'agence est déduite du compte. Pas de sous-domaine par agence en V1.

## 1. Objectif

1. Toute requête `/api/**` (sauf les exceptions publiques) exige un jeton JWT valide émis par Keycloak.
2. Deux chaînes de sécurité distinctes :
   - `/api/v1/backoffice/**` : jeton du realm **`platform`** uniquement → périmètre **plateforme** ;
   - le reste de `/api/**` : jeton du realm **`agencysuite`** uniquement → périmètre de **l'agence** de l'utilisateur.
3. L'agence vient du jeton (claim d'Organization Keycloak), **jamais** d'une donnée envoyée par le client.
4. Fin de l'accès provisoire au back-office : `BackofficeProperties`, `BackofficeAccessFilter`, `ScopedFilterChain` (si devenu inutile), `BackofficeDisabledException` et la propriété `agencysuite.backoffice.unsecured-access` sont **supprimés**, ainsi que leurs tests et leurs traductions `problem.backoffice-disabled.*`.
5. Les tests n'utilisent plus l'en-tête `X-Test-Agency` : `TestAgencyScopeFilter` est supprimé et les tests passent par de vrais jetons simulés (`spring-security-test`), donc par le vrai chemin de sécurité.

## 2. Étape préalable obligatoire : vérifier le format du claim Organization

Avant d'écrire la résolution de l'agence, **vérifie sur le Keycloak local (26.6)** ce que contient réellement le jeton d'un utilisateur membre d'une Organization quand le client demande le scope `organization` :

1. Mets à jour `deploy/keycloak/realms/agencysuite-realm.json` (section 5), réimporte le realm, crée à la main dans la console une Organization dont l'**alias** est un UUID d'agence de test, et un utilisateur fictif membre de cette Organization.
2. Récupère un jeton avec le client de développement (section 5) et décode-le (sans le coller dans un fichier versionné ni dans les journaux).
3. Note dans le compte rendu la forme exacte du claim `organization` (liste d'alias ? objet avec identifiant ?).

Conception retenue : **l'alias de l'Organization est l'UUID de l'agence** (posé à la création de l'Organization, lot 5). Le backend lit l'UUID dans le claim, sans table de correspondance ni lecture inter-agences.

**Si le claim ne permet pas de lire l'alias** (format différent, scope inexistant, alias non modifiable), **arrête-toi** et décris ce que tu observes : on adaptera la conception ici. N'invente pas de mapper ni d'attribut Keycloak non vérifié.

## 3. Livrables backend

### 3.1 Dépendances

- Starter Spring Boot du « resource server » OAuth2 et le support de test de Spring Security. **Vérifie les noms exacts des artefacts pour Spring Boot 4.1** (les starters ont été réorganisés en Boot 4) avant de les ajouter ; ne devine pas une version.

### 3.2 Nouveau module `iam`

- `ma.agencysuite.iam`, `package-info.java` : `@ApplicationModule(allowedDependencies = {"shared", "platform"})`.
- Tout le code lié aux jetons Keycloak est dans `iam/infrastructure/security/` (règle « un seul adaptateur », ADR-08). Les autres modules ne voient jamais un `Jwt`.

### 3.3 Configuration

`application.yml` (pas de secret : ce sont des adresses publiques) :

```yaml
agencysuite:
  security:
    agency-issuer: ${KEYCLOAK_AGENCY_ISSUER:http://localhost:8180/realms/agencysuite}
    platform-issuer: ${KEYCLOAK_PLATFORM_ISSUER:http://localhost:8180/realms/platform}
    audience: agencysuite-api
```

- `@ConfigurationProperties("agencysuite.security")`, record validé (issuers non vides).
- Ajoute les deux variables à `deploy/.env.example` (avec ces valeurs locales).

### 3.4 Décodeurs de jetons

- Un `JwtDecoder` par realm, construit avec l'URL des clés publiques du realm (`<issuer>/protocol/openid-connect/certs`). Les clés sont **chargées à la première requête**, pas au démarrage : l'application et les tests démarrent sans Keycloak.
- Validateurs sur chaque décodeur : validateurs par défaut (expiration, date de début) + **issuer exact** du realm + **audience** contenant `agencysuite-api`.
- Algorithme de signature attendu : celui des clés du realm (RS256 par défaut). Aucun jeton non signé accepté.

### 3.5 Chaînes de sécurité (`SecurityFilterChain`)

Ordre et règles :

| Ordre | `securityMatcher` | Règle |
|---|---|---|
| 1 | `/api/v1/backoffice/**` | Jeton du realm `platform` (décodeur `platform`) + contrôle explicite du claim `iss` = issuer `platform` ; puis ouverture du **périmètre plateforme** |
| 2 | `/api/**` | Exceptions publiques (ci-dessous) ; tout le reste : jeton du realm `agencysuite` + contrôle explicite `iss` = issuer `agencysuite` ; puis ouverture du **périmètre agence** |
| 3 | tout le reste (`/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`) | Public : `/actuator/health/**`, `/actuator/info`, documentation OpenAPI ; tout autre actuator refusé |

- Exceptions publiques dans la chaîne 2 : `GET /api/v1/platform/info` uniquement.
- Le contrôle explicite du claim `iss` dans la règle d'autorisation (en plus du validateur du décodeur) sert de défense en profondeur et rend la règle testable avec des jetons simulés.
- Sans état (`STATELESS`), CSRF désactivé pour ces API à jeton porteur, pas de formulaire ni de connexion HTTP Basic.
- Erreurs au format problem+json traduit (FR/AR/EN), via un `AuthenticationEntryPoint` et un `AccessDeniedHandler` qui réutilisent le même format que `PlatformProblemHandler` :
  - 401 `urn:agencysuite:problem:unauthenticated` : jeton absent, invalide, expiré, mauvais realm ;
  - 403 `urn:agencysuite:problem:access-denied` : jeton valide mais pas autorisé ici.
  - En 401, l'en-tête standard `WWW-Authenticate: Bearer` est conservé. Aucun détail sur la raison exacte du rejet dans la réponse.

### 3.6 Ouverture du périmètre

`iam/infrastructure/security/TenantScopeFilter` (ajouté **dans** la chaîne, après le filtre d'authentification par jeton porteur) :

- Chaîne `platform` : `TenantContext.runAsPlatform(...)` autour de la suite de la chaîne.
- Chaîne `agencysuite` :
  1. lire le claim `organization` (forme vérifiée à la section 2) ;
  2. exactement **une** Organization attendue (un utilisateur appartient à une seule agence en V1) ; zéro → 403 `agency-scope-required` ; plusieurs → 403 `access-denied` ;
  3. l'alias doit être un UUID valide, sinon 403 `access-denied` ;
  4. ouvrir `TenantContext.runAsAgency(agencyId, ...)`, puis vérifier **dans ce périmètre** que l'agence existe et est opérationnelle (TRIAL ou ACTIVE) via une nouvelle méthode `PlatformApi.isOperational(AgencyId)` (même règle que les autres : elle ne lit que l'agence courante) ;
  5. agence introuvable, suspendue ou fermée → 403 `urn:agencysuite:problem:agency-unavailable` (même réponse dans les deux cas : on ne révèle pas si l'agence existe).
- Le filtre ne journalise ni le jeton ni l'e-mail. Il peut journaliser en DEBUG l'identifiant `sub` (pseudonyme) et l'UUID de l'agence.

### 3.7 Services du back-office

Dette du lot 3 de l'étape 2 : `AgencyAdministration` et `ModuleActivation` vérifient explicitement qu'ils s'exécutent en périmètre plateforme (méthode `TenantContext.requirePlatform()` à ajouter dans `shared.tenant` si elle n'existe pas, sur le modèle de `requireAgency()`). Hors périmètre plateforme → exception interne (500), car c'est une erreur de programmation, pas une erreur de l'utilisateur.

### 3.8 Traductions

`messages{,_ar,_en}.properties` : `problem.unauthenticated.*`, `problem.access-denied.*`, `problem.agency-unavailable.*`. Supprimer `problem.backoffice-disabled.*`. Textes arabes à signaler pour relecture.

## 4. Tests

Les tests existants restent verts après migration vers les jetons simulés (`SecurityMockMvcRequestPostProcessors.jwt()` ou équivalent vérifié dans la version utilisée). Prévois une aide de test unique, par exemple `TestTokens.agency(UUID agencyId)` et `TestTokens.platform()`, qui pose les claims `iss`, `aud`, `sub` et `organization` dans la forme vérifiée à la section 2.

| Classe | Cas |
|---|---|
| `SecurityChainTest` (nouveau) | Sans jeton : `/api/v1/me/modules` → 401 problem+json avec `WWW-Authenticate` ; `/api/v1/backoffice/agencies` → 401 ; `GET /api/v1/platform/info` → 200 ; `/actuator/health` → 200 ; un autre actuator → refusé. Jeton `agencysuite` sur le back-office → 401 ou 403 (jamais 2xx). Jeton `platform` sur `/api/v1/me/modules` → refusé. |
| `TenantScopeFilterTest` (nouveau) | Jeton agence valide → périmètre de cette agence (`/me/modules` ne renvoie que ses modules) ; aucune Organization → 403 `agency-scope-required` ; deux Organizations → 403 ; alias non UUID → 403 ; agence inconnue → 403 `agency-unavailable` ; agence SUSPENDED → 403 `agency-unavailable` (même corps que l'inconnue) |
| `JwtValidationTest` (nouveau, unitaire) | Sur les validateurs des décodeurs, avec des `Jwt` construits en test : bon issuer et audience → valide ; autre issuer → rejet ; audience absente ou différente → rejet ; jeton expiré → rejet |
| Tests existants migrés | `BackofficeAgencyApiTest`, `ModuleActivationApiTest`, `MeModulesApiTest`, `RequiresModuleTest` : jeton `platform` ou `agency(...)` au lieu de l'en-tête de test ou de la propriété ; mêmes assertions |
| Supprimés | `BackofficeDisabledTest` (le comportement n'existe plus) |
| `BackofficeServicesScopeTest` (nouveau) | Appel de `AgencyAdministration` / `ModuleActivation` en périmètre agence ou sans périmètre → exception, aucune écriture |
| `ModularityTests`, `TenantIsolationTest`, `PlatformApiIsolationTest` | Restent verts |

Bonus : la suppression de `BackofficeDisabledTest` retire le second contexte Spring, donc le second conteneur PostgreSQL. Indique dans le compte rendu si la durée de `verify` baisse.

## 5. Keycloak local (`deploy/keycloak/realms/`, développement uniquement)

- Realm `agencysuite` :
  - client `agencysuite-frontend` (public, PKCE, existant) : ajouter le scope `organization` aux scopes par défaut et un mapper d'**audience** qui ajoute `agencysuite-api` ;
  - organisations activées (déjà le cas).
- Realm `platform` : client public `agencysuite-backoffice` (PKCE, même mapper d'audience), double authentification obligatoire inchangée.
- **Client de développement** `agencysuite-dev-cli` dans chaque realm : accès direct par mot de passe (*direct access grants*), même scope et même audience, pour obtenir un jeton avec `curl` lors des contrôles manuels. Ajoute dans le fichier un champ de description « DEV ONLY ». Ce client **n'existera jamais** dans les realms de recette et de production : note-le dans `deploy/README.md`.
- **Aucun utilisateur ni mot de passe dans les fichiers de realm versionnés.** Les utilisateurs fictifs de test sont créés à la main dans la console, en suivant une procédure ajoutée à `deploy/README.md`.
- `deploy/README.md` : procédure pour réimporter les realms (Keycloak n'importe pas un realm qui existe déjà), créer une Organization d'essai dont l'alias est l'UUID d'une agence existante, créer un utilisateur fictif, obtenir un jeton avec `curl.exe` (PowerShell), appeler `/api/v1/me/modules`.

## 6. Hors périmètre

- Frontend (connexion OIDC, envoi du jeton, 401 dans `ModuleAccessService`) : lot 3.
- Utilisateurs, rôles et permissions dans la base de l'application, `@RequiresPermission` : lot 2.
- Journal d'audit : lot 4.
- Création de l'Organization et invitation du gérant depuis le back-office (API d'administration Keycloak) : lot 5.
- Vérification par le backend que l'utilisateur `platform` a bien passé la double authentification (claim `acr`/`amr`) : à étudier au lot 2 ; pour l'instant, Keycloak impose la MFA à la connexion.
- Désactivation de la documentation OpenAPI publique en production : étape 12.

## 7. Vérification

Depuis `backend\` : `.\mvnw.cmd verify` au vert.

Contrôles manuels (Keycloak local lancé, procédure de `deploy/README.md`) :

1. sans jeton, `curl.exe -i http://localhost:8080/api/v1/me/modules` → 401 problem+json ;
2. avec le jeton de l'utilisateur fictif de l'agence d'essai → 200 et les modules de cette agence ;
3. avec ce même jeton sur `/api/v1/backoffice/agencies/<id>` → refusé ;
4. après passage de l'agence d'essai au statut SUSPENDED (par SQL en local) → 403 `agency-unavailable`.

Aucun jeton, mot de passe ou e-mail réel dans le compte rendu : remplace-les par `<jeton>`.

## 8. Compte rendu attendu

Dans `revue/etape3-lot1-compte-rendu.md`, plus le diff :

```
git add -N backend deploy
git diff -- backend deploy > revue/etape3-lot1.diff
```

1. forme exacte du claim `organization` observée (section 2), sans valeur réelle sensible ;
2. résultat du build (tests avant et après, durée de `verify`) ;
3. fichiers créés, modifiés et supprimés ;
4. écarts par rapport au plan et pourquoi ;
5. points à valider (textes arabes, choix techniques, noms d'artefacts retenus) ;
6. résultat des 4 contrôles manuels.

Message de commit proposé (sans ligne d'attribution) :

```
feat(iam): validate Keycloak tokens per realm and open tenant scope from organization claim
```

**Ne commite pas** avant l'accord du développeur.
