/**
 * Noyau partage (shared kernel) : types communs a tous les modules
 * (montants, identifiant d'agence, langues). Ne contient aucune logique metier
 * propre a un module.
 *
 * <p>Module de type OPEN : tous les modules peuvent utiliser ses sous-paquets.
 * Il ne doit jamais dependre d'un module metier.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package ma.agencysuite.shared;

import org.springframework.modulith.ApplicationModule;
