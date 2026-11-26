# Étape 3, lot 6 : écrans « Utilisateurs » et « Journal d'activité » (Angular)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User stories : US-201 côté interface (le gérant invite son équipe et attribue les rôles, sans code ni SQL) ; consultation du journal d'audit (permission `audit.read`).
> Références : API des lots 4 et 5 (`/api/v1/users`, `/api/v1/audit-events`), `CurrentUserService.has()` (lot 3).
> Périmètre : **frontend uniquement**. Si un besoin d'API apparaît, arrête-toi et explique-le au lieu de modifier le backend.

## 1. Objectif

Deux écrans dans une rubrique « Administration », visibles et accessibles selon les permissions de l'utilisateur :

1. **Utilisateurs** (`user.manage`) :
   - liste ;
   - invitation ;
   - modification des rôles ;
   - renvoi d'invitation ;
   - désactivation et réactivation.
2. **Journal d'activité** (`audit.read`) : liste paginée et lisible des actions de l'agence.

Tout en FR, AR (RTL) et EN, avec les messages d'erreur du serveur.

## 2. Règles transverses

- **Les permissions côté interface sont du confort.** Le serveur reste la barrière (`@RequiresPermission`). Masquer un bouton ne remplace jamais le contrôle serveur.
- **Messages d'erreur** : le serveur renvoie déjà `title` et `detail` traduits selon `Accept-Language`. On affiche `detail` quand le `type` commence par `urn:agencysuite:problem:`, sinon un message générique traduit. Jamais de message technique brut.
- **Aucune donnée venue du serveur en HTML** (`innerHTML` interdit, règle existante). Les e-mails, les noms et les identifiants sont isolés avec `<bdi>`.
- **Dates** : affichées dans la langue courante avec le `DatePipe` d'Angular, avec les données de langue enregistrées pour `fr`, `ar` et `en`. Vérifie dans la configuration actuelle ce qui manque, et ajoute-le. Fuseau : celui du navigateur pour l'instant. Le fuseau de l'agence viendra avec le paramétrage (`TODO(etape-5)`).
- **Confirmation** des actions sensibles (désactivation, retrait de rôle MANAGER) par un petit dialogue Angular. **Jamais** `window.confirm` ni `alert`.
- Composants autonomes, signals, formulaires réactifs, CSS en propriétés logiques uniquement.

## 3. Livrables (sous `frontend/src/app/`)

### 3.1 Garde de permission et menu

- `core/auth/requires-permission.guard.ts` : `requiresPermission(code: string): CanMatchFn`.
  - Il attend le chargement du profil (`CurrentUserService`), puis autorise si `has(code)`.
  - Sinon, il redirige vers l'accueil. Ne crée pas de nouvelle page de refus : l'interface ne propose de toute façon pas le lien.
- Routes, sous la route parente protégée du lot 3 :
  - `/administration/utilisateurs` avec `requiresPermission('user.manage')` ;
  - `/administration/journal` avec `requiresPermission('audit.read')`.
- Menu : une section « Administration », affichée seulement si l'utilisateur a au moins une des deux permissions, avec un lien par écran autorisé.

### 3.2 Client API

`core/api/users-api.ts` et `core/api/audit-api.ts`. Les types sont alignés **exactement** sur les réponses des lots 4 et 5 : relis les contrôleurs `UsersController` et `AuditEventsController` pour les noms de champs, sans les deviner.

### 3.3 Écran « Utilisateurs » (`features/admin-users/`)

**Liste (tableau)** :
- colonnes : nom, e-mail, rôles (libellés traduits), statut (badge traduit : Invité, Actif, Désactivé), dernière connexion (ou « Jamais »), actions ;
- tri par nom ; aucune pagination (au plus 500 utilisateurs par palier) ;
- l'utilisateur courant est marqué « (vous ») et son bouton « Désactiver » est absent.

**Inviter (bouton « Inviter un utilisateur »)**, formulaire en panneau ou en dialogue :
- e-mail : obligatoire, format e-mail, 254 caractères au plus ;
- prénom et nom : obligatoires, **80 caractères au plus** (aligné sur le serveur) ;
- rôles : cases à cocher (Gérant, Commercial, Opérations, Comptable), au moins une, avec une courte description traduite de chaque rôle sous la case ;
- envoi :
  - succès avec `invitationSent: true` → message « Invitation envoyée à <e-mail> » ;
  - succès avec `invitationSent: false` → avertissement « L'utilisateur est créé, mais l'e-mail n'est pas parti », avec un bouton « Renvoyer l'invitation » ;
  - erreurs 409 (`email-already-registered`, `user-quota-exceeded`) et 503 (`identity-admin-unavailable`) → `detail` du serveur affiché dans le formulaire ; le formulaire reste rempli.

**Actions par ligne** :
- **Modifier les rôles** : mêmes cases à cocher, préremplies.
  - Retirer le rôle Gérant de l'utilisateur courant demande une confirmation (« Vous ne pourrez plus gérer les utilisateurs »).
  - 409 `last-manager` → `detail` affiché.
- **Renvoyer l'invitation** : seulement pour un utilisateur INVITED.
- **Désactiver**, avec confirmation, ou **Réactiver**, selon le statut.
  - Si la réponse contient `identityProviderSynced: false`, avertissement « Action enregistrée ; la synchronisation avec le service de connexion a échoué, réessayez ». Réessayer = refaire la même action, qui est idempotente côté serveur.
  - 409 `user-quota-exceeded` à la réactivation → `detail` affiché.
- Après chaque action réussie, la liste est rechargée.

**État vide et chargement** : indicateur discret, et un message clair si la liste ne peut pas être chargée.

### 3.4 Écran « Journal d'activité » (`features/admin-audit/`)

- Tableau paginé (50 par page, boutons Précédent et Suivant, numéro de page) : date et heure, auteur, action, objet, détails.
- **Auteur** :
  - `AGENCY_USER` → nom de l'utilisateur, retrouvé dans la liste des utilisateurs si l'écran a la permission `user.manage` ; sinon, ou s'il est introuvable, « Utilisateur de l'agence » ;
  - `PLATFORM_USER` → « Équipe AgencySuite » ;
  - `SYSTEM` → « Système ».
  - **Aucun identifiant technique affiché.**
- **Action** : libellé traduit pour chaque valeur connue, par exemple `USER_INVITED` → « Utilisateur invité » ou `MODULE_ENABLED` → « Module activé ». Une valeur inconnue affiche un libellé générique « Autre action », jamais le code brut.
- **Objet et détails** :
  - modules → noms traduits (`module.<CODE>.name`) ;
  - rôles → libellés de rôle ;
  - utilisateur visé → nom si on le connaît, sinon rien ;
  - autres valeurs ignorées. Jamais d'affichage générique du JSON.
- Pas de filtres dans ce lot (backlog).

### 3.5 Traductions

Nouvelles clés dans `public/i18n/{fr,ar,en}.json` :
- section et liens « Administration » ;
- libellés des deux écrans, des statuts, des descriptions de rôles ;
- **toutes** les actions d'audit (`audit.action.<ACTION>`) des lots 4 et 5 ;
- auteurs (Équipe AgencySuite, Système, Utilisateur de l'agence) ;
- confirmations, messages de succès et d'avertissement, message générique d'erreur.

Le test de parité doit rester vert. Ajoute un test qui vérifie qu'**une clé `audit.action.*` existe pour chaque valeur** de la liste des actions connues côté frontend. Cette liste est à aligner sur l'enum serveur `AuditAction` : relis-le.

## 4. Tests (Vitest)

| Fichier | Cas |
|---|---|
| `requires-permission.guard.spec.ts` | Permission présente → `true` ; absente → redirection vers l'accueil ; attend le chargement du profil |
| Menu (`app.spec.ts`) | Section Administration absente sans permission ; un seul lien avec `audit.read` seul ; deux liens avec les deux permissions |
| `users-api.spec.ts`, `audit-api.spec.ts` | URL, verbes, corps et paramètres de pagination |
| `admin-users.spec.ts` | Liste : libellés traduits, « (vous) » sans bouton Désactiver ; invitation valide → POST correct + message ; `invitationSent: false` → avertissement + bouton Renvoyer ; 409 → `detail` affiché et formulaire conservé ; validations (e-mail, 80 caractères, au moins un rôle) ; désactivation → confirmation puis POST ; `identityProviderSynced: false` → avertissement ; renvoi visible seulement pour INVITED |
| `admin-audit.spec.ts` | Libellés d'action traduits ; action inconnue → « Autre action » ; auteurs (agence avec nom, éditeur, système) sans identifiant affiché ; modules traduits dans les détails ; pagination (page suivante → `page=1`) |
| `i18n-keys.spec.ts` (étendu) | Parité ; une clé par action d'audit connue |
| RTL | Au moins un test en arabe : `dir="rtl"`, e-mail dans `<bdi>` |

## 5. Vérification

Depuis `frontend\` : `npm run build` et `npx ng test --watch=false` au vert. Le backend n'est pas modifié.

Contrôles manuels **par le développeur**, avec `claim-check` (MANAGER), dans son navigateur :

1. Le menu affiche « Administration », avec les deux liens.
2. Invitation d'un utilisateur fictif `@example.test` avec le rôle Commercial → message de succès ; l'e-mail arrive dans Mailpit ; la ligne apparaît « Invité ».
3. Modification de ses rôles (ajout d'Opérations) → la liste se met à jour.
4. Désactivation (avec confirmation) → statut « Désactivé » ; réactivation → « Invité » ou « Actif ».
5. Tentative de retirer son propre rôle de Gérant (seul gérant) → confirmation, puis le message `last-manager` du serveur.
6. Journal : les actions précédentes apparaissent avec des libellés lisibles, les noms des personnes, et aucun identifiant technique.
7. En arabe : les deux écrans sont en RTL, les e-mails ne sont pas inversés, et les dates sont en arabe.
8. Avec l'utilisateur Commercial invité (après qu'il a choisi son mot de passe) : pas de section Administration, et l'adresse `/administration/utilisateurs` tapée à la main renvoie vers l'accueil.

## 6. Hors périmètre

- Filtres et export du journal ; affichage de l'adresse IP (décision du juriste attendue).
- Écran du back-office éditeur.
- Personnalisation des permissions par agence (US-202), succursales et équipes (US-205).
- Fuseau horaire de l'agence (étape 5, paramétrage).

## 7. Compte rendu attendu

`revue/etape3-lot6-compte-rendu.md` et le diff :

```
git add -N frontend
git diff -- frontend > revue/etape3-lot6.diff
```

Contenu habituel : build et tests (avant et après), fichiers, écarts et pourquoi, points à valider (textes arabes, libellés des actions et des rôles), et une liste à cocher des contrôles manuels.

Message de commit proposé (sans ligne d'attribution) :

```
feat(frontend): administration screens for users and audit log (US-201)
```

**Ne commite pas** avant l'accord du développeur.
