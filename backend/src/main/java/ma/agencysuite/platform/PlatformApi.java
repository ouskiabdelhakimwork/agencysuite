package ma.agencysuite.platform;

import java.util.Optional;
import java.util.Set;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * API publique du module Plateforme : seule entree utilisable par les autres modules.
 *
 * <p>Les lectures respectent le perimetre de l'appelant et n'en ouvrent jamais : dans le
 * perimetre d'une agence, seule cette agence est lisible (une autre agence donne {@code false}
 * ou un ensemble vide) ; le perimetre plateforme voit toutes les agences ; sans perimetre,
 * rien n'est visible.
 */
public interface PlatformApi {

    /** Vrai si l'agence a souscrit le module et qu'il est actuellement active. */
    boolean isModuleEnabled(AgencyId agencyId, ModuleCode module);

    /** Modules actuellement actives pour l'agence (vide si aucun ou si l'agence est inconnue). */
    Set<ModuleCode> enabledModules(AgencyId agencyId);

    /**
     * Vrai si l'agence existe et peut etre utilisee (statut TRIAL ou ACTIVE). Faux si elle est
     * inconnue, suspendue, en cloture ou fermee, ou si le perimetre courant ne permet pas de la lire.
     */
    boolean isOperational(AgencyId agencyId);

    /** Nom de l'agence. Vide si elle est inconnue ou si le perimetre courant ne permet pas de la lire. */
    Optional<String> agencyName(AgencyId agencyId);

    /**
     * Nom, sous-domaine, langue par defaut, statut clos et palier (nombre maximal d'utilisateurs)
     * de l'agence, pour la gestion de ses utilisateurs. Vide si elle est inconnue ou si le perimetre
     * courant ne permet pas de la lire.
     */
    Optional<AgencyUserSettings> userSettings(AgencyId agencyId);
}
