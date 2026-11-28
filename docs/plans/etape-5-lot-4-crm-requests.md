# Étape 5, lot 4 : demandes clients (backend, module `crm`) et annuaire de l'équipe (`iam`)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User story : US-701 (enregistrer une demande avec sa source, pour suivre d'où viennent les clients).
> Références :
> - 04-modele-de-donnees.md §6 (`request`) ;
> - 01-cahier-des-charges.md §5.2 (statuts de la demande : Nouvelle, En chiffrage, Devis envoyé, Gagnée, Perdue) ;
> - décisions du 27/11/2026 (les demandes sont dans `crm`) et du 28/11/2026 : raison de perte obligatoire ; champs du besoin (destination, dates souhaitées ou approximatives, adultes et enfants, budget indicatif, description) ; **un client existant est obligatoire** (le bouton « Créer le client » viendra dans l'écran du lot 6) ;
> - lots 1 à 3 (`AuthoredAgencyEntity`, `AuditableEvent`, `Money`, `MoneyJson`, `SearchKeys`, `stale-version`, `customer-archived`).
>
> Périmètre : **backend uniquement**. Les écrans arrivent au lot 6.

## 1. Objectif

1. Enregistrer une demande rattachée à un client de l'agence, avec son canal, sa source, son responsable et le besoin exprimé.
2. Suivre son statut avec des **transitions contrôlées**, et une **raison obligatoire** quand elle est perdue.
3. Tracer chaque changement de statut dans le journal d'audit (codes seulement).
4. Ajouter un **annuaire de l'équipe** en lecture pour tous les utilisateurs de l'agence : les écrans des lots 5 et 6 doivent afficher le nom du responsable d'un client ou d'une demande, et `GET /api/v1/users` est réservé à `user.manage`.

## 2. Décisions de conception (à respecter)

### 2.1 Nommage

- Entité `CustomerRequest`, table **`customer_request`** (et non `request`, trop générique côté Java et SQL). L'API garde le mot simple : `/api/v1/requests`. Note l'écart dans la migration ; le modèle de données sera mis à jour au bilan de l'étape.

### 2.2 Client obligatoire

- `customer_id` obligatoire, clé étrangère composée `(customer_id, agency_id)` vers `customer`.
- Client inconnu ou d'une autre agence → 404 `not-found` ; client **archivé** → 409 `customer-archived` (code du lot 2), à la création et quand on change le client d'une demande.
- Une demande existante garde son client même si celui-ci est archivé ensuite.

### 2.3 Responsable

- `owner_user_id` obligatoire, clé composée vers `user_account (id, agency_id)` comme au lot 1 (`invalid-owner` si l'utilisateur n'est pas de l'agence).
- Par défaut : l'utilisateur courant (`UserContext`) si c'est un utilisateur d'agence. Un utilisateur de la plateforme qui agirait dans une agence n'a pas de compte `user_account` : il doit alors donner `ownerUserId` explicitement, sinon 400 `validation-failed`.
- Le responsable sert à l'affichage et au filtrage (« Mes demandes »), **jamais** à restreindre l'accès : tous les utilisateurs de l'agence voient toutes ses demandes, comme les clients.

### 2.4 Statuts et transitions

| Depuis | Vers autorisés |
|---|---|
| `NEW` | `QUOTING`, `LOST` |
| `QUOTING` | `QUOTE_SENT`, `LOST` |
| `QUOTE_SENT` | `QUOTING` (le client demande une autre proposition), `WON`, `LOST` |
| `LOST` | `NEW` (réouverture) |
| `WON` | aucun (état final ; la conversion en dossier arrive à l'étape 6) |

- Toute autre transition → 409 `invalid-status-transition`, avec `from` et `to`. Passer au statut courant → 200 sans changement ni audit (idempotent).
- `QUOTE_SENT` se met à la main à ce lot ; à l'étape 6, l'envoi d'un devis le mettra automatiquement. Ajoute un `TODO(etape-6)` à l'endroit de la transition.
- Passer en `LOST` exige `lostReason` (400 `validation-failed` sinon) ; `lostComment` facultatif (500 caractères). Rouvrir (`LOST → NEW`) efface la raison et le commentaire.
- `status_changed_at` mis à jour à chaque changement effectif.
- Le statut ne se change **que** par l'opération dédiée (section 5), jamais par le `PUT` de la demande.

### 2.5 Raisons de perte

`PRICE` (prix), `COMPETITOR` (concurrent), `DATES_UNAVAILABLE` (dates ou disponibilités), `PROJECT_CANCELLED` (projet annulé), `NO_RESPONSE` (pas de réponse du client), `OTHER` (autre, commentaire conseillé mais pas obligatoire). Liste à valider avec l'agence pilote ; tout changement passera par une migration (`CHECK`).

### 2.6 Canal et source

- `channel` obligatoire : comment la demande est arrivée. Valeurs du modèle de données : `FORM`, `EMAIL`, `WHATSAPP`, `PHONE`, `WALK_IN`.
- `source` facultative : origine commerciale. Réutilise l'enum `CustomerSource` du lot 1 (même liste et même `CHECK`), pour que « d'où viennent mes clients » se lise pareil sur les clients et sur les demandes.

### 2.7 Besoin exprimé

- `destination` (texte libre, 200) ; `departure_date` et `return_date` facultatives (`return_date >= departure_date` si les deux sont présentes) ; `dates_note` (texte libre, 200 : « mi-juillet, environ une semaine ») pour les dates approximatives ; `adults` (0 à 999, défaut 1) ; `children` (0 à 999, défaut 0) ; au moins une personne au total ; `budget` facultatif en `MoneyJson` (montant positif, devise envoyée ou MAD par défaut, mêmes règles qu'au lot 3, aucun arrondi) ; `description` (4 000).
- Des dates dans le passé sont acceptées (saisie d'historique, import futur).
- Pas d'âge des enfants ni de champs MICE à ce lot.

### 2.8 Audit

- Nouvelle action `REQUEST_STATUS_CHANGED`, `entityType` = `REQUEST`, `details` = `{"from": "QUOTING", "to": "LOST", "lostReason": "PRICE"}` (`lostReason` seulement pour `LOST`). **Jamais** le commentaire, la destination, la description ni le budget.
- Pas d'audit de la création ni de la modification à ce lot.

### 2.9 Annuaire de l'équipe (`iam`)

- `GET /api/v1/team` : tous les utilisateurs **ACTIVE et INVITED** de l'agence courante, avec `id`, `firstName`, `lastName`, `status`. Ni e-mail, ni rôles, ni date de connexion. Trié par nom.
- Accessible à **tout utilisateur de l'agence** (pas de permission) ; un utilisateur de la plateforme sans agence → comportement habituel des routes d'agence.
- Code dans `iam` (lecture de `user_account`), aucune dépendance de `crm` vers `iam` : le frontend fait la correspondance identifiant → nom.

## 3. Migration `V10__create_crm_requests.sql`

```
customer_request
  id UUID PK, agency_id UUID NOT NULL REFERENCES agency(id)
  customer_id UUID NOT NULL
  owner_user_id UUID NOT NULL
  channel VARCHAR(20) NOT NULL CHECK (channel IN ('FORM','EMAIL','WHATSAPP','PHONE','WALK_IN'))
  source VARCHAR(30) NULL CHECK (source IN (même liste que customer.source))
  status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','QUOTING','QUOTE_SENT','WON','LOST'))
  status_changed_at TIMESTAMPTZ NOT NULL
  lost_reason VARCHAR(30) NULL CHECK (lost_reason IN ('PRICE','COMPETITOR','DATES_UNAVAILABLE',
       'PROJECT_CANCELLED','NO_RESPONSE','OTHER'))
  lost_comment VARCHAR(500) NULL
  destination VARCHAR(200) NULL
  departure_date DATE NULL, return_date DATE NULL
  dates_note VARCHAR(200) NULL
  adults SMALLINT NOT NULL DEFAULT 1 CHECK (adults BETWEEN 0 AND 999)
  children SMALLINT NOT NULL DEFAULT 0 CHECK (children BETWEEN 0 AND 999)
  budget_amount NUMERIC(19,4) NULL CHECK (budget_amount > 0)
  budget_currency VARCHAR(3) NULL CHECK (budget_currency ~ '^[A-Z]{3}$')
  description VARCHAR(4000) NULL
  search_key VARCHAR(500) NOT NULL   -- SearchKeys.words de la destination ; jamais renvoyé
  colonnes d'auteur et de version habituelles
  CONSTRAINT ck_request_people CHECK (adults + children >= 1)
  CONSTRAINT ck_request_dates CHECK (return_date IS NULL OR departure_date IS NULL OR return_date >= departure_date)
  CONSTRAINT ck_request_budget CHECK ((budget_amount IS NULL) = (budget_currency IS NULL))
  CONSTRAINT ck_request_lost CHECK ((status = 'LOST') = (lost_reason IS NOT NULL))
  CONSTRAINT fk_request_customer_same_agency FOREIGN KEY (customer_id, agency_id) REFERENCES customer (id, agency_id)
  CONSTRAINT fk_request_owner_same_agency FOREIGN KEY (owner_user_id, agency_id) REFERENCES user_account (id, agency_id)
```

- Index : `(agency_id, status, created_at DESC)`, `(agency_id, owner_user_id, status)`, `(customer_id)`, `(agency_id, search_key)`.
- RLS et politique `tenant_isolation`, comme V6, V8 et V9.
- `ck_request_lost` garantit en base qu'une demande perdue a toujours une raison et qu'une autre n'en a jamais.

## 4. Règles métier (résumé)

| Règle | Détail |
|---|---|
| Création | Statut `NEW`, `status_changed_at` = maintenant, responsable par défaut (2.3), client existant et non archivé (2.2) |
| Modification (`PUT`) | Remplace client, canal, source, responsable et besoin ; jamais le statut ; `version` obligatoire → 409 `stale-version` |
| Demande `WON` ou `LOST` | Reste modifiable (correction d'une saisie) ; seul le statut suit les transitions |
| Transitions | Tableau 2.4 ; 409 `invalid-status-transition` ; idempotent sur le même statut |
| Raison de perte | Obligatoire en `LOST`, effacée à la réouverture |
| Budget | `MoneyJson`, aucun arrondi, devise par défaut MAD |
| Suppression | Aucune : une demande abandonnée passe en `LOST` (`PROJECT_CANCELLED`) |

## 5. API

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/requests` | Filtres : `status` (répétable : `?status=NEW&status=QUOTING`), `ownerUserId`, `customerId`, `channel`, `createdFrom`, `createdTo` (dates, fuseau de l'agence non géré : UTC, `TODO` existant), `q` (destination sans accents, et nom du client par sous-requête sur `customer.name`). `page`, `size` (25, max 100), `sort` = `-createdAt` (défaut), `-statusChangedAt`, `departureDate`. Liste : `id`, `status`, `statusChangedAt`, `customer` (`id`, `name`, `type`), `ownerUserId`, `channel`, `destination`, `departureDate`, `returnDate`, `adults`, `children`, `createdAt` |
| GET | `/api/v1/requests/{id}` | Fiche complète, avec `budget` (`MoneyJson`), `lostReason`, `lostComment`, `version` |
| POST | `/api/v1/requests` | `crm.write` ; 201 + `Location` ; 404 client, 409 `customer-archived`, 400 `invalid-owner` |
| PUT | `/api/v1/requests/{id}` | `crm.write` ; `version` ; mêmes contrôles |
| POST | `/api/v1/requests/{id}/status` | `crm.write` ; corps `{ version, status, lostReason, lostComment }` ; 200 avec la fiche ; 409 `invalid-status-transition` ou `stale-version` ; 400 si `LOST` sans raison |
| GET | `/api/v1/team` | Tout utilisateur de l'agence (section 2.9) |

- Nouveau code traduit FR/AR/EN : `invalid-status-transition` (détail avec le statut de départ et d'arrivée **en code**, le frontend affichera les libellés). Réutilisés : `not-found`, `customer-archived`, `invalid-owner`, `stale-version`, `validation-failed`, `permission-denied`.
- `GET /api/v1/customers/{id}` ne change pas ; les demandes d'un client s'obtiennent par `GET /api/v1/requests?customerId=…`.
- OpenAPI : tags `requests` et `team` ; le tableau des transitions décrit sur l'opération de statut.

## 6. Tests

| Test | Ce qu'il vérifie |
|---|---|
| `RequestApiTest` | Création minimale (client, canal) avec statut `NEW` et responsable = utilisateur courant ; besoin complet ; dates incohérentes → 400 ; 0 adulte et 0 enfant → 400 ; budget en chaîne, sans arrondi, MAD par défaut ; filtres (`status` répété, `ownerUserId`, `customerId`, `channel`, période), tri, pagination ; `q` sur la destination sans accent et sur le nom du client ; le `PUT` ne change pas le statut ; ACCOUNTANT lit (200) mais ne crée pas (403) |
| `RequestCustomerTest` | Client inconnu ou d'une autre agence → 404 ; client archivé → 409 à la création et au changement de client ; archiver le client garde la demande |
| `RequestOwnerTest` | Responsable par défaut ; responsable d'une autre agence → 400 `invalid-owner` ; utilisateur de la plateforme sans `ownerUserId` → 400 |
| `RequestStatusTest` | Chaque transition autorisée du tableau 2.4 ; chaque transition interdite → 409 avec `from` et `to` ; `WON` final ; idempotence ; `LOST` sans raison → 400 ; réouverture efface raison et commentaire ; `status_changed_at` ; version périmée → 409 |
| `RequestAuditTest` | Une entrée `REQUEST_STATUS_CHANGED` par changement effectif, `details` limités à `from`, `to`, `lostReason` ; aucune entrée pour une transition idempotente ou refusée ; ni commentaire ni destination dans `details` |
| `TeamApiTest` | Tous les rôles lisent ; ACTIVE et INVITED seulement ; aucun e-mail ni rôle dans la réponse ; jamais un utilisateur d'une autre agence |
| `TenantIsolationTest` (étendu) | RLS de `customer_request` ; clés composées client et responsable d'une autre agence refusées par la base |
| `ModularityTests` | `crm` → `shared` seulement ; l'annuaire reste dans `iam` |

## 7. Contrôles manuels (développeur, console du navigateur, fonction `api` du lot 1)

1. Créer une demande pour « Atlas Pharma Test » : `{ customerId, channel: 'WHATSAPP', source: 'REFERRAL', destination: 'Istanbul', datesNote: 'mi-juillet, une semaine', adults: 2, children: 1, budget: { amount: '25000.00' } }` → 201, `status: 'NEW'`, `ownerUserId` = l'identifiant de `claim-check`, budget en MAD.
2. Passer en `QUOTING`, puis tenter `WON` directement → 409 `invalid-status-transition`.
3. Passer en `QUOTE_SENT`, puis en `LOST` sans raison → 400 ; avec `lostReason: 'PRICE'` → 200.
4. Rouvrir (`NEW`) → la raison disparaît.
5. Le journal (`/api/v1/audit-events`) montre les changements avec `{"from":…, "to":…}` et `lostReason` pour la perte, sans la destination.
6. Rechercher `istanbul` et `atlas` → la demande est trouvée.
7. `GET /api/v1/team` → la liste des utilisateurs avec nom et prénom, sans e-mail.

## 8. Hors périmètre

Devis et passage automatique en `QUOTE_SENT` (étape 6), conversion en dossier (étape 6), tâches et relances (« Ma journée », P2), formulaire web public de demande, réception automatique des e-mails et messages WhatsApp, voyageurs rattachés à une demande, champs MICE, numérotation des demandes, écrans (lot 6).

## 9. Compte rendu attendu

Dans `revue/etape5-lot4-compte-rendu.md` et `revue/etape5-lot4.diff` (`git add -N backend` puis `git diff -- backend`) :
- build et tests (avant / après) ;
- fichiers créés et modifiés ;
- écarts au plan et pourquoi ;
- points à valider ;
- contrôles manuels de la section 7 avec les extraits de code pour la console ;
- contrôle des secrets et des données (données fictives uniquement) ;
- message de commit proposé, sans ligne d'attribution :

```
feat(crm): customer requests with controlled status transitions and lost reasons, team directory (US-701)
```
