package ma.agencysuite.iam.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Branchement web du module iam : garde @RequiresPermission. */
@Configuration(proxyBeanMethods = false)
class IamWebConfiguration implements WebMvcConfigurer {

    /**
     * Ordre de la garde des permissions : apres celle des modules (@RequiresModule, ordre 0 par
     * defaut), pour qu'un module non souscrit reponde module-not-enabled avant toute question
     * de permission.
     */
    static final int REQUIRES_PERMISSION_ORDER = 100;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequiresPermissionInterceptor()).order(REQUIRES_PERMISSION_ORDER);
    }
}
