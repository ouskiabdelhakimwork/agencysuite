# Étape 3, lot 4 : journal d'audit

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User stories : critère d'acceptation de US-102 (« l'action est inscrite dans le journal d'audit »), préparation de US-201 (lot 5). Permission `audit.read` (lot 2).
> Références : 04-modele-de-donnees.md (`audit_log`), 06-conventions-code.md (« audit après le commit », « logs sans données personnelles »).

## 1. Objectif

1. Chaque action sensible laisse une trace **non modifiable** : qui (utilisateur d'agence, membre de l'éditeur ou système), quoi, sur quoi, quand, depuis quelle adresse IP.
2. La trace est écrite **dans la même transaction** que l'action : soit les deux sont enregistrées, soit aucune. On ne trace jamais une action annulée, et on n'oublie jamais une action réussie.
3. Un gérant (permission `audit.read`) peut lire le journal de **son** agence par l'API, avec pagination.
4. La connexion d'un utilisateur est tracée, et `last_login_at` est mis à jour.

Événements tracés dans ce lot :

| Action | Origine | Acteur |
|---|---|---|
| `AGENCY_CREATED` | création d'agence (back-office) | membre de l'éditeur |
| `MODULE_ENABLED` / `MODULE_DISABLED` | événements existants `ModuleEnabled` / `ModuleDisabled` | membre de l'éditeur |
| `USER_LOGGED_IN` | `POST /api/v1/me/login-events` (section 4.4) | utilisateur d'agence |

Le lot 5 (invitations) ajoutera `USER_INVITED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `USER_DISABLED`.

## 2. Décisions de conception (à respecter)

1. **Écriture synchrone, dans la transaction de l'action.** L'écouteur est un `@TransactionalEventListener(phase = BEFORE_COMMIT)`, ou un `@EventListener` dans la transaction ; vérifie lequel convient dans la version utilisée. Il tourne dans le même fil d'exécution que l'action : il voit donc le périmètre, l'acteur et la requête HTTP. Ce choix **remplace**, pour l'audit, la règle « écouteurs après le commit » de 06-conventions-code.md, qui reste valable pour la recherche et les traitements secondaires. Je mettrai la convention à jour au bilan de l'étape.
2. **`audit_log` n'hérite pas de `AgencyScopedEntity`.** C'est une exception justifiée à la règle multi-tenant : l'agence concernée est **explicite** dans l'entrée. Une action du back-office (périmètre plateforme) doit écrire une entrée pour l'agence ciblée, et avec `@TenantId`, Hibernate y mettrait l'identifiant fictif de la plateforme. La RLS reste la barrière :
   - en périmètre plateforme, l'écriture est autorisée pour n'importe quelle agence ;
   - en périmètre agence, seulement pour l'agence courante (`WITH CHECK`) ;
   - à la lecture, une agence ne voit que ses entrées. La requête de lecture filtre **en plus** explicitement sur l'agence courante (double barrière, puisque Hibernate ne filtre pas cette entité).
   - Ajoute ce cas au test de garde RLS. Documente l'exception dans la Javadoc de l'entité.
3. **Ajout seul.** Le rôle `agencysuite_app` n'a que `SELECT` et `INSERT` sur `audit_log` (`REVOKE UPDATE, DELETE`). L'entité est `@Immutable`. La purge selon la durée de conservation (à valider, section 8) se fera par une tâche dédiée plus tard, pas par l'application courante.
4. **Acteur.** Nouveau type `shared/security/Actor` (record : `ActorType type` parmi `AGENCY_USER`, `PLATFORM_USER`, `SYSTEM` ; `UUID id`, nul pour `SYSTEM`), dans un `ScopedValue` `ActorContext`, ouvert par `TenantScopeFilter` pour les **deux** chaînes. Côté agence, c'est l'identifiant de `user_account` ; côté plateforme, celui de `platform_user` (`isActivePlatformUser` doit donc renvoyer l'identifiant, ou une nouvelle méthode le fait). Sans `ActorContext` (tâche planifiée, migration), l'acteur est `SYSTEM`.
5. **Pas de données personnelles dans `details`.** C'est du JSONB avec des codes techniques seulement, par exemple `{"module":"TRAVEL"}` ou `{"plan":"PRO","modules":["TRAVEL"]}`. Jamais d'e-mail, de nom ni de numéro de document : l'identité de l'acteur passe par son identifiant. Un test le vérifie sur les entrées produites.
6. **Adresse IP.** C'est l'adresse distante de la requête, `null` hors requête HTTP. Derrière le proxy de production, l'adresse réelle demandera la configuration des en-têtes transférés : ajoute un `TODO(etape-12)`, sans le configurer maintenant. L'adresse IP est une donnée personnelle : elle est dans la base, protégée par `audit.read`, et **jamais** dans les journaux applicatifs.

## 3. Migration `V5__create_audit_log.sql`

```
audit_log  id UUID PK,
           agency_id UUID NOT NULL REFERENCES agency(id),
           actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('AGENCY_USER','PLATFORM_USER','SYSTEM')),
           actor_id UUID NULL,
           action VARCHAR(50) NOT NULL,
           entity_type VARCHAR(50) NOT NULL,
           entity_id VARCHAR(100) NULL,
           details JSONB NOT NULL DEFAULT '{}',
           ip VARCHAR(45) NULL,
           occurred_at TIMESTAMPTZ NOT NULL
INDEX ix_audit_log_agency_time (agency_id, occurred_at DESC)
RLS : politique tenant_isolation habituelle
REVOKE UPDATE, DELETE ON audit_log FROM agencysuite_app ; GRANT SELECT, INSERT
CHECK : (actor_type = 'SYSTEM') = (actor_id IS NULL)
```

Pas de `version` ni d'`updated_at` : une entrée ne change jamais. `actor_id` n'a pas de clé étrangère, car il peut viser `user_account` ou `platform_user`.

Contrainte reportée du lot 2 (décidée en revue) : profite de cette migration pour ajouter l'unicité `(id, agency_id)` sur `user_account` et la clé étrangère composée `user_role (user_id, agency_id) → user_account (id, agency_id)`. Un rôle ne peut alors être rattaché qu'à un utilisateur de la même agence. Ajoute un test : une insertion incohérente est rejetée.

## 4. Code

### 4.1 `platform`

- Nouvel événement public `AgencyCreated(AgencyId agencyId, String planCode, Set<ModuleCode> modules)` à la racine du paquet, publié par `AgencyAdministration.createAgency`. Les `ModuleEnabled` publiés à la création restent publiés et sont donc tracés aussi.
- Vérifie que `ModuleEnabled` et `ModuleDisabled` portent l'identifiant de l'agence et le code du module. Sinon, complète-les.

### 4.2 `shared/security`

- `Actor`, `ActorType`, `ActorContext` (sur le modèle de `UserContext`).

### 4.3 `iam` (l'audit fait partie de `iam`, cf. ADR, section 2)

- `iam/domain/AuditEntry` (entité `@Immutable`, `agencyId` explicite, voir la décision 2), `AuditAction` (enum).
- `iam/infrastructure/AuditEntryRepository` : écriture ; lecture paginée filtrée explicitement par `agencyId`.
- `iam/application/AuditTrail` : `record(AgencyId, AuditAction, String entityType, String entityId, Map<String, Object> details)`. Il lit l'acteur dans `ActorContext` et l'IP dans la requête courante si elle existe. **Doit être appelé dans une transaction en cours** (`Propagation.MANDATORY`) : un appel hors transaction est une erreur de programmation.
- `iam/application/AuditListeners` : écouteurs synchrones (décision 1) de `AgencyCreated`, `ModuleEnabled` et `ModuleDisabled`.
- `TenantScopeFilter` : ouvre `ActorContext` pour les deux chaînes (décision 4).
- `ModularityTests` doit rester vert : `iam` dépend déjà de `platform`.

### 4.4 Connexion

- `POST /api/v1/me/login-events` (agence, sans corps), dans `MeController` ou un contrôleur voisin, répond 204. Dans une transaction :
  - `last_login_at = maintenant` sur l'utilisateur courant ;
  - entrée `USER_LOGGED_IN` (`entity_type = USER`, `entity_id` = identifiant de l'utilisateur).
- Limite les abus : une deuxième entrée `USER_LOGGED_IN` pour le même utilisateur à moins de 60 secondes est ignorée (204, sans entrée). Ce n'est pas une sécurité, juste une protection contre les rechargements en boucle.
- Frontend : `AuthService` appelle ce point **une seule fois**, juste après un retour de connexion réussi (quand `tryLogin()` vient d'échanger un code), pas à chaque rechargement de page. Le résultat est ignoré (`void`), et un échec n'empêche rien. Ajoute le test Vitest correspondant.
- Les échecs de connexion (mauvais mot de passe) sont vus par Keycloak, pas par nous : active les événements de connexion dans le realm `agencysuite` (section 6).

### 4.5 Lecture

- `GET /api/v1/audit-events?page=0&size=50` (taille maximale 200), `@RequiresPermission(AUDIT_READ)`, tri par `occurred_at` décroissant. Réponse : `items` (`occurredAt`, `actorType`, `actorId`, `action`, `entityType`, `entityId`, `details`) et les informations de pagination.
- **L'IP n'est pas renvoyée** dans ce lot. Elle sert aux enquêtes de sécurité de l'éditeur, pas à l'écran de l'agence. Sa consultation se décidera avec le juriste.
- Pas d'écran Angular dans ce lot.

## 5. Tests

| Classe | Cas |
|---|---|
| `AuditTrailTest` | Création d'agence → `AGENCY_CREATED` + un `MODULE_ENABLED` par module, acteur `PLATFORM_USER` avec le bon identifiant ; désactivation → `MODULE_DISABLED` ; activation idempotente (déjà actif) → aucune nouvelle entrée |
| `AuditAtomicityTest` | Action annulée (exception après la publication de l'événement, transaction annulée) → **aucune** entrée ; échec d'écriture de l'audit → l'action est annulée aussi |
| `AuditIsolationTest` | L'agence A ne lit pas les entrées de B (API et SQL direct) ; en périmètre agence, insérer une entrée pour B est rejeté par la RLS ; `agencysuite_app` ne peut ni modifier ni supprimer une entrée |
| `AuditApiTest` | `GET /api/v1/audit-events` : MANAGER → 200, entrées de son agence, ordre décroissant, pagination, pas de champ `ip` ; SALES → 403 `permission-denied` |
| `LoginEventApiTest` | `POST /api/v1/me/login-events` → 204, `last_login_at` mis à jour, une entrée ; second appel immédiat → pas de deuxième entrée |
| `AuditPrivacyTest` | Pour toutes les entrées produites par les tests ci-dessus, `details` ne contient ni `@` ni le nom de l'utilisateur de test |
| `TenantIsolationTest` (étendu) | `audit_log` a sa politique RLS (test de garde) ; contrainte composée `user_role` → `user_account` |
| Frontend `auth.service.spec.ts` (étendu) | Après un retour de connexion → un seul `POST /api/v1/me/login-events` ; rechargement avec un jeton déjà présent → aucun appel |

Pendant le développement, lance les tests ciblés (`-Dtest=...`) ; un seul `verify` complet à la fin.

## 6. Keycloak local

Active les événements de connexion du realm `agencysuite` **sans réimport** (avec `kcadm`, comme au lot 3), et reporte les mêmes réglages dans `agencysuite-realm.json` :
- `eventsEnabled: true` ;
- types : au minimum `LOGIN`, `LOGIN_ERROR`, `LOGOUT` ;
- `eventsExpiration` : 7 776 000 secondes (90 jours), **à valider avec le juriste** ;
- événements d'administration activés, sans le détail des représentations, pour ne pas stocker de données personnelles dans les événements.

Documente dans `deploy/README.md` où les consulter (console > realm > *Events*).

## 7. Hors périmètre

- Écran Angular du journal : avec les écrans de gestion des utilisateurs (lot 5 ou étape suivante).
- Journal des actions sur les données métier (devis, factures, pièces d'identité) : chaque étape ajoutera ses actions. La lecture des pièces d'identité devra être tracée (06-conventions-code.md) à l'étape 8.
- Purge selon la durée de conservation, et export du journal.
- Journal des actions de l'éditeur sans agence (ex. gestion de `platform_user`) : table séparée si besoin, plus tard.
- Déconnexion après inactivité côté interface : backlog, avant la production.

## 8. Points à faire valider (à reporter dans le compte rendu)

- Durée de conservation du journal d'audit et des événements Keycloak (90 jours proposés pour Keycloak ; rien pour `audit_log`) : juriste (CNDP) et expert-comptable (traçabilité).
- Conservation et consultation de l'adresse IP.

## 9. Compte rendu attendu

`revue/etape3-lot4-compte-rendu.md` et le diff :

```
git add -N backend frontend deploy
git diff -- backend frontend deploy > revue/etape3-lot4.diff
```

Contenu habituel : build et tests (avant et après), fichiers, écarts et pourquoi, points à valider, contrôles manuels. Pour les contrôles manuels : activer un module depuis le back-office est impossible sans jeton `platform`. Fais donc au minimum la connexion dans le navigateur (par le développeur) → une entrée `USER_LOGGED_IN` visible par `GET /api/v1/audit-events` et en SQL ; et vérifie qu'un échec de connexion apparaît dans les événements Keycloak.

Message de commit proposé (sans ligne d'attribution) :

```
feat(iam): append-only audit log written in the action transaction, login events and audit API
```

**Ne commite pas** avant l'accord du développeur.
