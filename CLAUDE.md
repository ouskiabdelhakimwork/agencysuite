# Instructions pour Claude Code — AgencySuite

Réponds en **français**. Code, identifiants, noms de tables et d'API en **anglais** ; textes d'interface via fichiers de traduction ; documentation en français.

## Contexte

SaaS multi-agences (multi-tenant) de gestion d'agence de voyage et MICE au Maroc : CRM, devis, dossiers, facturation, fichiers, OCR passeports/CIN, import/export Excel, modules activables facturés séparément. Interface FR/AR (droite à gauche)/EN. Hébergement au Maroc. Nom de code technique : AgencySuite (`ma.agencysuite`).

## Organisation du travail (duo)

- Les décisions d'architecture, le découpage en étapes et les plans de lots sont préparés **en dehors** de ce dépôt (projet de cadrage), puis déposés dans `docs/plans/`.
- Ton rôle : **implémenter le plan du lot en cours**, lancer les builds et tests, corriger jusqu'au vert, puis rendre compte.
- **Ne t'écarte pas du plan.** Si une contrainte technique impose un écart (API qui n'existe pas, conflit avec une règle ci-dessous), arrête-toi et explique le problème au lieu d'improviser une autre architecture.
- Ne commite et ne pousse **jamais** sans l'accord explicite du développeur.
- À la fin d'un lot, fournis : fichiers créés/modifiés, résultat de `mvnw verify` (nombre de tests), écarts éventuels par rapport au plan, points à faire valider.

## Environnement (poste Windows sans droits administrateur)

- PowerShell. JDK 25 via `JAVA_HOME` (sinon `. .\env.ps1` à la racine). Docker via **Rancher Desktop** (doit être démarré pour les tests Testcontainers).
- Commandes **toujours depuis le bon dossier** :
  - `backend\` : `.\mvnw.cmd verify` (build complet + tests), `.\mvnw.cmd -Dtest=NomDuTest test`, `.\mvnw.cmd spring-boot:run`
  - `frontend\` : `npm start`, `npm run build`, `npx ng test --watch=false`
  - `deploy\` : `docker compose up -d`, `docker compose ps`, `docker compose logs <service> --tail 50`
- Port 8080 parfois occupé : vérifier avec `netstat -ano | findstr :8080`.
- Erreur « zip END header not found » sur un `.jar` : téléchargement Maven tronqué ; supprimer le dossier de ce jar dans `%USERPROFILE%\.m2\repository` et relancer.

## Stack

Java 25, Spring Boot 4.1 (Maven), Spring Modulith 2.1, Hibernate 7, PostgreSQL 18, Flyway, Testcontainers 2, springdoc 3.1 ; Angular 22, ngx-translate 18, Vitest ; Keycloak 26 (étape 3), RabbitMQ, SeaweedFS (S3), Gotenberg (PDF Chromium), ClamAV, Mailpit en local (`deploy/docker-compose.yml`). CI GitHub Actions (`.github/workflows/`).

## Règles d'architecture

- **Monolithe modulaire** : un paquet par module sous `ma.agencysuite` (`platform`, `iam`, `settings`, `crm`, `supplier`, `quote`, `booking`, `billing`, `files`, `access`, `ocr`, `dataexchange`, `search`, `reporting`, `travel`, `ticketing`, `giftbox`…). `shared` = noyau partagé ouvert (Money, AgencyId, langues, persistance, tenant).
- Chaque module déclare ses dépendances autorisées dans `package-info.java` (`@ApplicationModule(allowedDependencies = …)`). Un module n'utilise que l'API publique (`<Module>Api` à la racine du paquet) ou les événements d'un autre module, jamais ses entités ni repositories. `ModularityTests` doit rester vert.
- Structure d'un module : `api/` (contrôleurs + DTO records, aucune logique métier), `application/` (cas d'usage, transactions), `domain/` (entités, règles), `infrastructure/` (repositories, clients externes).
- Un module n'est créé dans le code qu'à l'étape où il est développé.

## Multi-tenant (règle absolue)

- Toute entité métier appartenant à une agence **hérite de `AgencyScopedEntity`** (`@TenantId` : Hibernate remplit et filtre `agency_id`). Le code ne manipule jamais `agency_id` lui-même et ne le lit **jamais** depuis une donnée envoyée par le client.
- Toute table portant `agency_id` doit, **dans la même migration**, activer la Row Level Security et créer la politique :
  ```sql
  ALTER TABLE <t> ENABLE ROW LEVEL SECURITY;
  CREATE POLICY tenant_isolation ON <t>
      USING (app_platform_access() OR agency_id = app_current_agency())
      WITH CHECK (app_platform_access() OR agency_id = app_current_agency());
  ```
  `TenantIsolationTest` échoue sinon.
- Périmètre d'exécution via `TenantContext` (ScopedValue) : `runAsAgency` / `callAsAgency`, `runAsPlatform` / `callAsPlatform` (back-office éditeur uniquement). Sans périmètre, rien n'est visible : les tâches asynchrones doivent ouvrir leur périmètre.
- Pas de `JdbcTemplate` ni de SQL hors Hibernate sur les tables métier.
- Tests d'isolation pour chaque nouvel endpoint métier : l'agence A ne lit, ne modifie ni ne compte jamais les données de B.

## Données et migrations

- Flyway `backend/src/main/resources/db/migration/V<n>__description.sql` ; une migration publiée n'est **jamais** modifiée. Migrations de test dans `src/test/resources/db/test-migration` (V1000+).
- Hibernate en `ddl-auto: validate` : chaque entité doit correspondre exactement au schéma (VARCHAR et non CHAR/TEXT pour les `String`, TIMESTAMPTZ pour `Instant`).
- Identifiants UUID v7 (`BaseEntity` / `Uuids.v7()`), horodatages `Instant` UTC, verrouillage optimiste `@Version Long`.
- Montants : type `Money` / `NUMERIC(19,4)` + devise ; **jamais** `double`/`float`. Conversion de devise uniquement avec un taux explicite et figé.
- Enums en `VARCHAR` + contrainte `CHECK`, mappés `@Enumerated(EnumType.STRING)`.

## API

- REST `/api/v1/<ressources-au-pluriel-en-kebab-case>` ; endpoints externes sans compte sous `/public/v1/`.
- Erreurs au format RFC 9457 (`application/problem+json`), messages traduits (`resources/i18n/messages*.properties`, FR par défaut, AR, EN).
- Documentation OpenAPI (annotations springdoc) sur chaque endpoint.
- Endpoints d'un module activable : `@RequiresModule(ModuleCode.X)` (403 si le module n'est pas souscrit).

## Frontend

- Aucun texte en dur : clés de traduction `<module>.<écran>.<élément>` dans `public/i18n/{fr,ar,en}.json`.
- CSS en propriétés logiques uniquement (`margin-inline-start`, `padding-block`…) pour le RTL.
- Contenu arabe : montants, codes, numéros, dates et e-mails isolés en gauche-à-droite (`<bdi>` ou `direction: ltr; unicode-bidi: isolate`).

## Sécurité

- Aucun secret dans le dépôt (local : `deploy/.env`, ignoré par Git). Aucune donnée personnelle dans les logs (nom, passeport, CIN, e-mail, téléphone, jeton). Aucune vraie pièce d'identité, même dans les tests.
- OWASP : validation des entrées (Bean Validation), pas de concaténation SQL, erreurs sans détails internes.

## Règles légales et fiscales

N'implémente **aucune** règle de TVA, de facturation, de CNDP ou de signature électronique qui ne figure pas comme validée par un expert. En cas de doute, arrête-toi et demande. Le régime de TVA actuel est la TVA classique uniquement.

## Tests

- JUnit 5 + AssertJ ; intégration avec `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (PostgreSQL réel).
- Pas de `@Transactional` au niveau des méthodes de test qui ouvrent un périmètre : utiliser `TenantContext` + `TransactionTemplate` (voir `TenantIsolationTest`, `PlatformPersistenceTest`).
- Avec des aides génériques (`asAgency(..., Supplier<T>)`), éviter les références de méthode surchargées (`repo::findAll`) : passer par une variable typée.

## Git

Conventional Commits avec la référence de la story : `feat(platform): create agency from back-office (US-101)`. Identité Git locale au dépôt (compte `ouskiabdelhakimwork`), jamais l'identité professionnelle.

N'ajoute jamais de ligne Co-Authored-By, Claude-Session ou toute autre attribution dans les messages de commit ni dans les descriptions de pull request.
