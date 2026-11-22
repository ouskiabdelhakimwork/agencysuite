package ma.agencysuite.shared.i18n;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Resolution de la langue cote serveur a partir de l'en-tete Accept-Language.
 * Les textes serveur (erreurs, e-mails, PDF) sont dans resources/i18n/messages*.properties.
 */
@Configuration(proxyBeanMethods = false)
class I18nConfiguration {

    /** Le nom du bean doit etre "localeResolver" pour etre pris en compte par Spring MVC. */
    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SupportedLocale.locales());
        resolver.setDefaultLocale(SupportedLocale.DEFAULT.locale());
        return resolver;
    }
}
