/**
 * Module Identite et acces : validation des jetons Keycloak, ouverture du perimetre
 * (plateforme ou agence) de chaque requete HTTP, utilisateurs, roles systeme et permissions
 * ({@code @RequiresPermission}).
 *
 * <p>Seul adaptateur Keycloak de l'application (ADR-08) : tout le code qui manipule un jeton
 * est dans {@code iam.infrastructure.security}. Les autres modules ne voient jamais un {@code Jwt} ;
 * ils lisent le perimetre courant dans {@code TenantContext}.
 */
@ApplicationModule(displayName = "Identity and access", allowedDependencies = {"shared", "platform"})
package ma.agencysuite.iam;

import org.springframework.modulith.ApplicationModule;
