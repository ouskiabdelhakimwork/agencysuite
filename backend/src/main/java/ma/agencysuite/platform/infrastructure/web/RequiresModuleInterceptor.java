package ma.agencysuite.platform.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.platform.application.AgencyScopeRequiredException;
import ma.agencysuite.platform.application.ModuleNotEnabledException;
import ma.agencysuite.shared.module.RequiresModule;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantScope;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Garde des modules activables : avant chaque appel d'un controleur annote
 * {@link RequiresModule} (sur la methode, sinon sur la classe), verifie que l'agence courante
 * a souscrit le module et qu'il est actif. Sinon : 403 problem+json.
 *
 * <p>Verification a chaque requete, sans cache (a envisager plus tard, avec invalidation
 * a l'activation/desactivation).
 */
class RequiresModuleInterceptor implements HandlerInterceptor {

    private final PlatformApi platform;

    RequiresModuleInterceptor(PlatformApi platform) {
        this.platform = platform;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresModule required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresModule.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresModule.class);
        }
        if (required == null) {
            return true;
        }
        AgencyId agency = TenantContext.current()
                .filter(TenantScope.Agency.class::isInstance)
                .map(scope -> ((TenantScope.Agency) scope).agencyId())
                .orElseThrow(AgencyScopeRequiredException::new);
        if (!platform.isModuleEnabled(agency, required.value())) {
            throw new ModuleNotEnabledException(required.value());
        }
        return true;
    }
}
