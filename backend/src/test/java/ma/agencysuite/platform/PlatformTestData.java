package ma.agencysuite.platform;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import ma.agencysuite.TestTokens;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.application.ModuleActivation;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.AgencyModuleId;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.platform.infrastructure.SubscriptionRepository;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Donnees de test de la plateforme, ecrites et nettoyees dans le perimetre plateforme.
 * Les sous-domaines portent un suffixe aleatoire pour que les tests ne se genent pas.
 */
@Component
public class PlatformTestData {

    private static final String[] CLEANUP = {
        "DELETE FROM rate WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM product WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM supplier WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM customer_traveler WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM traveler WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM contact WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM customer WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM user_role WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM user_account WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM tenant_probe WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM agency_module WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM subscription WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE :pattern)",
        "DELETE FROM agency WHERE subdomain LIKE :pattern",
    };

    private final AgencyRepository agencies;
    private final AgencyModuleRepository agencyModules;
    private final ModuleActivation moduleActivation;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;
    private final IamTestData iam;
    private final DataSource dataSource;
    private final SubscriptionRepository subscriptions;

    PlatformTestData(AgencyRepository agencies, AgencyModuleRepository agencyModules,
            ModuleActivation moduleActivation, TransactionTemplate transaction, EntityManager entityManager,
            IamTestData iam, DataSource dataSource, SubscriptionRepository subscriptions) {
        this.agencies = agencies;
        this.agencyModules = agencyModules;
        this.moduleActivation = moduleActivation;
        this.transaction = transaction;
        this.entityManager = entityManager;
        this.iam = iam;
        this.dataSource = dataSource;
        this.subscriptions = subscriptions;
    }

    /** Suffixe aleatoire (8 caracteres hexadecimaux) a ajouter aux sous-domaines d'un test. */
    public static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Cree une agence cliente (sans abonnement), son gerant par defaut (MANAGER, ACTIVE, sub
     * {@link TestTokens#managerSub(UUID)}, porte par {@link TestTokens#agency(UUID)}) et active les
     * modules indiques.
     */
    public UUID createAgency(String subdomain, ModuleCode... modules) {
        UUID id = TenantContext.callAsPlatform(() -> transaction.execute(
                status -> agencies.save(Agency.createCustomer("Agence " + subdomain, subdomain)).getId()));
        iam.createUser(id, TestTokens.managerSub(id), UserStatus.ACTIVE, "MANAGER");
        for (ModuleCode module : modules) {
            enable(id, module);
        }
        return id;
    }

    public void enable(UUID agencyId, ModuleCode module) {
        TenantContext.runAsPlatform(() -> moduleActivation.enable(agencyId, module));
    }

    public void disable(UUID agencyId, ModuleCode module) {
        TenantContext.runAsPlatform(() -> moduleActivation.disable(agencyId, module));
    }

    /** Abonnement actif de l'agence a ce palier (quota d'utilisateurs : STARTER 3, PRO 10, BUSINESS illimite). */
    public void subscribe(UUID agencyId, String planCode) {
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> subscriptions.save(
                Subscription.start(agencyId, planCode, SubscriptionPeriod.MONTHLY, LocalDate.of(2026, 12, 1)))));
    }

    /** Passe l'agence au statut CLOSED (aucune transition du domaine n'y mene encore). */
    public void close(UUID agencyId) {
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE agency SET status = 'CLOSED' WHERE id = :id")
                .setParameter("id", agencyId)
                .executeUpdate()));
    }

    /** Passe l'agence au statut SUSPENDED. */
    public void suspend(UUID agencyId) {
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(
                status -> agencies.findById(agencyId).orElseThrow().suspend()));
    }

    /** Vrai si une agence porte ce sous-domaine (lecture en perimetre plateforme). */
    public boolean agencyExists(String subdomain) {
        Boolean exists = TenantContext.callAsPlatform(() -> transaction.execute(
                status -> agencies.existsBySubdomain(subdomain)));
        return Boolean.TRUE.equals(exists);
    }

    /** Ligne agency_module (vide si le module n'a jamais ete souscrit). */
    public Optional<AgencyModule> agencyModule(UUID agencyId, ModuleCode module) {
        return TenantContext.callAsPlatform(() -> transaction.execute(
                status -> agencyModules.findById(new AgencyModuleId(agencyId, module.name()))));
    }

    /** Supprime les agences dont le sous-domaine se termine par le suffixe, et leurs donnees. */
    public void deleteAgenciesWithSuffix(String suffix) {
        deleteAuditEntries("%" + suffix);
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> {
            String pattern = "%" + suffix;
            for (String statement : CLEANUP) {
                entityManager.createNativeQuery(statement).setParameter("pattern", pattern).executeUpdate();
            }
        }));
    }

    /**
     * Le journal d'audit est en ajout seul : le role applicatif ne peut pas supprimer ses entrees
     * (migration V5). Le nettoyage des tests passe donc par une connexion directe de la source de
     * donnees, avec le compte proprietaire des tables (sans SET ROLE, hors RLS). Tests uniquement.
     */
    private void deleteAuditEntries(String pattern) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM audit_log WHERE agency_id IN (SELECT id FROM agency WHERE subdomain LIKE ?)")) {
            delete.setString(1, pattern);
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
