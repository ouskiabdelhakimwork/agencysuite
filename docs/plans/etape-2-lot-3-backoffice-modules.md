# Plan — Étape 2, lot 3 : back-office agences et activation des modules

Statut : à implémenter. Prérequis : lots 1 (schéma plateforme) et 2 (multi-tenant + RLS) terminés et verts.

## 1. Objectif

Permettre à l'éditeur de créer une agence cliente avec son palier et ses modules (US-101), d'activer ou désactiver un module pour une agence (US-102), et bloquer côté serveur tout endpoint d'un module non souscrit (`@RequiresModule`, 403).

Critères d'acceptation repris des user stories :
- Un module désactivé renvoie 403 sur ses API, même en appel direct.
- La réactivation rend les données du module à nouveau visibles, sans perte (on ne supprime jamais la ligne `agency_module`, on la désactive).
- Chaque activation ou désactivation est tracée. **Le journal d'audit n'existe qu'à l'étape 3** : dans ce lot, publier un événement applicatif (voir § 4) que l'étape 3 consommera.

## 2. Périmètre

Inclus : services applicatifs, API REST back-office, API publique du module `platform`, intercepteur `@RequiresModule`, endpoint « mes modules actifs », messages d'erreur traduits, tests.

Exclus : authentification (étape 3), table d'audit (étape 3), écrans Angular (lot 5 et étape 3), facturation des abonnements (hors outil, ADR-28).

## 3. Sécurité provisoire du back-office (important)

Tant que Keycloak n'est pas branché (étape 3), les endpoints back-office n'ont pas d'authentification. Ils doivent donc être **désactivés par défaut** :

- Propriété `agencysuite.backoffice.unsecured-access` (booléen, **défaut `false`**), dans une classe `@ConfigurationProperties`.
- `false` : tout appel sous `/api/v1/backoffice/**` renvoie **403** (problem+json, type `backoffice-disabled`, voir § 5).
- `true` : un filtre ouvre `TenantContext.runAsPlatform(...)` pour ces requêtes et journalise au démarrage un **WARN** explicite (« back-office sans authentification : usage local uniquement »).
- Activée uniquement par la configuration de test et, si besoin, en local par variable d'environnement. **Jamais** dans `application.yml` principal.
- Marquer le code `// TODO(etape-3)` : remplacer par la vérification du jeton du realm `platform`.

## 4. Conception

### 4.1 API publique du module `platform`

`ma.agencysuite.platform.PlatformApi` (interface à la racine du paquet, seule chose que les autres modules peuvent utiliser) :

```java
public interface PlatformApi {
    boolean isModuleEnabled(AgencyId agencyId, ModuleCode module);
    Set<ModuleCode> enabledModules(AgencyId agencyId);
}
```
Implémentation dans `platform/application`. Les lectures se font dans le périmètre de l'agence concernée ou en plateforme ; attention à la RLS (une lecture sans périmètre ne voit rien).

### 4.2 Annotation et garde des modules

- `ma.agencysuite.shared.module.RequiresModule` : annotation (`@Target({TYPE, METHOD})`, `@Retention(RUNTIME)`), attribut `ModuleCode value()`. Dans `shared` car tous les modules l'utilisent.
- Garde : `HandlerInterceptor` dans `platform/infrastructure/web` (enregistré via un `WebMvcConfigurer`), pas d'AOP.
  - Annotation lue sur la méthode, sinon sur la classe du contrôleur.
  - Périmètre agence requis (`TenantContext.requireAgency()`) ; sinon 403.
  - Module non activé pour l'agence → exception `ModuleNotEnabledException` → 403 problem+json.
- Pas de cache dans ce lot (à envisager plus tard, avec invalidation à l'activation/désactivation).

### 4.3 Services applicatifs (`platform/application`)

- `AgencyAdministration` :
  - `createAgency(CreateAgencyCommand)` : crée l'agence (`Agency.createCustomer`), son identité légale, l'abonnement (`Subscription.start`) et active les modules demandés. Vérifie : sous-domaine libre (sinon `SubdomainAlreadyTakenException` → 409), palier existant (sinon 404), modules existants. Une seule transaction.
  - `getAgency(UUID)` : agence + abonnement actif + modules actifs.
- `ModuleActivation` :
  - `enable(UUID agencyId, ModuleCode)` : crée la ligne ou la réactive (`AgencyModule.reenable`). Idempotent.
  - `disable(UUID agencyId, ModuleCode)` : désactive sans supprimer. Idempotent. Erreur 404 si l'agence n'existe pas.
  - Publie `ModuleEnabled(agencyId, module, at)` / `ModuleDisabled(...)` (records dans `platform`, via `ApplicationEventPublisher`), seulement quand l'état change.
- Ces services s'exécutent dans le périmètre plateforme (ouvert par le filtre du § 3).

### 4.4 API REST

Back-office (périmètre plateforme, § 3) — préfixe `/api/v1/backoffice` (ne pas utiliser `/api/v1/platform`, déjà pris par l'endpoint public `/api/v1/platform/info`) :

| Méthode | Chemin | Corps / réponse | Codes |
|---|---|---|---|
| POST | `/api/v1/backoffice/agencies` | `CreateAgencyRequest` → `AgencyResponse` + en-tête `Location` | 201, 400, 404 (palier/module inconnu), 409 (sous-domaine pris) |
| GET | `/api/v1/backoffice/agencies/{agencyId}` | `AgencyResponse` | 200, 404 |
| PUT | `/api/v1/backoffice/agencies/{agencyId}/modules/{moduleCode}` | — | 204, 404 |
| DELETE | `/api/v1/backoffice/agencies/{agencyId}/modules/{moduleCode}` | — | 204, 404 |

`CreateAgencyRequest` (record, Bean Validation) : `name` (obligatoire, ≤ 200), `subdomain` (obligatoire ; la règle détaillée reste dans `Agency`), `legalName`, `ice` (optionnel, 15 chiffres — format à confirmer avec l'expert-comptable), `ifNumber`, `rc`, `patente`, `address` (≤ 500), `planCode` (obligatoire), `period` (`MONTHLY`/`YEARLY`), `startDate` (obligatoire), `modules` (liste de `ModuleCode`, peut être vide).

`AgencyResponse` : `id`, `name`, `subdomain`, `status`, `kind`, `legalName`, `ice`, `planCode`, `period`, `startDate`, `enabledModules`.

Agence (périmètre agence) :

| Méthode | Chemin | Réponse |
|---|---|---|
| GET | `/api/v1/me/modules` | `{ "modules": ["TRAVEL", …] }` — modules actifs de l'agence courante (servira au garde Angular, lot 5) |

Cet endpoint n'est atteignable qu'avec un périmètre agence, donc en pratique seulement dans les tests jusqu'à l'étape 3.

## 5. Gestion des erreurs

`@RestControllerAdvice` dans `platform/api` (ou un gestionnaire commun dans `shared` si plus naturel, sans dépendre de `platform`) qui produit des `ProblemDetail` :

| Exception | Statut | `type` (URI) |
|---|---|---|
| `SubdomainAlreadyTakenException` | 409 | `urn:agencysuite:problem:subdomain-taken` |
| `AgencyNotFoundException`, plan ou module inconnu | 404 | `urn:agencysuite:problem:not-found` |
| `ModuleNotEnabledException` | 403 | `urn:agencysuite:problem:module-not-enabled` (+ propriété `module`) |
| Back-office désactivé | 403 | `urn:agencysuite:problem:backoffice-disabled` |
| `IllegalArgumentException` des règles du domaine (sous-domaine invalide…) | 400 | `urn:agencysuite:problem:invalid-request` |
| Erreurs Bean Validation | 400 | standard Spring (déjà au format problem+json) |

`title` et `detail` traduits via `MessageSource` (clés `problem.<code>.title` / `.detail` dans `messages.properties`, `messages_ar.properties`, `messages_en.properties`). Ne jamais renvoyer de trace ni de message SQL.

## 6. Tests (tous verts avant de rendre le lot)

Configuration de test : activer `agencysuite.backoffice.unsecured-access=true` dans `src/test/resources/config/application.yml`.

1. `BackofficeAgencyApiTest` (MockMvc, Testcontainers) :
   - création complète → 201, `Location`, réponse avec palier et modules ;
   - GET de l'agence créée → 200 ;
   - sous-domaine déjà pris → 409 problem+json, type `subdomain-taken` ;
   - sous-domaine invalide ou réservé (`www`) → 400 ;
   - palier inconnu → 404 ;
   - champ obligatoire manquant → 400 ;
   - message d'erreur en arabe avec `Accept-Language: ar`.
2. `ModuleActivationApiTest` : PUT puis DELETE d'un module → 204, état reflété par le GET ; PUT/DELETE répétés idempotents ; agence inconnue → 404 ; événements `ModuleEnabled`/`ModuleDisabled` publiés une seule fois par changement d'état (`@RecordApplicationEvents` ou écouteur de test).
3. `RequiresModuleTest` : un contrôleur **de test** (dans `src/test`) annoté `@RequiresModule(ModuleCode.TRAVEL)` ; un filtre **de test uniquement** qui ouvre le périmètre agence à partir d'un en-tête `X-Test-Agency` (jamais en production) :
   - module non activé → 403 type `module-not-enabled` ;
   - module activé → 200 ;
   - désactivé puis réactivé → 200 et la donnée créée avant la désactivation est toujours là ;
   - sans périmètre agence → 403.
4. `MeModulesApiTest` : `/api/v1/me/modules` renvoie uniquement les modules actifs de l'agence courante, jamais ceux d'une autre agence.
5. `BackofficeDisabledTest` : avec `agencysuite.backoffice.unsecured-access=false` (surcharge de propriété dans ce test), tout appel back-office → 403 `backoffice-disabled`.
6. `ModularityTests` et `TenantIsolationTest` restent verts.

Les tests qui écrivent des données utilisent des sous-domaines uniques (suffixe aléatoire) et nettoient en périmètre plateforme.

## 7. Livrables et rendu attendu

- Code : `shared/module/RequiresModule.java`, `platform/PlatformApi.java`, `platform/application/*`, `platform/api/*` (contrôleurs, DTO, gestion d'erreurs), `platform/infrastructure/web/*` (intercepteur, filtre back-office, configuration), messages i18n FR/AR/EN, tests.
- Aucune nouvelle migration n'est prévue. Si une s'avère nécessaire, s'arrêter et l'expliquer.
- `.\mvnw.cmd verify` vert ; Swagger (`/swagger-ui.html`) montre les nouveaux endpoints.
- Message de commit proposé : `feat(platform): back-office agency creation and module activation with @RequiresModule (US-101, US-102)` — **ne pas commiter sans accord**.
- Rendu : liste des fichiers, nombre de tests, écarts au plan, points à valider (textes arabes, format ICE).
