/**
 * Module CRM (etape 5) : clients particuliers et entreprises, contacts des entreprises (US-401),
 * voyageurs et leur rattachement a zero, un ou plusieurs clients (lot 2), demandes des clients avec
 * leurs transitions de statut controlees (lot 4, US-701). Fait partie du socle :
 * pas de {@code @RequiresModule}.
 *
 * <p>Dependances autorisees : shared uniquement. Aucune dependance vers iam : la permission vient
 * de {@code @RequiresPermission} (shared), l'auteur de {@code ActorContext}, et l'audit passe par
 * l'evenement generique {@code AuditableEvent}. La permission {@code identity.read}
 * (date de naissance des voyageurs) est lue dans {@code UserContext} (shared), meme source que
 * {@code @RequiresPermission}.
 *
 * <p>Visibilite : tous les utilisateurs de l'agence voient tous ses clients et toutes ses demandes ;
 * le responsable ({@code owner_user_id}) sert a l'affichage et au filtrage, jamais a restreindre l'acces.
 */
@ApplicationModule(displayName = "CRM", allowedDependencies = {"shared"})
package ma.agencysuite.crm;

import org.springframework.modulith.ApplicationModule;
