# Étape 5, lot 3 : fournisseurs, prestations et tarifs (backend, module `supplier`)

> Plan rédigé dans le chat projet. À implémenter par Claude Code en suivant `CLAUDE.md`.
> User story : US-601 (créer un fournisseur et ses prestations avec tarifs par saison et base, dans la devise du fournisseur).
> Références :
> - 04-modele-de-donnees.md §5 (`supplier`, `product`, `rate`) et §1 (montants, textes traduisibles en JSONB) ;
> - 01-cahier-des-charges.md §5.3 ;
> - ADR-15 (montants : `BigDecimal`, `NUMERIC(19,4)`, ISO 4217, type `Money`, arrondi à confirmer RG-03) ;
> - 06-conventions-code.md (montants, erreurs) ;
> - décisions du 28/11/2026 : liste fermée de types de fournisseurs ; tarifs simples **par période et par base**, sans chevauchement ; enfants, suppléments et allotements plus tard ; le fournisseur fait partie du **socle** (pas de `@RequiresModule`).
>
> Périmètre : **backend uniquement**. Les écrans arrivent au lot 6.

## 1. Objectif

1. Nouveau module **`supplier`** : fournisseurs, prestations (produits) et tarifs d'achat.
2. Tarifs **par période** (dates incluses) et **par base** (single, double, par personne…), dans la devise du fournisseur, **sans chevauchement** pour une même prestation et une même base.
3. Premier usage du type **`Money`** : montants exacts, devise ISO 4217, jamais de `double`.
4. Premiers **textes traduisibles saisis par l'agence** (nom et description de la prestation en FR/AR/EN, JSONB).
5. Recherche, archivage, restauration, doublon probable sur les fournisseurs, audit de l'archivage.

## 2. Décisions de conception (à respecter)

### 2.1 Module et dépendances

- `ma.agencysuite.supplier`, `@ApplicationModule(allowedDependencies = {"shared"})`. Aucune dépendance vers `crm` ni `iam`. Pas de `@RequiresModule`.
- Entités `Supplier`, `Product`, `Rate` héritant de `AuthoredAgencyEntity`.
- Permission d'écriture : `supplier.write` (créée en V7 : MANAGER, OPERATIONS). Lecture : tout utilisateur de l'agence (le commercial a besoin des prix d'achat pour ses devis).

### 2.2 Code commun déplacé dans `shared`

Le module `supplier` a besoin d'outils écrits au lot 2 dans `crm`. Déplace-les dans `shared`, sans changer leur comportement, et adapte `crm` :
- `crm/domain/NameKeys` → `shared/text/SearchKeys` (normalisation minuscules, sans accents, apostrophes, espaces) ;
- `CustomerSpecifications.escapeLike` → `shared/text/SearchKeys.escapeLike` (ou une classe voisine) ;
- les tests existants (`NameKeysTest`) suivent le déplacement.

Le code `possible-duplicate` est le même pour tous les modules, mais l'exception est propre à chaque module (`supplier` ne dépend pas de `crm`). Le gestionnaire du module `supplier` produit **exactement** la même forme de réponse (`type`, titre et détail traduits, `candidates`).

### 2.3 Type `Money`

- Si `shared` contient déjà un type `Money`, réutilise-le. Sinon crée `shared/money/Money` : `record Money(BigDecimal amount, CurrencyCode currency)` (ou `java.util.Currency`), immuable, sans opération d'arrondi implicite. Ajoute seulement ce dont ce lot a besoin (création, validation, comparaison) : les additions et conversions arriveront avec les devis.
- Validation de la devise : code ISO 4217 connu de `java.util.Currency`, en majuscules (`@ValidCurrencyCode` dans `shared/money`).
- **Pas d'arrondi** : un montant qui a plus de décimales que la devise n'en autorise (`Currency.getDefaultFractionDigits()` : 2 pour MAD, EUR, USD, SAR ; 0 pour JPY ; 3 pour TND, KWD) est **refusé** (400 `validation-failed`), jamais arrondi. La règle d'arrondi (RG-03) n'est pas validée.
- Montant strictement positif ; au plus 15 chiffres avant la virgule (`NUMERIC(19,4)`).
- **JSON** : un montant est un objet `{"amount": "1250.00", "currency": "EUR"}` avec **`amount` en chaîne** (exact côté Angular, pas de nombre flottant JavaScript). En entrée, refuse un nombre JSON (`1250.5`) : seule la chaîne est acceptée. Sortie : `toPlainString()` à l'échelle de la devise (`1250.00`, `1250`, `1250.000`).
- Base : `NUMERIC(19,4)` + `VARCHAR(3)`.

### 2.4 Textes traduisibles `LocalizedText`

- Nouveau type `shared/i18n/LocalizedText` : textes par langue (`fr`, `ar`, `en`), stocké en JSONB `{"fr": "...", "ar": "...", "en": "..."}` (même technique de mapping JSON que `audit_log.details`).
- Règles : clés limitées à `fr`, `ar`, `en` ; valeurs nettoyées (espaces) ; valeur vide = langue absente ; **au moins une langue** pour un champ obligatoire ; longueur maximale par langue fixée par le champ.
- L'API renvoie l'objet complet. Le choix de la langue affichée (repli sur une autre langue si la langue demandée manque) est fait par le frontend au lot 6 : ne l'implémente pas côté serveur.

### 2.5 Non-chevauchement des tarifs

- Règle : pour une même prestation et une même base, deux tarifs **non archivés** ne peuvent pas avoir de périodes qui se chevauchent (dates incluses : un tarif qui finit le 30/06 et un autre qui commence le 30/06 se chevauchent).
- Contrôle **dans l'application**, sous **verrou pessimiste** sur la ligne `product` (`PESSIMISTIC_WRITE` dans la transaction de création ou de modification d'un tarif) pour éviter que deux écritures simultanées passent le contrôle.
- Pas de contrainte d'exclusion PostgreSQL : elle demande l'extension `btree_gist`, dont la disponibilité chez l'hébergeur n'est pas confirmée. Ajoute un `TODO(etape-12)` dans la migration : remplacer ou doubler le contrôle par `EXCLUDE USING gist` si l'extension est disponible.
- Conflit → 409 `rate-overlap` avec `conflictingRateId`, `validFrom`, `validTo` du tarif en conflit.

## 3. Migration `V9__create_supplier_catalog.sql`

```
supplier
  id UUID PK, agency_id UUID NOT NULL REFERENCES agency(id)
  type VARCHAR(30) NOT NULL CHECK (type IN ('HOTEL','RIAD','TRANSPORT','AIRLINE','DMC','RESTAURANT',
       'VENUE','GUIDE','ACTIVITY','TECHNICAL','INSURER','OTHER'))
  name VARCHAR(200) NOT NULL
  name_key VARCHAR(200) NOT NULL          -- SearchKeys, jamais renvoyé
  ice VARCHAR(15) NULL
  email VARCHAR(254) NULL                 -- minuscules
  phone VARCHAR(20) NULL                  -- E.164
  contact_name VARCHAR(200) NULL
  address_line VARCHAR(300) NULL, city VARCHAR(100) NULL
  country VARCHAR(2) NOT NULL DEFAULT 'MA' CHECK (country ~ '^[A-Z]{2}$')
  default_currency VARCHAR(3) NOT NULL DEFAULT 'MAD' CHECK (default_currency ~ '^[A-Z]{3}$')
  payment_terms VARCHAR(500) NULL         -- texte libre : « 30 % à la réservation, solde J-15 »
  notes VARCHAR(2000) NULL
  archived_at TIMESTAMPTZ NULL
  colonnes d'auteur et de version habituelles
  CONSTRAINT uk_supplier_id_agency UNIQUE (id, agency_id)

product
  id UUID PK, agency_id UUID NOT NULL REFERENCES agency(id)
  supplier_id UUID NOT NULL
  type VARCHAR(30) NOT NULL CHECK (type IN ('ACCOMMODATION','TRANSFER','EXCURSION','ROOM_HIRE',
       'CATERING','FLIGHT','OTHER'))
  name JSONB NOT NULL                     -- LocalizedText, au moins une langue
  description JSONB NULL                  -- LocalizedText
  search_key VARCHAR(700) NOT NULL        -- SearchKeys de toutes les langues du nom, jamais renvoyé
  cancellation_policy VARCHAR(2000) NULL  -- texte interne
  archived_at TIMESTAMPTZ NULL
  colonnes d'auteur et de version habituelles
  CONSTRAINT uk_product_id_agency UNIQUE (id, agency_id)
  CONSTRAINT fk_product_supplier_same_agency FOREIGN KEY (supplier_id, agency_id) REFERENCES supplier (id, agency_id)

rate
  id UUID PK, agency_id UUID NOT NULL REFERENCES agency(id)
  product_id UUID NOT NULL
  basis VARCHAR(20) NOT NULL CHECK (basis IN ('SINGLE','DOUBLE','TRIPLE','QUADRUPLE','PER_PERSON','PER_UNIT'))
  valid_from DATE NOT NULL, valid_to DATE NOT NULL
  purchase_amount NUMERIC(19,4) NOT NULL CHECK (purchase_amount > 0)
  purchase_currency VARCHAR(3) NOT NULL CHECK (purchase_currency ~ '^[A-Z]{3}$')
  label VARCHAR(100) NULL                 -- « Haute saison », « Ramadan »…
  archived_at TIMESTAMPTZ NULL
  colonnes d'auteur et de version habituelles
  CONSTRAINT ck_rate_period CHECK (valid_to >= valid_from)
  CONSTRAINT fk_rate_product_same_agency FOREIGN KEY (product_id, agency_id) REFERENCES product (id, agency_id)
```

- Index : `supplier (agency_id, name_key)`, `(agency_id, ice)`, `(agency_id, email)`, `(agency_id, phone)` ; `product (supplier_id)`, `(agency_id, search_key)` ; `rate (product_id, basis, valid_from)`.
- RLS et politique `tenant_isolation` sur les **trois** tables, comme V6 et V8.
- Pas de prix de vente conseillé (`sale_price_suggested` du modèle de données) : la marge et le prix de vente arrivent avec les devis (étape 6). Note-le en tête de migration.
- Pas de table `allotment` (US-602, P3).

## 4. Règles métier

| Règle | Détail |
|---|---|
| Normalisation fournisseur | Comme les clients : espaces, e-mail en minuscules, téléphone E.164 (région MA), `name_key` recalculée, pays MA et devise MAD par défaut |
| ICE fournisseur | Facultatif, 15 chiffres (même règle que les clients, format à confirmer RG-19), quel que soit le type |
| Doublon probable fournisseur | À la création : fournisseur non archivé avec le même ICE, le même e-mail ou le même téléphone → 409 `possible-duplicate` (candidats `id`, `name`, critères `ICE`, `EMAIL`, `PHONE`) ; `confirmDuplicate: true`. Même contrôle à la modification si l'un de ces champs change. Le nom seul n'est pas un critère |
| Devise du tarif | Par défaut, la devise du fournisseur ; une autre devise est acceptée si elle est explicitement envoyée (contrat en EUR d'un hôtel facturant d'habitude en MAD). Changer la devise par défaut du fournisseur **ne modifie pas** les tarifs existants |
| Montant du tarif | Règles de la section 2.3 (chaîne, > 0, décimales de la devise, pas d'arrondi) |
| Période | `validTo >= validFrom` (400) ; pas de chevauchement (section 2.5) ; une période dans le passé est acceptée (saisie d'historique) |
| Prestation d'un fournisseur archivé | Création refusée : 409 `supplier-archived`. Les prestations existantes restent lisibles |
| Tarif d'une prestation archivée | Création refusée : 409 `product-archived` |
| Archivage | Fournisseur, prestation et tarif : `archived_at`, idempotent, absent des listes par défaut, lisible par son `id`, restaurable. Archiver un fournisseur **n'archive pas** ses prestations (elles sont simplement masquées par le filtre `supplierArchived` côté liste, voir API). Restaurer un tarif dont la période chevauche un tarif actif → 409 `rate-overlap` |
| Audit | `SUPPLIER_ARCHIVED`, `SUPPLIER_RESTORED`, `PRODUCT_ARCHIVED`, `PRODUCT_RESTORED`, avec `details` = `{"type": "<type>"}`. Pas d'audit des tarifs à ce lot (modification fréquente ; l'historique des prix sera traité avec les devis) |
| Verrouillage optimiste | `version` obligatoire aux `PUT` → 409 `stale-version` |
| Suppression | Aucune suppression réelle à ce lot : un tarif erroné est archivé |

## 5. API

### Fournisseurs

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/suppliers` | `q` (nom sans accents, e-mail, téléphone forme locale et E.164, ICE, ville), `type`, `city`, `archived` (défaut `false`), `page`, `size` (25, max 100), `sort` = `name` (défaut), `-createdAt`, `-updatedAt`. Liste : `id`, `type`, `name`, `city`, `country`, `defaultCurrency`, `phone`, `email`, `archived`, `productCount` (non archivées) |
| GET | `/api/v1/suppliers/{id}` | Fiche complète |
| POST | `/api/v1/suppliers` | `supplier.write` ; 201 + `Location` ; 409 `possible-duplicate` |
| PUT | `/api/v1/suppliers/{id}` | `supplier.write` ; `version` |
| POST | `/api/v1/suppliers/{id}/archive`, `/restore` | `supplier.write` ; idempotents |

### Prestations

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/products` | `q` (nom dans toutes les langues, sans accents), `supplierId`, `type`, `archived` (défaut `false`), `supplierArchived` (défaut `false` : masque les prestations des fournisseurs archivés), `page`, `size`, `sort` = `name` (sur `search_key`) ou `-updatedAt`. Liste : `id`, `type`, `name` (objet par langue), `supplier` (`id`, `name`, `archived`), `archived` |
| GET | `/api/v1/products/{id}` | Fiche + `rates` non archivés, triés par base puis `validFrom` |
| POST | `/api/v1/suppliers/{supplierId}/products` | `supplier.write` ; 201 + `Location` (`/api/v1/products/{id}`) ; 409 `supplier-archived` |
| PUT | `/api/v1/products/{id}` | `supplier.write` ; `version` ; le fournisseur ne change pas |
| POST | `/api/v1/products/{id}/archive`, `/restore` | `supplier.write` |

### Tarifs

| Méthode | Chemin | Détail |
|---|---|---|
| GET | `/api/v1/products/{id}/rates` | `archived` (défaut `false`), `basis`, `on` (date : tarifs valables ce jour-là) |
| POST | `/api/v1/products/{id}/rates` | `supplier.write` ; corps `basis`, `validFrom`, `validTo`, `purchase` (`{amount, currency}`, devise facultative = celle du fournisseur), `label` ; 201 ; 409 `rate-overlap` ou `product-archived` |
| PUT | `/api/v1/products/{productId}/rates/{rateId}` | `supplier.write` ; `version` ; mêmes contrôles |
| POST | `/api/v1/products/{productId}/rates/{rateId}/archive`, `/restore` | `supplier.write` ; restauration contrôlée (chevauchement) |

- Un identifiant d'une autre agence → 404 partout ; un tarif d'une autre prestation → 404.
- Nouveaux codes d'erreur traduits FR/AR/EN : `rate-overlap`, `supplier-archived`, `product-archived`. Réutilisés : `possible-duplicate`, `stale-version`, `not-found`, `permission-denied`, `validation-failed`.
- OpenAPI : groupes `suppliers`, `products`, `rates` ; le format des montants (chaîne, décimales de la devise, pas d'arrondi) est décrit dans le schéma `Money`.

## 6. Tests

| Test | Ce qu'il vérifie |
|---|---|
| `MoneyTest` (unitaire) | Création, devise inconnue, montant nul ou négatif, trop de décimales pour MAD, EUR, JPY, TND refusées sans arrondi ; sortie à l'échelle de la devise ; aucun `double` |
| `MoneyJsonTest` | `amount` en chaîne accepté ; nombre JSON refusé (400) ; sortie en chaîne |
| `LocalizedTextTest` | Clés autorisées, langue vide ignorée, au moins une langue, longueurs, écriture et relecture JSONB |
| `SearchKeysTest` | Ancien `NameKeysTest` déplacé, inchangé |
| `SupplierApiTest` | Création, normalisation, recherche (`q` nom sans accent, téléphone, ICE, ville), filtres, tri, pagination ; ACCOUNTANT et SALES lisent mais ne créent pas (403) ; `productCount` |
| `SupplierDuplicateTest` | ICE, e-mail, téléphone → 409 ; `confirmDuplicate` ; archivé non candidat ; autre agence jamais proposée |
| `ProductApiTest` | Création avec nom FR seul, AR seul, trois langues ; aucune langue → 400 ; recherche dans toutes les langues ; fournisseur archivé → 409 ; `supplierArchived` ; prestation d'une autre agence → 404 |
| `RateApiTest` | Création dans la devise du fournisseur par défaut et dans une autre devise ; montant en chaîne ; décimales ; `validTo < validFrom` → 400 ; filtre `on` ; changer la devise par défaut du fournisseur ne change pas les tarifs |
| `RateOverlapTest` | Chevauchement partiel, inclus, même jour de bord → 409 avec le tarif en conflit ; bases différentes → autorisé ; tarif archivé ignoré ; restauration en conflit → 409 ; **deux créations simultanées en conflit : une seule réussit** (deux fils, même prestation) |
| `SupplierArchiveTest` | Archivage et restauration idempotents (fournisseur, prestation, tarif) ; audit avec `details` = type seulement |
| `TenantIsolationTest` (étendu) | RLS des trois tables ; clés composées : prestation vers fournisseur d'une autre agence, tarif vers prestation d'une autre agence refusés par la base |
| `ReferenceDataTest` | Inchangé (pas de nouvelle permission) |
| `ModularityTests` | `supplier` → `shared` seulement ; `crm` → `shared` seulement après le déplacement de `SearchKeys` |

## 7. Contrôles manuels (développeur, console du navigateur, fonction `api` du lot 1)

1. Créer un fournisseur `{ type: 'RIAD', name: 'Riad Atlas Test', city: 'Marrakech', defaultCurrency: 'EUR', phone: '0524000000' }` → 201, téléphone en E.164.
2. Créer une prestation `{ type: 'ACCOMMODATION', name: { fr: 'Chambre double demi-pension', ar: 'غرفة مزدوجة نصف إقامة' } }` → 201.
3. Créer deux tarifs `DOUBLE` : du 01/01 au 30/06 à `"95.00"` et du 01/07 au 31/12 à `"120.00"` → 201 ; puis un troisième du 15/06 au 15/07 → 409 `rate-overlap` avec le tarif en conflit.
4. Tarif avec `"95.555"` en EUR → 400 ; avec `95.5` (nombre) → 400.
5. Rechercher la prestation par `chambre`, `demi pension` et en arabe.
6. Archiver puis restaurer le fournisseur : deux entrées d'audit avec `{"type":"RIAD"}`.
7. Avec `claim-check` en ACCOUNTANT ou SALES (rôle modifié dans psql) : lecture 200, création 403. Remettre le rôle MANAGER ensuite.

## 8. Hors périmètre

Prix de vente et marge (étape 6, devis), tarifs enfants, suppléments, réductions, contingents et allotements (US-602), conditions d'annulation structurées et pénalités, contrats fournisseurs en fichiers (étape 8), règlements et échéances fournisseurs (US-603), taux de change (étape 9), import Excel des grilles tarifaires (étape 7), filtrage des prestations par module, écrans (lot 6).

## 9. Compte rendu attendu

Dans `revue/etape5-lot3-compte-rendu.md` et `revue/etape5-lot3.diff` (`git add -N backend` puis `git diff -- backend`) :
- build et tests (avant / après) ;
- fichiers créés, modifiés et **déplacés** (`SearchKeys`) ;
- écarts au plan et pourquoi, en particulier : `Money` existant ou créé, mapping JSONB de `LocalizedText`, refus des montants en nombre JSON, test de concurrence du chevauchement ;
- points à valider ;
- contrôles manuels de la section 7 avec les extraits de code pour la console ;
- contrôle des secrets et des données (fournisseurs fictifs uniquement) ;
- message de commit proposé, sans ligne d'attribution :

```
feat(supplier): suppliers, products and seasonal purchase rates with Money and localized texts (US-601)
```
