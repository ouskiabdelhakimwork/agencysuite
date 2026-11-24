package ma.agencysuite.platform;

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
}
