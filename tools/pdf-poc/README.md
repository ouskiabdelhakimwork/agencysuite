# Banc d'essai PDF (ADR-13)

Compare deux moteurs de rendu HTML → PDF sur une même facture fictive, en français et en arabe :

| Candidat | Principe |
|---|---|
| Chromium (Playwright pour Java) | Le moteur du navigateur Chrome, sans interface, imprime la page en PDF |
| openhtmltopdf + extension RTL | Bibliothèque Java pure, sans navigateur |

## Lancer

Depuis le dossier `backend` (pour réutiliser son wrapper Maven) :

```powershell
cd backend
.\mvnw.cmd -f ..\tools\pdf-poc\pom.xml compile exec:java
```

Au premier lancement, Playwright télécharge Chromium (environ 150 Mo) dans le profil utilisateur, sans droits administrateur. Derrière un proxy d'entreprise, définir `HTTPS_PROXY` si le téléchargement échoue.

Résultat : 4 PDF dans `tools/pdf-poc/target/pdf-poc/` et le temps de rendu de chacun dans la console.

## Grille de comparaison

Ouvrir les 4 PDF et noter, pour chaque moteur :

| Critère | Chromium | openhtmltopdf |
|---|---|---|
| Lettres arabes liées correctement (pas de lettres isolées) | | |
| Texte arabe de droite à gauche, ordre des mots correct | | |
| Mélange arabe + chiffres + codes latins (ICE, F-2026-000123, e-mail) dans le bon ordre | | |
| Tableau en RTL : colonne « البيان » à droite, montants alignés | | |
| Bloc des totaux placé à gauche en arabe | | |
| Rendu français correct (accents, alignements) | | |
| Temps de rendu (ms, deuxième document) | | |

Les données sont fictives. Les textes arabes sont à faire relire par un arabophone.
