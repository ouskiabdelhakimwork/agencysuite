package ma.agencysuite.platform.infrastructure.web;

import ma.agencysuite.platform.PlatformApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Branchement web du module Plateforme : garde @RequiresModule et acces au back-office. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BackofficeProperties.class)
class PlatformWebConfiguration implements WebMvcConfigurer {

    private final PlatformApi platform;

    PlatformWebConfiguration(PlatformApi platform) {
        this.platform = platform;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequiresModuleInterceptor(platform));
    }

    /** Resolveur de Spring MVC (et non celui des pages d'erreur) : erreurs au format problem+json. */
    @Bean
    BackofficeAccessFilter backofficeAccessFilter(BackofficeProperties properties,
            @Qualifier("handlerExceptionResolver") ObjectProvider<HandlerExceptionResolver> exceptionResolver) {
        return new BackofficeAccessFilter(properties, exceptionResolver);
    }
}
