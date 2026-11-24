package ma.agencysuite.iam.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.agencysuite.iam.application.PermissionDeniedException;
import ma.agencysuite.iam.application.UserRequiredException;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.RequiresPermission;
import ma.agencysuite.shared.security.UserContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Garde des permissions : avant chaque appel d'un controleur annote {@link RequiresPermission}
 * (sur la methode, sinon sur la classe), verifie que l'utilisateur courant a la permission.
 * Sans utilisateur courant : 403 access-denied ; permission absente : 403 permission-denied.
 *
 * <p>Les permissions sont celles resolues par le filtre de securite pour cette requete
 * ({@link UserContext}) : aucune lecture en base ici.
 */
class RequiresPermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission required =
                AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresPermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }
        CurrentUser user = UserContext.current().orElseThrow(UserRequiredException::new);
        if (!user.has(required.value())) {
            throw new PermissionDeniedException(required.value());
        }
        return true;
    }
}
