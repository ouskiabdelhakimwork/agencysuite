# Environnement local (docker compose)

Services d'infrastructure pour développer AgencySuite sur un poste. Usage **local uniquement** : ports publiés sur `127.0.0.1`, mots de passe de développement, stockage sans authentification.

| Service | Rôle | Adresse locale | Profil |
|---|---|---|---|
| PostgreSQL 18 | Bases `agencysuite` et `keycloak` | `localhost:5432` | base |
| Keycloak 26.6 | Identité (realms `agencysuite` et `platform`) | http://localhost:8180 | base |
| SeaweedFS | Stockage compatible S3 (remplace MinIO) | http://localhost:8333 | base |
| RabbitMQ 4 | File de messages | console http://localhost:15672 | base |
| Mailpit | Capture des e-mails envoyés | http://localhost:8025 | base |
| ClamAV | Antivirus | `localhost:3310` | full |
| Gotenberg 8 | Rendu PDF par Chromium | http://localhost:3000 | full |

## Démarrer

```powershell
cd deploy
Copy-Item .env.example .env      # une seule fois, puis changer les mots de passe dans .env
docker compose up -d             # pile de base (~2 à 3 Go de mémoire)
docker compose --profile full up -d   # pile complète (~5 Go), ClamAV met plusieurs minutes à démarrer
docker compose ps
```

Arrêter : `docker compose stop`. Tout effacer, données comprises : `docker compose down -v`.

## Vérifications du lot 3

1. `docker compose ps` : tous les services à l'état `running` (PostgreSQL `healthy`).
2. **Keycloak** : http://localhost:8180, connexion avec `KC_ADMIN_USER` / `KC_ADMIN_PASSWORD` de `.env`. Les realms `agencysuite` et `platform` existent.
3. **Keycloak en arabe** :
   - dans le realm `agencysuite`, créer un utilisateur de test (onglet Users), lui donner un mot de passe (onglet Credentials) ;
   - ouvrir http://localhost:8180/realms/agencysuite/account, choisir **العربية** dans le sélecteur de langue de la page de connexion ;
   - vérifier : textes en arabe, page affichée de droite à gauche.
4. **E-mails** : sur la page de connexion, « Mot de passe oublié » avec l'e-mail de l'utilisateur de test, puis vérifier que l'e-mail arrive dans Mailpit (http://localhost:8025).
5. **RabbitMQ** : http://localhost:15672 avec `RABBITMQ_USER` / `RABBITMQ_PASSWORD`.
6. **SeaweedFS** : `curl.exe http://localhost:8333` renvoie une réponse XML (liste de buckets vide).
7. **PostgreSQL** : `docker compose exec postgres psql -U agencysuite -c "\l"` liste `agencysuite` et `keycloak`.
8. **Gotenberg** (profil full) : rendu de la facture arabe du banc d'essai :
   ```powershell
   Copy-Item ..\tools\pdf-poc\src\main\resources\invoice-ar.html $env:TEMP\index.html
   curl.exe -s -o $env:TEMP\gotenberg-ar.pdf -F "files=@$env:TEMP\index.html" http://localhost:3000/forms/chromium/convert/html
   Start-Process $env:TEMP\gotenberg-ar.pdf
   ```
   Le PDF doit être identique à `chromium-ar.pdf` du banc d'essai (lettres liées, tableau RTL, montants lisibles).

## Keycloak : jetons pour le backend (étape 3)

Le backend n'accepte que des jetons signés par Keycloak :

| Chemin | Realm attendu | Périmètre ouvert |
|---|---|---|
| `/api/v1/backoffice/**` | `platform` | plateforme (toutes les agences), si `platform_user` ACTIVE |
| reste de `/api/**` (sauf `GET /api/v1/platform/info`) | `agencysuite` | agence de l'utilisateur, si `user_account` ACTIVE |

L'agence vient du claim `organization` du jeton : c'est la liste des **alias** des Organizations Keycloak dont l'utilisateur est membre, par exemple `"organization": ["<uuid-agence>"]`. L'alias d'une Organization est l'UUID de l'agence. Il est fixé à la création et ne peut plus être modifié.

Les jetons doivent contenir l'audience `agencysuite-api` (mapper d'audience des clients `agencysuite-frontend`, `agencysuite-backoffice` et `agencysuite-dev-cli`).

> **Client `agencysuite-dev-cli` : DÉVELOPPEMENT LOCAL UNIQUEMENT.** Il autorise l'obtention d'un jeton par identifiant et mot de passe (*direct access grants*) pour les tests avec `curl`. Il **n'existe jamais** dans les realms de recette ni de production : ne pas le reprendre dans leurs fichiers de configuration.

Les fichiers de realm versionnés ne contiennent **aucun utilisateur ni mot de passe**. Les utilisateurs fictifs sont créés à la main (procédure ci-dessous).

### Réimporter les realms après une modification des fichiers

Keycloak n'importe pas un realm qui existe déjà (stratégie `IGNORE_EXISTING`). Pour appliquer une modification de `keycloak/realms/*.json` :

1. console http://localhost:8180 (compte `KC_ADMIN_USER`), choisir le realm, *Realm settings* > menu *Action* > *Delete* ; faire de même pour l'autre realm si besoin ;
2. `docker compose restart keycloak` ;
3. `docker compose logs keycloak --tail 20` doit afficher `Realm 'agencysuite' imported` et `Realm 'platform' imported`.

La suppression d'un realm efface ses utilisateurs et ses Organizations : il faut les recréer.

### Créer une agence d'essai, son Organization et un utilisateur fictif

1. Disposer de l'UUID d'une agence existante dans la base locale :
   ```powershell
   docker compose exec postgres psql -U agencysuite -d agencysuite -c "select id, subdomain, status from agency"
   ```
   (s'il n'y en a pas, en créer une avec `POST /api/v1/backoffice/agencies` et un jeton du realm `platform`).
2. Realm `agencysuite` > *Organizations* > *Create organization* : un nom libre et, dans **Alias**, l'UUID de l'agence (en minuscules). Un domaine n'est pas nécessaire.
3. Realm `agencysuite` > *Users* > *Create new user* : identifiant fictif (ex. `essai-agence`), e-mail fictif en `@example.test`, *Email verified* activé. Onglet *Credentials* : mot de passe de 12 caractères au moins, *Temporary* désactivé.
4. Retour à l'Organization > *Members* > *Add member* > choisir l'utilisateur.

Un utilisateur ne doit appartenir qu'à **une** Organization : avec zéro ou plusieurs Organizations, le backend refuse l'accès (403).

### Déclarer l'utilisateur dans la base (provisoire, étape 3 lot 2)

Un jeton valide ne suffit pas : l'utilisateur doit **exister et être actif** dans la base de l'application (table `user_account` pour une agence, `platform_user` pour l'éditeur), sinon le backend répond 403 `access-denied`. Les droits viennent de ses rôles (`MANAGER`, `SALES`, `OPERATIONS`, `ACCOUNTANT`).

> **Procédure provisoire.** Le lot 5 créera les comptes par invitation ; en attendant, les lignes sont insérées à la main, avec le compte propriétaire des tables (`POSTGRES_USER`), qui n'est pas soumis à la Row Level Security. Aucun identifiant réel dans ce fichier : remplacer les valeurs entre chevrons.

1. Obtenir le `sub` de l'utilisateur : console Keycloak > realm concerné > *Users* > l'utilisateur > champ **ID** (UUID).
2. Utilisateur d'agence (realm `agencysuite`), actif et gérant :
   ```powershell
   docker compose exec postgres psql -U agencysuite -d agencysuite -c "
     WITH u AS (
       INSERT INTO user_account (id, agency_id, external_idp_id, email, full_name, status, created_at, updated_at, version)
       VALUES (uuidv7(), '<uuid-agence>', '<sub-keycloak>', '<e-mail fictif>@example.test', '<nom fictif>', 'ACTIVE', now(), now(), 0)
       RETURNING id, agency_id)
     INSERT INTO user_role (id, agency_id, user_id, role_code, created_at, updated_at, version)
     SELECT uuidv7(), agency_id, id, 'MANAGER', now(), now(), 0 FROM u;"
   ```
   L'agence doit être celle de l'Organization de l'utilisateur (même UUID que l'alias).
3. Premier membre de l'éditeur (realm `platform`), administrateur :
   ```powershell
   docker compose exec postgres psql -U agencysuite -d agencysuite -c "
     INSERT INTO platform_user (id, external_idp_id, email, full_name, role, status, created_at, updated_at, version)
     VALUES (uuidv7(), '<sub-keycloak-platform>', '<e-mail fictif>@example.test', '<nom fictif>', 'ADMIN', 'ACTIVE', now(), now(), 0);"
   ```
4. Vérifier : `GET /api/v1/me` (jeton `agencysuite`) ou `GET /api/v1/backoffice/me` (jeton `platform`) répond 200.

Désactiver un utilisateur : `UPDATE user_account SET status = 'DISABLED' WHERE external_idp_id = '<sub>';` (effet immédiat, à la requête suivante).

### Obtenir un jeton et appeler l'API (PowerShell)

```powershell
$token = (curl.exe -s -X POST http://localhost:8180/realms/agencysuite/protocol/openid-connect/token `
    -d grant_type=password -d client_id=agencysuite-dev-cli -d scope=openid `
    --data-urlencode "username=essai-agence" --data-urlencode "password=<mot de passe>" | ConvertFrom-Json).access_token
curl.exe -i http://localhost:8080/api/v1/me/modules -H "Authorization: Bearer $token"
```

Ne jamais coller un jeton dans un fichier versionné, un ticket ou un journal.

Pour le realm `platform`, la double authentification est obligatoire : l'utilisateur doit d'abord configurer son application TOTP en se connectant une fois sur http://localhost:8180/realms/platform/account, puis ajouter `-d totp=<code à 6 chiffres>` à la requête de jeton.

## Points de vigilance

- Le dossier `deploy/postgres/init` n'est lu qu'au **premier** démarrage de PostgreSQL. Pour rejouer l'initialisation : `docker compose down -v`.
- Les images `latest` (SeaweedFS, Mailpit) sont à figer sur une version précise une fois l'environnement validé.
- Rancher Desktop : si la mémoire manque, augmenter la mémoire allouée à WSL ou n'utiliser que la pile de base.
