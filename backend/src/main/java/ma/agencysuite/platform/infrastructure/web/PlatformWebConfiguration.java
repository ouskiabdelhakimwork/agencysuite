package ma.agencysuite.platform.infrastructure.web;

import ma.agencysuite.platform.PlatformApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Branchement web du module Plateforme : garde @RequiresModule.
 * L'acces au back-office (jeton du realm "platform") est gere par le module iam.
 */
@Configuration(proxyBeanMethods = false)
class PlatformWebConfiguration implements WebMvcConfigurer {

    private final PlatformApi platform;

    PlatformWebConfiguration(PlatformApi platform) {
        this.platform = platform;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequiresModuleInterceptor(platform));
    }
}
