/**
 * Module Plateforme : agences (tenants), paliers, modules souscrits, back-office
 * editeur, agence de demonstration, reversibilite.
 *
 * <p>Dependances autorisees (03-decisions-architecture.md, section 2) : shared uniquement.
 */
@ApplicationModule(displayName = "Platform", allowedDependencies = "shared")
package ma.agencysuite.platform;

import org.springframework.modulith.ApplicationModule;
