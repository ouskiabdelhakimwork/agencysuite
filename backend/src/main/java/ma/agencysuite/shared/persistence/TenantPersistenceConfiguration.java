package ma.agencysuite.shared.persistence;

import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Branche le multi-tenant dans Hibernate (ADR-06) :
 * filtre applicatif par @TenantId (resolveur) et isolation en base par RLS (fournisseur de connexions).
 */
@Configuration(proxyBeanMethods = false)
class TenantPersistenceConfiguration {

    @Bean
    TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    RowLevelSecurityConnectionProvider rowLevelSecurityConnectionProvider(DataSource dataSource) {
        return new RowLevelSecurityConnectionProvider(dataSource);
    }

    @Bean
    HibernatePropertiesCustomizer tenantHibernateProperties(TenantIdentifierResolver resolver,
            RowLevelSecurityConnectionProvider connectionProvider) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        };
    }
}
