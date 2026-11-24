# Étape 2, lot 5 : accès aux modules côté interface (Angular)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User stories : US-102 (un module non souscrit est invisible et inaccessible).
> Backend : **aucune modification attendue**. Si le plan oblige à toucher `backend/`, arrête-toi et explique pourquoi.

## 1. Objectif

L'interface lit les modules actifs de l'agence (`GET /api/v1/me/modules`, livré au lot 3) et :

- n'affiche dans le menu que les modules souscrits et actifs ;
- bloque l'accès aux routes d'un module non actif (garde de route) et affiche une page « module non activé » ;
- reste utilisable en FR, AR (droite à gauche) et EN.

**Rappel de sécurité :** le garde Angular sert le confort de l'utilisateur. La vraie barrière est côté serveur (`@RequiresModule` et RLS). Ne jamais présenter le garde Angular comme une protection, ni dans le code ni dans les commentaires.

## 2. Contrainte de ce lot : pas encore d'authentification

Tant que Keycloak n'est pas branché (étape 3), aucune requête réelle n'a de périmètre agence. `/api/v1/me/modules` répond donc **403 `urn:agencysuite:problem:agency-scope-required`** en local.

Décision : **n'ajoute aucun en-tête ni filtre de développement côté backend** pour simuler une agence. Ce serait un moyen de se faire passer pour n'importe quelle agence si on l'oubliait activé.

Conséquences :

- les comportements sont vérifiés par des tests Vitest avec `HttpTestingController` ;
- en local, l'interface affiche un menu sans module et un bandeau « aucune agence connectée » ;
- la démo de bout en bout se fera à l'étape 3.

## 3. Livrables (sous `frontend/src/app/`)

### 3.1 Codes des modules

`core/modules/module-code.ts`

- `export const MODULE_CODES = ['TRAVEL', 'TICKETING', 'GIFTBOX', 'TRANSPORT', 'VISA', 'MICE', 'PORTAL', 'OMRA_HAJJ', 'INBOUND', 'FLEET'] as const;`
- `export type ModuleCode = (typeof MODULE_CODES)[number];`
- `export function isModuleCode(value: unknown): value is ModuleCode`
- Même ordre que l'enum backend `ModuleCode` (ordre du catalogue).
- Commentaire : liste à garder alignée sur le backend. Dette notée : la générer depuis l'OpenAPI plus tard.

### 3.2 Client API

`core/api/me-api.ts`

- `MeApi.myModules(): Observable<MyModulesResponse>` sur `GET /api/v1/me/modules`.
- `interface MyModulesResponse { readonly modules: readonly string[] }`. Le type reste `string` en entrée : le filtrage se fait dans le service.

### 3.3 Service d'accès aux modules

`core/modules/module-access.service.ts`, `providedIn: 'root'`, fondé sur les signals :

- `state: Signal<ModuleAccessState>` avec `type ModuleAccessState = 'idle' | 'loading' | 'ready' | 'no-agency' | 'error'`.
- `enabled: Signal<ReadonlySet<ModuleCode>>`.
- `has(code: ModuleCode): boolean` lit le signal, donc se met à jour dans les templates.
- `load(): Promise<void>` :
  - un seul appel HTTP même si on l'appelle plusieurs fois en parallèle (partage de la promesse en cours) ;
  - pas de nouvel appel si l'état est déjà `ready` ou `no-agency`.
- `refresh(): Promise<void>` force un nouvel appel. Il servira après la connexion (étape 3).
- Réponse 200 : on ne garde que les codes connus (`isModuleCode`). Les codes inconnus sont ignorés sans erreur, ce qui couvre un nouveau module ajouté au backend avant l'interface.
- 403 avec `type` = `urn:agencysuite:problem:agency-scope-required` : état `no-agency`, ensemble vide.
- Toute autre erreur (réseau, 500, autre 403) : état `error`, ensemble vide. **Fermé par défaut** : en cas de doute, aucun module n'est affiché.
- Rien n'est écrit dans `localStorage` : l'état du service vit seulement en mémoire.
- Aucune donnée de la réponse dans `console.*`.

### 3.4 Garde de route

`core/modules/requires-module.guard.ts`

- `export function requiresModule(code: ModuleCode): CanMatchFn`.
- On utilise `CanMatch` plutôt que `CanActivate` : le code du module, chargé en différé, n'est même pas téléchargé si le module n'est pas actif.
- Le garde attend `load()`, puis :
  - renvoie `true` si `has(code)` ;
  - sinon renvoie l'`UrlTree` `/module-non-active?module=<CODE>`.
- Le code dans l'URL est un code technique, pas une donnée personnelle.

### 3.5 Routes et pages

`app.routes.ts`

- Une route par module, générée depuis `MODULE_CODES` :
  - chemin en minuscules avec tirets (`travel`, `ticketing`, `giftbox`, `transport`, `visa`, `mice`, `portal`, `omra-hajj`, `inbound`, `fleet`) ;
  - `canMatch: [requiresModule(code)]`, `data: { module: code }` ;
  - `loadComponent` vers un composant générique de remplacement.
- `module-non-active` : page « module non activé ».
- Garder `''` (accueil) et `**` → `''`. **`**` doit rester la dernière route.**

`features/module-placeholder/` (`module-placeholder.ts/.html/.scss`)

- Affiche le nom traduit du module (`module.<CODE>.name`) et le texte « Ce module sera construit dans une prochaine étape. »
- Il sera remplacé par les vrais modules : c'est temporaire. Ajoute un commentaire `TODO(module)`.

`features/module-not-enabled/` (`module-not-enabled.ts/.html/.scss`)

- Lit `module` dans les paramètres de requête.
- Si `isModuleCode(module)` : « Le module <nom traduit> n'est pas activé pour votre agence. Contactez votre administrateur ou l'éditeur pour le souscrire. »
- Sinon : texte générique sans afficher la valeur reçue (pas de réinjection d'une valeur venue de l'URL).
- Lien de retour à l'accueil.

### 3.6 Menu

Dans la coquille (`app.html/.ts/.scss`) :

- une barre de navigation `<nav>` sous l'en-tête, avec `aria-label` traduit ;
- un lien par module, dans l'ordre de `MODULE_CODES`, affiché seulement si `has(code)`, avec `routerLink` et `routerLinkActive` ;
- si l'état est `no-agency`, un bandeau discret « Aucune agence connectée : la connexion arrive à l'étape 3. » ;
- si l'état est `error`, un bandeau « Impossible de charger vos modules. » avec un bouton « Réessayer » qui appelle `refresh()` ;
- **en RTL**, uniquement des propriétés CSS logiques (`margin-inline-start`, `padding-inline`, `inset-inline-*`), jamais `left` ou `right`. Le menu doit s'inverser tout seul en arabe.

Chargement : appeler `ModuleAccessService.load()` au démarrage, avec `provideAppInitializer` ou dans la coquille. **Le démarrage ne doit pas bloquer** si l'API ne répond pas : l'erreur passe dans l'état `error`, jamais dans un rejet du démarrage.

### 3.7 Traductions

Dans `public/i18n/fr.json`, `ar.json` et `en.json` :

- `module.<CODE>.name` pour les 10 modules. Les noms des modules vivent dans les fichiers de traduction, pas en base (décision de l'étape 2).
- `nav.label`, `modules.noAgency`, `modules.loadError`, `modules.retry`, `modules.placeholder`, `moduleNotEnabled.title`, `moduleNotEnabled.detail` (paramètre `module`), `moduleNotEnabled.generic`, `moduleNotEnabled.back`.

Noms proposés (les textes arabes sont **à faire relire par un locuteur natif**) :

| Code | FR | EN | AR |
|---|---|---|---|
| TRAVEL | Voyages | Travel | الرحلات |
| TICKETING | Billetterie | Ticketing | التذاكر |
| GIFTBOX | Concepts cadeaux | Gift concepts | الهدايا |
| TRANSPORT | Transport | Transport | النقل |
| VISA | Visas | Visas | التأشيرات |
| MICE | MICE | MICE | المؤتمرات والفعاليات (MICE) |
| PORTAL | Portail client | Client portal | بوابة العملاء |
| OMRA_HAJJ | Omra et Hajj | Umrah & Hajj | العمرة والحج |
| INBOUND | Réceptif | Inbound | الاستقبال السياحي |
| FLEET | Flotte et chauffeurs | Fleet & drivers | الأسطول والسائقون |

Règle bidi : dans un texte arabe, un code ou un sigle latin (`MICE`) reste isolé de gauche à droite, avec `<bdi>` ou `dir="ltr"` sur un `<span>` dans le template. Ne pas l'imposer dans le JSON.

## 4. Tests (Vitest, `npx ng test --watch=false`)

| Fichier | Cas à couvrir |
|---|---|
| `module-code.spec.ts` | `isModuleCode` accepte les 10 codes et refuse `'travel'`, `''`, `null` et `'UNKNOWN'`. |
| `module-access.service.spec.ts` | 200 → `ready` avec les bons modules ; un code inconnu est ignoré ; 403 `agency-scope-required` → `no-agency` ; autre 403 → `error` ; 500 → `error` et ensemble vide ; deux `load()` en parallèle → **une seule** requête ; `load()` après `ready` → aucune requête ; `refresh()` → nouvelle requête. |
| `requires-module.guard.spec.ts` | module actif → `true` ; inactif → `UrlTree` vers `/module-non-active?module=TRAVEL` ; le garde attend la fin du chargement ; état `error` → refus. |
| `app.spec.ts` (coquille) | seuls les modules actifs sont dans le menu, dans l'ordre du catalogue ; bandeau `no-agency` ; bandeau `error` avec bouton Réessayer qui relance la requête. |
| `module-not-enabled.spec.ts` | code valide → nom traduit ; code invalide → texte générique, et la valeur reçue **n'apparaît pas** dans le DOM. |
| `i18n-keys.spec.ts` | **fr, ar et en ont exactement le même jeu de clés** (test de parité, qui protégera toutes les étapes suivantes) ; les 10 `module.<CODE>.name` existent dans les 3 langues. |

Les tests existants (`language.service.spec.ts`, `app.spec.ts` actuel) restent verts. Adapte `app.spec.ts` si la coquille change, sans supprimer ce qu'il vérifiait déjà.

## 5. Vérification

Depuis `frontend\` (jamais depuis la racine) :

```
npm run build
npx ng test --watch=false
```

Les deux doivent être verts. Ensuite, contrôle manuel avec le backend lancé (`npm start`, puis http://localhost:4200) :

1. le bandeau « aucune agence connectée » s'affiche et le menu ne montre aucun module ;
2. http://localhost:4200/travel redirige vers la page « module non activé » avec le nom « Voyages » ;
3. en arabe, la page est en RTL, le bandeau et le lien de retour sont alignés à droite, et `MICE` n'est pas inversé ;
4. avec le backend arrêté, le bandeau d'erreur apparaît et l'application ne plante pas.

## 6. Hors périmètre

- Authentification, Keycloak, `refresh()` après connexion : étape 3.
- Vrais écrans des modules : étapes suivantes.
- Back-office éditeur en Angular : pas dans ce lot.
- Génération du client TypeScript depuis l'OpenAPI : dette notée.

## 7. Compte rendu attendu

Écris-le dans `revue/lot5-compte-rendu.md` (dossier ignoré par Git), et génère le diff :

```
git add -N frontend
git diff -- frontend > revue/lot5.diff
```

Contenu du compte rendu :

1. résultat du build et des tests (nombre de tests avant et après) ;
2. fichiers créés ou modifiés ;
3. écarts par rapport au plan, et pourquoi ;
4. points à valider (textes arabes, choix d'interface) ;
5. résultat des 4 contrôles manuels de la section 5, ou ceux que tu n'as pas pu faire.

Message de commit proposé (sans ligne d'attribution) :

```
feat(frontend): module access service, route guard and menu filtering (US-102)
```

**Ne commite pas** avant l'accord du développeur.
