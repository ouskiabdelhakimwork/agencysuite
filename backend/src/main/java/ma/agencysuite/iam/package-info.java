/**
 * Module Identite et acces : validation des jetons Keycloak et ouverture du perimetre
 * (plateforme ou agence) de chaque requete HTTP.
 *
 * <p>Seul adaptateur Keycloak de l'application (ADR-08) : tout le code qui manipule un jeton
 * est dans {@code iam.infrastructure.security}. Les autres modules ne voient jamais un {@code Jwt} ;
 * ils lisent le perimetre courant dans {@code TenantContext}.
 */
@ApplicationModule(displayName = "Identity and access", allowedDependencies = {"shared", "platform"})
package ma.agencysuite.iam;

import org.springframework.modulith.ApplicationModule;
