# Étape 3, lot 5 : invitations et gestion des utilisateurs (API d'administration Keycloak)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User stories : US-201 (inviter un utilisateur et lui attribuer un rôle), fin de US-101 (le gérant reçoit son accès à la création de l'agence).
> Références : ADR-08 (règle 2 : tout le code Keycloak dans un seul adaptateur du module `iam` ; règle 3 : ce lot séparé), décision du 24/11/2026 (invitation par e-mail, aucun mot de passe généré, affiché ni envoyé), 04-modele-de-donnees.md (§2.2).
> Périmètre : **backend et realm local**. Les écrans Angular (liste des utilisateurs, invitation, journal) feront un **lot 6**.

## 1. Objectif

1. **Back-office** : l'éditeur rattache le gérant à une agence existante. Le backend crée l'Organization Keycloak de l'agence si elle n'existe pas encore (alias = UUID de l'agence), puis invite le gérant par e-mail avec le rôle MANAGER.
2. **Agence** : le gérant (permission `user.manage`) invite son équipe, attribue les rôles, renvoie une invitation, désactive ou réactive un utilisateur.
3. **Invitation** : Keycloak envoie un e-mail avec un lien à durée limitée. Le nouvel utilisateur vérifie son adresse et **choisit lui-même** son mot de passe.
4. Le nombre d'utilisateurs respecte le palier (`plan.max_users`).
5. Toutes ces actions sont tracées dans le journal d'audit.
6. Les événements de connexion sont activés dans le realm `platform`.

## 2. Vérifications préalables (obligatoires, sur le Keycloak local 26.6)

Avant d'écrire l'adaptateur, vérifie chaque appel sur le Keycloak local avec `curl` et le compte de service (section 3). Note le résultat dans le compte rendu. **N'utilise aucun appel non vérifié.** Si l'un ne se comporte pas comme décrit, arrête-toi et décris ce que tu observes.

| Besoin | Appel prévu (API d'administration, realm `agencysuite`) | À vérifier |
|---|---|---|
| Créer l'Organization | `POST /admin/realms/agencysuite/organizations`, corps `OrganizationRepresentation` (`name`, `alias`, `enabled`) | Les domaines sont-ils obligatoires ? (sinon, n'en mets pas). Un UUID est-il accepté comme alias ? (L'alias ne peut plus changer une fois défini.) Où lire l'identifiant créé (en-tête `Location`) ? |
| Retrouver l'Organization par alias | `GET /admin/realms/agencysuite/organizations` avec un paramètre de recherche | Nom exact du paramètre, et correspondance **exacte** sur l'alias |
| Créer l'utilisateur | `POST /admin/realms/agencysuite/users` (`username` = e-mail, `email`, `firstName`/`lastName`, `enabled: true`, `emailVerified: false`, attribut `locale`) | Identifiant dans `Location` ; c'est bien le `sub` des jetons ; réponse 409 si l'e-mail existe déjà |
| Ajouter le membre | `POST /admin/realms/agencysuite/organizations/{orgId}/members`, corps = identifiant de l'utilisateur | Format exact du corps |
| Envoyer l'invitation | `PUT /admin/realms/agencysuite/users/{id}/execute-actions-email?client_id=agencysuite-frontend&redirect_uri=<origine>/&lifespan=<s>`, corps `["VERIFY_EMAIL","UPDATE_PASSWORD"]` | L'e-mail arrive dans Mailpit, dans la langue de l'attribut `locale` ; le lien expire bien |
| Désactiver / réactiver | `PUT /admin/realms/agencysuite/users/{id}` avec `enabled` | — |
| Fermer les sessions | `POST /admin/realms/agencysuite/users/{id}/logout` | — |
| Supprimer (compensation) | `DELETE /admin/realms/agencysuite/users/{id}` | — |

On n'utilise **pas** l'invitation propre aux Organizations (`.../members/invite-user`). Elle laisse l'utilisateur créer son compte lui-même, et nous ne connaîtrions son `sub` qu'après coup. Or `user_account` exige `external_idp_id` dès l'invitation.

## 3. Compte de service (realm `agencysuite`)

- Nouveau client **confidentiel** `agencysuite-backend` : *client authentication* activée, **comptes de service** activés, aucun autre flux (pas de connexion interactive, pas d'accès direct par mot de passe).
- Rôles du compte de service : le **minimum** du client `realm-management` qui permet les appels de la section 2. Commence par `manage-users` et `view-users`, ajoute ce qui manque pour les Organizations, et **liste dans le compte rendu les rôles retenus et pourquoi**. N'attribue pas `realm-admin`.
- **Le secret n'est jamais dans le dépôt** :
  - le client est déclaré dans `agencysuite-realm.json` **sans** champ `secret` (Keycloak en génère un) ;
  - en local, le développeur copie le secret depuis la console dans `deploy/.env` (`KEYCLOAK_BACKEND_CLIENT_SECRET`) ;
  - `.env.example` reçoit la variable **vide**, avec un commentaire.
- Applique la création du client **sans réimport** (`kcadm`), comme aux lots précédents, et documente la procédure dans `deploy/README.md`.
- Configuration backend :
  ```yaml
  agencysuite:
    keycloak-admin:
      base-url: ${KEYCLOAK_BASE_URL:http://localhost:8180}
      realm: agencysuite
      client-id: agencysuite-backend
      client-secret: ${KEYCLOAK_BACKEND_CLIENT_SECRET:}
      invitation-lifespan: PT72H
      app-base-url: ${APP_BASE_URL:http://localhost:4200}
  ```
  Sans secret, l'application démarre quand même : les opérations d'invitation répondent alors 503 `identity-admin-unavailable`. Les tests n'ont pas besoin de Keycloak (section 7).

## 4. Adaptateur Keycloak (`iam/infrastructure/keycloak/`)

- Port : `iam/application/IdentityProviderAdmin` (interface, aucun type Keycloak dans sa signature) :
  - `String ensureOrganization(AgencyId agencyId, String name)` → identifiant de l'Organization ;
  - `String createUser(String email, String firstName, String lastName, String locale)` → `sub` ;
  - `void addMember(String organizationId, String sub)` ;
  - `void sendInvitation(String sub)` ;
  - `void setEnabled(String sub, boolean enabled)` ;
  - `void logout(String sub)` ;
  - `void deleteUser(String sub)`.
- Implémentation `KeycloakIdentityProviderAdmin` avec le `RestClient` de Spring et de petits DTO internes. **Pas de dépendance `keycloak-admin-client`** : il est lié à la version du serveur, et nous n'utilisons que quelques appels.
- Jeton du compte de service obtenu par le flux *client credentials*, mis en cache jusqu'à 30 secondes avant son expiration. **Jamais journalisé.**
- Erreurs :
  - e-mail déjà existant (409 Keycloak) → exception métier `EmailAlreadyRegisteredException` ;
  - toute autre erreur ou Keycloak injoignable → `IdentityAdminUnavailableException` (503 `identity-admin-unavailable`).
  - Délais de connexion et de lecture courts (5 s et 10 s).
  - Jamais d'e-mail ni de réponse brute de Keycloak dans les journaux.

## 5. Règles métier

### 5.1 Inviter un utilisateur (service `UserInvitations`)

Ordre des opérations, car Keycloak et notre base ne sont pas dans la même transaction :

1. Contrôles dans la base (périmètre de l'agence) :
   - e-mail pas déjà utilisé dans l'agence (sinon 409 `email-already-registered`) ;
   - quota : le nombre d'utilisateurs **non DISABLED** doit être inférieur à `plan.max_users` du palier actif (nul = illimité), sinon 409 `user-quota-exceeded`. Le palier se lit via une nouvelle méthode `PlatformApi`, avec la règle de périmètre habituelle ;
   - au moins un rôle valide.
2. Keycloak :
   - `ensureOrganization` (idempotent) ;
   - `createUser`, avec l'attribut `locale` = langue par défaut de l'agence ;
   - `addMember`.
3. Base, **une transaction** dans le périmètre de l'agence : `user_account` INVITED (avec le `sub`), ses `user_role`, et l'audit `USER_INVITED` (`details` : rôles).
4. Si l'étape 3 échoue → **compensation** : `deleteUser(sub)`, puis l'erreur d'origine est relancée. Si la compensation échoue aussi, journalise un avertissement avec le `sub` (pseudonyme), sans e-mail.
5. Après le commit : `sendInvitation`. Un échec n'annule rien : la réponse indique `invitationSent: false`, et le gérant pourra renvoyer l'invitation.

### 5.2 Première connexion d'un invité

Changement par rapport au lot 2 : `CurrentUserResolver` accepte désormais **INVITED et ACTIVE** (DISABLED reste refusé). Keycloak ne laisse se connecter un invité qu'une fois l'e-mail vérifié et le mot de passe choisi.

Au premier `POST /api/v1/me/login-events`, un utilisateur INVITED passe ACTIVE, avec l'audit `USER_ACTIVATED`. Mets à jour la Javadoc et les tests du lot 2 qui affirmaient « INVITED refusé ».

### 5.3 Autres actions

| Action | Règles | Keycloak | Audit |
|---|---|---|---|
| Renvoyer l'invitation | Seulement si INVITED, sinon 409 `user-not-invited` | `sendInvitation` | `INVITATION_RESENT` |
| Changer les rôles (remplacer l'ensemble) | Au moins un rôle ; ne pas retirer le dernier MANAGER **actif ou invité** de l'agence (409 `last-manager`) | — | `ROLE_ASSIGNED` / `ROLE_REVOKED`, une entrée par rôle ajouté ou retiré |
| Désactiver | Interdit sur soi-même (409 `cannot-disable-self`) et sur le dernier MANAGER (409 `last-manager`) | `setEnabled(false)` puis `logout` | `USER_DISABLED` |
| Réactiver | Le quota s'applique (409 `user-quota-exceeded`) | `setEnabled(true)` | `USER_ENABLED` |

Désactiver : l'ordre est base d'abord (DISABLED, l'accès est coupé tout de suite par notre filtre), puis Keycloak. Si Keycloak échoue, l'utilisateur reste bloqué chez nous, ce qui est l'effet voulu. Journalise l'avertissement et réponds 200 avec `identityProviderSynced: false`.

### 5.4 Rattacher le gérant depuis le back-office

`POST /api/v1/backoffice/agencies/{id}/manager` `{ "email", "firstName", "lastName" }`, avec un jeton `platform`.

- **Idempotent** pour l'Organization (déjà créée → réutilisée), audit `ORGANIZATION_PROVISIONED` à la première création.
- Puis invitation MANAGER, selon les mêmes règles que 5.1. L'écriture en base se fait **dans le périmètre de l'agence ciblée**, ouvert explicitement par le service pour cette seule transaction. Le back-office est le seul point d'entrée autorisé à le faire : ajoute-le à la règle de `CLAUDE.md` (« un service n'ouvre jamais de périmètre lui-même… sauf le rattachement du gérant par le back-office »), avec un commentaire dans le code.
- Agence inconnue → 404 ; agence CLOSED → 409.

## 6. API

Agence (permission `user.manage`, `@RequiresPermission(USER_MANAGE)`) :

| Méthode | Chemin | Corps / réponse |
|---|---|---|
| GET | `/api/v1/users` | Liste : `id`, `email`, `fullName`, `status`, `roles`, `lastLoginAt` |
| POST | `/api/v1/users` | `{ email, firstName, lastName, roles[] }` → 201, `Location`, `{ id, status: "INVITED", invitationSent }` |
| PUT | `/api/v1/users/{id}/roles` | `{ roles[] }` → 200 |
| POST | `/api/v1/users/{id}/invitation` | → 202 |
| POST | `/api/v1/users/{id}/disable` | → 200 `{ identityProviderSynced }` |
| POST | `/api/v1/users/{id}/enable` | → 200 `{ identityProviderSynced }` |

Back-office : `POST /api/v1/backoffice/agencies/{id}/manager` (section 5.4).

- Validation : e-mail valide (`@Email`, 254 caractères au plus), prénom et nom de 1 à 100 caractères, rôles parmi les codes connus.
- Un `id` d'une autre agence → **404**, jamais 403. Ne révèle pas qu'il existe ailleurs : il est de toute façon invisible avec la RLS.
- Toutes les erreurs au format problem+json traduit (FR/AR/EN) : `email-already-registered`, `user-quota-exceeded`, `last-manager`, `cannot-disable-self`, `user-not-invited`, `identity-admin-unavailable`.

## 7. Tests

L'adaptateur Keycloak est remplacé dans les tests d'intégration par une **fausse implémentation en mémoire** (`FakeIdentityProviderAdmin`, dans `src/test`), qui sait simuler des pannes. Aucun test ne demande un Keycloak réel.

| Classe | Cas |
|---|---|
| `KeycloakIdentityProviderAdminTest` (unitaire, `MockRestServiceServer` ou équivalent vérifié) | Forme exacte des requêtes de la section 2 ; jeton du compte de service mis en cache ; 409 → `EmailAlreadyRegisteredException` ; 500 ou délai dépassé → `IdentityAdminUnavailableException` ; le secret et le jeton n'apparaissent pas dans les messages d'erreur |
| `UserInvitationsTest` | Invitation OK : utilisateur INVITED, rôles, audit, invitation envoyée après le commit ; e-mail déjà dans l'agence → 409 sans appel à Keycloak ; quota atteint → 409 ; **échec en base après la création Keycloak → `deleteUser` appelé** ; échec de l'envoi → `invitationSent: false`, utilisateur conservé |
| `UserManagementApiTest` | Les endpoints de la section 6 ; SALES → 403 `permission-denied` ; `id` d'une autre agence → 404 ; dernier MANAGER (rôles et désactivation) → 409 ; se désactiver soi-même → 409 ; désactivation → l'utilisateur reçoit 403 à la requête suivante ; renvoi pour un ACTIVE → 409 |
| `FirstLoginTest` | Jeton d'un INVITED → accès autorisé ; `login-events` → ACTIVE + `USER_ACTIVATED` ; DISABLED → toujours 403 |
| `BackofficeManagerApiTest` | Rattachement → Organization créée une fois, puis réutilisée au second appel ; écriture dans la bonne agence (pas l'identifiant fictif de la plateforme) ; agence inconnue → 404 ; jeton d'agence → refusé |
| `AuditTrailTest` (étendu) | Chaque action de la section 5 produit son entrée ; `details` sans e-mail ni nom |
| `ModularityTests`, `TenantIsolationTest` | Restent verts |

Pendant le développement, lance les tests ciblés ; un seul `verify` complet à la fin.

## 8. Realm `platform`

Active les événements de connexion comme pour `agencysuite` au lot 4 (`eventsEnabled`, `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, 90 jours, événements d'administration sans détail, **sans** `jboss-logging`), avec `kcadm` sans réimport, et dans `platform-realm.json`.

## 9. Contrôles manuels

Avec la pile locale, le secret copié dans `deploy/.env`, Mailpit sur http://localhost:8025 et le jeton de `claim-check` (MANAGER) :

1. `POST /api/v1/users` (un e-mail fictif `@example.test`, rôle SALES) → 201 ; l'e-mail d'invitation arrive dans Mailpit, en français.
2. Le développeur clique sur le lien, choisit un mot de passe, puis se connecte sur http://localhost:4200 → accès accordé ; en SQL, le statut passe ACTIVE.
3. `POST /api/v1/users/{id}/disable` → l'utilisateur invité est refusé à sa requête suivante, et apparaît désactivé dans la console Keycloak.
4. Tentative de désactiver `claim-check` lui-même → 409 `cannot-disable-self`.
5. Journal : `GET /api/v1/audit-events` contient `USER_INVITED`, `USER_ACTIVATED`, `USER_DISABLED`.
6. (Si un jeton `platform` est disponible) rattachement d'un gérant à une nouvelle agence → l'Organization apparaît dans la console avec l'UUID comme alias.

Fais toi-même ce qui est possible sans navigateur (appels, Mailpit par son API, SQL). Laisse au développeur une liste à cocher pour le reste.

## 10. Hors périmètre

- Écrans Angular : lot 6 (liste et invitation des utilisateurs, journal d'audit).
- Suppression définitive d'un utilisateur (droits des personnes) : étape 13b.
- Changement d'e-mail d'un utilisateur.
- Double authentification exigée par l'agence : lot à part, après vérification de ce que Keycloak met dans le jeton (`acr`/`amr`).
- Gestion des `platform_user` par API.

## 11. Points à valider (à reporter dans le compte rendu)

- Durée de validité de l'invitation (72 h proposées).
- Le quota compte les utilisateurs INVITED et ACTIVE (pas les DISABLED) : à confirmer commercialement.
- Rôles `realm-management` attribués au compte de service.

## 12. Compte rendu attendu

`revue/etape3-lot5-compte-rendu.md` et le diff :

```
git add -N backend deploy CLAUDE.md
git diff -- backend deploy CLAUDE.md > revue/etape3-lot5.diff
```

Contenu habituel, plus le résultat des vérifications préalables (section 2) et les rôles du compte de service. **Aucun secret, jeton ni vrai e-mail dans le compte rendu ni dans le diff** : vérifie le diff avant de terminer.

Message de commit proposé (sans ligne d'attribution) :

```
feat(iam): invite users through Keycloak admin API, manage roles and status, provision agency organization (US-201)
```

**Ne commite pas** avant l'accord du développeur.
