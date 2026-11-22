# AgencySuite

SaaS multi-agences de gestion d'agence de voyage et MICE (Maroc) : CRM, devis, dossiers, facturation, espace de fichiers, OCR des passeports et CIN, import/export Excel, modules activables.

La documentation produit et les décisions sont dans le projet Claude (cahier des charges, décisions d'architecture, modèle de données, user stories, conventions).

## Structure du dépôt

```
backend/     API Spring Boot 4 (Java 25, Maven), monolithe modulaire contrôlé par Spring Modulith
frontend/    Application Angular (lot suivant)
deploy/      docker-compose local, charts Helm (lots suivants)
docs/        ADR, OpenAPI exporté (lots suivants)
env.ps1      Bascule la session PowerShell sur Java 25
```

## Prérequis

| Outil | Version | Remarque |
|---|---|---|
| JDK | 25 | Temurin conseillé. `JAVA_HOME` doit pointer dessus |
| Maven | 3.9+ | Fourni par le wrapper `mvnw` : rien à installer |
| Node.js | 24 | Pour le frontend (lot suivant) |
| Docker | récent | Nécessaire à partir du lot base de données |

## Premier démarrage (Windows, PowerShell)

1. Basculer la session sur Java 25 (inutile si `JAVA_HOME` pointe déjà sur un JDK 25) :
   ```powershell
   . .\env.ps1
   ```
2. Générer le wrapper Maven, une seule fois, avec le Maven déjà présent sur le poste :
   ```powershell
   cd backend
   & "$env:USERPROFILE\apache-maven-3.8.6\bin\mvn.cmd" -N wrapper:wrapper "-Dmaven=3.9.9"
   ```
   Cela crée `mvnw`, `mvnw.cmd` et `.mvn/wrapper/maven-wrapper.properties`, à versionner.
3. Compiler et lancer tous les tests :
   ```powershell
   .\mvnw.cmd verify
   ```
4. Démarrer l'API :
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

## Vérifier que le socle fonctionne

| URL | Attendu |
|---|---|
| http://localhost:8080/api/v1/platform/info | JSON avec `"locale": "fr"`, `"direction": "ltr"` |
| Même URL avec l'en-tête `Accept-Language: ar-MA` | `"locale": "ar"`, `"direction": "rtl"`, message en arabe |
| http://localhost:8080/swagger-ui.html | Documentation OpenAPI |
| http://localhost:8080/actuator/health | `{"status":"UP"}` |
| http://localhost:8080/api/v1/inexistant | Erreur 404 au format `application/problem+json` |

Après `verify`, les diagrammes des modules sont générés dans `backend/target/spring-modulith-docs`.

## Règles essentielles

- Un paquet par module métier sous `ma.agencysuite`. Chaque module déclare ses dépendances autorisées dans son `package-info.java` ; `ModularityTests` fait échouer le build en cas de violation.
- Montants : type `Money` (BigDecimal), jamais `double`.
- Aucun secret dans le dépôt ; aucune vraie pièce d'identité, même dans les tests.
- Commits : Conventional Commits avec la référence de la story, par exemple `feat(quote): add margin calculation (US-702)`.
