# Étape 3, lot 3 : connexion dans l'interface Angular (OIDC)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> Références : ADR-08 (Keycloak, standard OIDC, fournisseur remplaçable), ADR-03 (Angular 22), lots 1 et 2 de l'étape 3 (jetons, `/api/v1/me`).
> Périmètre : **frontend** et réglages du realm local. **Aucune modification du backend n'est attendue** : si elle devient nécessaire, arrête-toi et explique pourquoi.

## 1. Objectif

1. L'application exige une connexion : sans session, l'utilisateur est redirigé vers l'écran de connexion Keycloak du realm `agencysuite`, dans la langue choisie (FR/AR/EN).
2. Le jeton d'accès est envoyé **uniquement** aux appels `/api/`.
3. Après connexion :
   - l'en-tête affiche le nom de l'utilisateur, le nom de l'agence et un bouton de déconnexion ;
   - le menu affiche les modules de l'agence.
4. Les cas d'échec ont un écran clair et traduit :
   - compte non rattaché à une agence ;
   - compte non activé ;
   - agence indisponible ;
   - service de connexion injoignable.
5. Les réglages de durée des jetons et des sessions sont fixés dans le realm.

## 2. Bibliothèque retenue

**`angular-oauth2-oidc`, ligne 22.x** (vérifié dans le chat projet le 24/11/2026 : la version 22.0.2 déclare Angular 22 en dépendance, flux « code + PKCE » activé par défaut).

Pourquoi elle plutôt que `keycloak-angular` (22.x, lui aussi compatible) : c'est une bibliothèque **OIDC générique**. Le frontend ne dépend donc pas de Keycloak, conformément à l'ADR-08 (« fournisseur remplaçable sans toucher au métier »). Elle ne sait rien de Keycloak et n'en a pas besoin : l'agence est lue par le backend.

- Vérifie la version exacte disponible avec `npm view angular-oauth2-oidc versions` avant l'installation, et installe la dernière 22.x. N'invente pas d'option : vérifie chaque option de configuration dans le README ou les types de la version installée.
- Le paquet `angular-oauth2-oidc-jwks` n'est **pas** nécessaire : on ne valide pas la signature du jeton d'accès côté navigateur (c'est le rôle du backend).
- Si la version 22.x ne s'installe pas proprement avec Angular 22.3 (conflit de dépendances, erreurs de compilation), arrête-toi et décris le problème.

## 3. Livrables (sous `frontend/src/app/`)

### 3.1 Configuration

`core/auth/auth.config.ts` :

- issuer : `http://localhost:8180/realms/agencysuite` ; client : `agencysuite-frontend` ; `responseType: 'code'` ; scope `openid profile email organization` ;
- `redirectUri` : origine de l'application ; `postLogoutRedirectUri` : origine de l'application ;
- pas d'option qui désactive HTTPS pour un autre hôte que `localhost` ;
- commentaire `TODO(etape-12)` : configuration lue au démarrage (fichier servi avec l'application), pour la recette et la production.

Stockage des jetons : **`sessionStorage`** (comportement par défaut de la bibliothèque, à vérifier), jamais `localStorage`. Le jeton disparaît à la fermeture de l'onglet. C'est un compromis assumé : un stockage uniquement en mémoire casserait le retour de connexion (PKCE) et obligerait à se reconnecter à chaque rechargement. Les protections complémentaires sont l'absence d'`innerHTML` (règle existante), des jetons de courte durée (section 5) et une politique CSP à l'étape 12 (dette notée).

### 3.2 Service d'authentification

`core/auth/auth.service.ts` (`providedIn: 'root'`, à base de signals) :

- `state: Signal<'initializing' | 'authenticated' | 'unauthenticated' | 'unavailable'>` ;
- `init(): Promise<void>` :
  - charge le document de découverte et traite le retour de connexion (code dans l'URL) ;
  - active le renouvellement automatique du jeton par jeton de rafraîchissement ;
  - **ne rejette jamais** : si Keycloak est injoignable, l'état passe à `unavailable` ;
- `login(): void` : lance le flux de connexion en passant la langue courante (`ui_locales` = code de `LanguageService`), pour que l'écran Keycloak soit dans la bonne langue ;
- `logout(): void` : vide les états applicatifs (modules, profil), puis déconnexion OIDC avec retour sur l'application ;
- `accessToken(): string | null`.

Démarrage : `init()` est appelé par `provideAppInitializer`, **après** l'initialiseur de langue. Exception assumée à la règle « démarrage non bloquant » : il faut traiter le retour de connexion avant que le routeur ne s'exécute. `init()` ne rejette jamais, donc une panne de Keycloak n'empêche pas l'affichage.

### 3.3 Intercepteur du jeton

`core/http/auth-token.interceptor.ts`, sur le modèle de `acceptLanguageInterceptor` :

- ajoute `Authorization: Bearer <jeton>` **uniquement** si l'URL commence par `/api/` et qu'un jeton existe ;
- **n'utilise pas** l'option d'envoi automatique du jeton de la bibliothèque : on garde notre règle, testée, en un seul endroit ;
- sur une réponse **401** d'un appel `/api/` : appelle `login()` **une seule fois** (pas de boucle si plusieurs requêtes échouent en même temps), puis propage l'erreur ;
- ne dépend d'aucun service qui injecte `HttpClient` à sa construction (leçon de la dépendance circulaire NG0200) ; ajoute un test qui monte la vraie configuration, comme `app.config.spec.ts`.

### 3.4 Profil de l'utilisateur courant

- `core/api/me-api.ts` : ajouter `me(): Observable<MeResponse>` (`GET /api/v1/me`), type aligné sur la réponse du lot 2 : `id`, `email`, `fullName`, `agency {id, name}`, `roles`, `permissions`.
- `core/auth/current-user.service.ts` : signals `profile` et `state` (`idle | loading | ready | no-agency | not-activated | agency-unavailable | error`). Correspondance des erreurs, par le `type` problem+json :

| Réponse | État |
|---|---|
| 403 `agency-scope-required` | `no-agency` |
| 403 `access-denied` | `not-activated` |
| 403 `agency-unavailable` | `agency-unavailable` |
| autre | `error` |

  Fermé par défaut, comme `ModuleAccessService`.
- `has(permission: string): boolean` lit le signal. Il servira à masquer les actions dans l'interface (confort seulement : le serveur contrôle).
- Après `authenticated` : charger `me` et rafraîchir `ModuleAccessService` (`refresh()`).

### 3.5 Garde et routes

- `core/auth/auth.guard.ts` (`CanMatchFn`) :
  - attend `init()` ;
  - si `authenticated`, autorise ;
  - si `unauthenticated`, appelle `login()` et refuse ;
  - si `unavailable`, redirige vers `/connexion-indisponible`.
- Appliqué à toutes les routes de l'application (accueil et modules), **avant** `requiresModule`.
- Nouvelles pages publiques (sans garde), traduites FR/AR/EN, RTL correct :
  - `connexion-indisponible` : « Le service de connexion est momentanément indisponible », avec un bouton « Réessayer » ;
  - `acces-refuse` : un seul composant, le texte dépend de l'état `no-agency`, `not-activated` ou `agency-unavailable`. Bouton « Se déconnecter ».
- Quand `CurrentUserService` passe dans l'un de ces trois états, la coquille redirige vers `acces-refuse`.
- `**` reste la dernière route.

### 3.6 Coquille

- En-tête : `<bdi>` autour du nom de l'utilisateur et du nom de l'agence (noms latins dans une interface arabe), et un bouton « Se déconnecter ».
- **Supprime** le bandeau « aucune agence connectée : la connexion arrive à l'étape 3 » (`TODO(etape-3)`) et sa clé de traduction. Le cas est maintenant traité par la page `acces-refuse`.
- Pendant `initializing` : un indicateur de chargement discret, sans texte en dur.

### 3.7 Nettoyage signalé aux lots 1 et 2

`core/modules/module-access.service.spec.ts` : remplacer le type obsolète `backoffice-disabled` par un type existant (`access-denied`).

### 3.8 Traductions

Nouvelles clés dans `public/i18n/{fr,ar,en}.json` : en-tête (`auth.logout`, `auth.loading`), les deux pages, les quatre messages de refus. Le test de parité doit rester vert. Textes arabes à signaler pour relecture.

## 4. Tests (Vitest)

| Fichier | Cas |
|---|---|
| `auth-token.interceptor.spec.ts` | Jeton ajouté sur `/api/v1/me` ; **absent** sur `/i18n/fr.json`, une URL externe, `/apix/y` ; aucun en-tête sans jeton ; un 401 sur `/api/` déclenche `login()` ; deux 401 simultanés → **un seul** `login()` |
| `auth.service.spec.ts` | Bibliothèque simulée : découverte OK + connecté → `authenticated` ; non connecté → `unauthenticated` ; découverte en échec → `unavailable` et `init()` ne rejette pas ; `login()` transmet `ui_locales` de la langue courante ; `logout()` vide les états des modules et du profil |
| `current-user.service.spec.ts` | 200 → `ready` et `has()` correct ; les trois 403 → les trois états ; 500 → `error` ; un seul appel pour deux `load()` simultanés |
| `auth.guard.spec.ts` | Connecté → `true` ; non connecté → `login()` et refus ; indisponible → `/connexion-indisponible` |
| `app.spec.ts` (adapté) | En-tête avec le nom de l'utilisateur et de l'agence dans `<bdi>` ; bouton de déconnexion ; plus de bandeau « étape 3 » |
| `app.config.spec.ts` (adapté) | La vraie configuration démarre sans NG0200, la langue reste chargée ; le jeton simulé part sur `/api/` |
| Pages `connexion-indisponible` et `acces-refuse` | Bon texte selon l'état ; aucune valeur venue de l'URL affichée |
| Tests existants | Restent verts (parité des clés comprise) |

## 5. Realm local (`deploy/keycloak/realms/agencysuite-realm.json`)

Rends explicites les durées. Valeurs proposées, **à valider** :

| Réglage Keycloak | Valeur | Effet |
|---|---|---|
| `accessTokenLifespan` | 300 (5 min) | Un utilisateur désactivé dans Keycloak perd l'accès en 5 minutes au plus |
| `ssoSessionIdleTimeout` | 1800 (30 min) | Déconnexion après 30 minutes d'inactivité |
| `ssoSessionMaxLifespan` | 36000 (10 h) | Une journée de travail, puis reconnexion |
| `rememberMe` | `false` | Pas de session prolongée |

**Ne réimporte pas le realm** : la réimportation efface l'Organization et l'utilisateur d'essai. Applique les mêmes valeurs dans la console (*Realm settings* > *Sessions* et *Tokens*) et mets-les dans le fichier JSON pour les nouvelles installations. Vérifie dans le jeton (`exp` − `iat`) que la durée de 5 minutes est appliquée.

## 6. Vérification

Depuis `frontend\` : `npm run build` et `npx ng test --watch=false` au vert. Le backend n'est pas modifié, mais lance `.\mvnw.cmd verify` une fois à la fin pour confirmer qu'il reste vert.

Contrôles manuels à faire **par le développeur** dans un navigateur, avec la pile locale et l'utilisateur d'essai `claim-check`, dont le mot de passe est à redéfinir dans la console Keycloak. Décris-les dans le compte rendu sous forme de liste à cocher, et fais toi-même ceux que tu peux faire sans navigateur interactif.

1. `http://localhost:4200` sans session → écran de connexion Keycloak ; en arabe, l'écran est en arabe (RTL).
2. Connexion → retour dans l'application ; en-tête avec le nom et l'agence ; menu TRAVEL et MICE ; outils du navigateur : le jeton part vers `/api/`, pas vers `/i18n/`.
3. Rechargement de la page → toujours connecté (jeton en `sessionStorage`, pas dans `localStorage`).
4. « Se déconnecter » → retour à l'écran de connexion ; un nouveau rechargement demande à se reconnecter.
5. Utilisateur passé en DISABLED par SQL → page « compte non activé ».
6. Agence passée en SUSPENDED → page « agence indisponible » ; remettre en TRIAL ensuite.
7. Keycloak arrêté (`docker compose stop keycloak`) puis rechargement → page « service de connexion indisponible », sans écran blanc.

Contrôle du back-office laissé en suspens au lot 2, à faire par le développeur :

8. Créer dans le realm `platform` un utilisateur fictif, configurer sa double authentification avec une application TOTP sur téléphone (via `http://localhost:8180/realms/platform/account`), insérer son `platform_user` (procédure du README), obtenir un jeton avec `agencysuite-dev-cli` et le code TOTP, puis appeler `GET /api/v1/backoffice/me` → 200. **Vérifier le nom du paramètre** du code (`totp` dans le README actuel) et corriger le README si nécessaire.

## 7. Hors périmètre

- Interface du back-office éditeur (realm `platform`) : pas dans cette étape.
- Masquage des boutons selon les permissions : la méthode `has()` est prête, les écrans métier viendront aux étapes suivantes.
- Configuration lue au démarrage et politique CSP : étape 12.
- Journal d'audit des connexions : lot 4.

## 8. Compte rendu attendu

`revue/etape3-lot3-compte-rendu.md` et le diff :

```
git add -N frontend deploy
git diff -- frontend deploy > revue/etape3-lot3.diff
```

Contenu :

1. version installée de la bibliothèque et options utilisées, avec leur source (README ou types) ;
2. build et tests (avant et après) ;
3. fichiers ;
4. écarts et pourquoi ;
5. points à valider (durées de session, textes arabes, stockage des jetons) ;
6. liste à cocher des contrôles manuels 1 à 8, en indiquant ceux que tu as pu faire.

Message de commit proposé (sans ligne d'attribution) :

```
feat(frontend): OIDC login with Keycloak, token interceptor, current user and access pages
```

**Ne commite pas** avant l'accord du développeur.
