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

## Points de vigilance

- Le dossier `deploy/postgres/init` n'est lu qu'au **premier** démarrage de PostgreSQL. Pour rejouer l'initialisation : `docker compose down -v`.
- Les images `latest` (SeaweedFS, Mailpit) sont à figer sur une version précise une fois l'environnement validé.
- Rancher Desktop : si la mémoire manque, augmenter la mémoire allouée à WSL ou n'utiliser que la pile de base.
