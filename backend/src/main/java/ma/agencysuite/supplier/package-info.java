/**
 * Module fournisseurs (etape 5, lot 3, US-601) : fournisseurs, prestations (produits) et tarifs
 * d'achat par periode et par base, dans la devise du fournisseur. Fait partie du socle : pas de
 * {@code @RequiresModule}.
 *
 * <p>Dependances autorisees : shared uniquement (aucune dependance vers crm ni iam). La permission
 * d'ecriture {@code supplier.write} vient de {@code @RequiresPermission} (shared), l'auteur de
 * {@code ActorContext}, l'audit de l'evenement generique {@code AuditableEvent}. Montants :
 * {@code Money} (shared/money) ; noms et descriptions traduisibles : {@code LocalizedText}
 * (shared/i18n) ; cles de recherche : {@code SearchKeys} (shared/text).
 *
 * <p>Visibilite : tous les utilisateurs de l'agence lisent fournisseurs, prestations et prix
 * d'achat (le commercial en a besoin pour ses devis).
 */
@ApplicationModule(displayName = "Suppliers", allowedDependencies = {"shared"})
package ma.agencysuite.supplier;

import org.springframework.modulith.ApplicationModule;
