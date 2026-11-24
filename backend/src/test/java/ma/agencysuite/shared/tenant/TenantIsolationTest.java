package ma.agencysuite.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Isolation entre agences (04-modele-de-donnees.md, section 11) : l'agence A ne peut ni lire,
 * ni modifier, ni compter les donnees de l'agence B. Deux barrieres sont verifiees :
 * le filtre Hibernate (@TenantId) et les politiques Row Level Security de PostgreSQL,
 * qui bloquent meme une requete SQL directe.
 *
 * <p>Les donnees sont reellement enregistrees (chaque bloc est une transaction validee),
 * puis supprimees apres chaque test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantIsolationTest {

    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private TenantProbeRepository probes;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    private AgencyId agencyA;
    private AgencyId agencyB;

    @BeforeEach
    void createTwoAgencies() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        agencyA = asPlatform(() -> new AgencyId(agencies.save(Agency.createCustomer("Agence A", "iso-a-" + suffix)).getId()));
        agencyB = asPlatform(() -> new AgencyId(agencies.save(Agency.createCustomer("Agence B", "iso-b-" + suffix)).getId()));
    }

    @AfterEach
    void deleteTestData() {
        asPlatform(() -> {
            entityManager.createNativeQuery("DELETE FROM tenant_probe WHERE agency_id IN (:a, :b)")
                    .setParameter("a", agencyA.value()).setParameter("b", agencyB.value()).executeUpdate();
            return entityManager.createNativeQuery("DELETE FROM agency WHERE id IN (:a, :b)")
                    .setParameter("a", agencyA.value()).setParameter("b", agencyB.value()).executeUpdate();
        });
    }

    @Test
    void eachAgencySeesAndCountsOnlyItsOwnRows() {
        asAgency(agencyA, () -> probes.saveAll(List.of(new TenantProbe("A1"), new TenantProbe("A2"))));
        asAgency(agencyB, () -> probes.save(new TenantProbe("B1")));

        List<TenantProbe> seenByA = asAgency(agencyA, () -> probes.findAll());
        List<TenantProbe> seenByB = asAgency(agencyB, () -> probes.findAll());
        long countedByB = asAgency(agencyB, () -> probes.count());

        assertThat(seenByA).extracting(TenantProbe::getLabel).containsExactlyInAnyOrder("A1", "A2");
        assertThat(seenByB).extracting(TenantProbe::getLabel).containsExactly("B1");
        assertThat(countedByB).isEqualTo(1);
    }

    @Test
    void agencyIdIsFilledAutomaticallyFromTheCurrentAgency() {
        UUID id = asAgency(agencyA, () -> probes.save(new TenantProbe("A1")).getId());

        TenantProbe reloaded = asPlatform(() -> probes.findById(id).orElseThrow());
        assertThat(reloaded.getAgencyId()).isEqualTo(agencyA);
    }

    @Test
    void agencyCannotReadAnotherAgencysRowEvenByItsId() {
        UUID idOfA = asAgency(agencyA, () -> probes.save(new TenantProbe("A1")).getId());

        assertThat(asAgency(agencyB, () -> probes.findById(idOfA))).isEmpty();
    }

    @Test
    void databaseBlocksDirectSqlAcrossAgencies() {
        UUID idOfA = asAgency(agencyA, () -> probes.save(new TenantProbe("A1")).getId());
        asAgency(agencyB, () -> probes.save(new TenantProbe("B1")));

        // Requetes SQL directes : le filtre Hibernate ne s'applique pas, seule la RLS protege.
        Number visibleToB = asAgency(agencyB,
                () -> (Number) entityManager.createNativeQuery("SELECT count(*) FROM tenant_probe").getSingleResult());
        int updatedByB = asAgency(agencyB, () -> entityManager
                .createNativeQuery("UPDATE tenant_probe SET label = 'pirate' WHERE id = :id")
                .setParameter("id", idOfA).executeUpdate());

        assertThat(visibleToB.longValue()).isEqualTo(1);
        assertThat(updatedByB).isZero();
        assertThat(asPlatform(() -> probes.findById(idOfA).orElseThrow().getLabel())).isEqualTo("A1");
    }

    @Test
    void databaseRefusesToWriteARowForAnotherAgency() {
        assertThatThrownBy(() -> asAgency(agencyB, () -> entityManager
                .createNativeQuery("INSERT INTO tenant_probe (id, agency_id, label, created_at, updated_at, version) "
                        + "VALUES (:id, :agency, 'intrus', now(), now(), 0)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("agency", agencyA.value())
                .executeUpdate()))
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    @Test
    void withoutAnAgencyNothingIsVisibleAndNothingCanBeWritten() {
        asAgency(agencyA, () -> probes.save(new TenantProbe("A1")));

        long probesWithoutScope = withoutScope(() -> probes.count());
        long agenciesWithoutScope = withoutScope(() -> agencies.count());

        assertThat(probesWithoutScope).isZero();
        assertThat(agenciesWithoutScope).isZero();
        assertThatThrownBy(() -> withoutScope(() -> probes.saveAndFlush(new TenantProbe("orphelin"))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void agencyTableItselfIsIsolated() {
        List<Agency> seenByA = asAgency(agencyA, () -> agencies.findAll());

        assertThat(seenByA)
                .extracting(agency -> new AgencyId(agency.getId()))
                .containsExactly(agencyA);
    }

    @Test
    void platformSeesEveryAgency() {
        asAgency(agencyA, () -> probes.save(new TenantProbe("A1")));
        asAgency(agencyB, () -> probes.save(new TenantProbe("B1")));

        List<TenantProbe> allProbes = asPlatform(() -> probes.findAll());
        List<Agency> allAgencies = asPlatform(() -> agencies.findAll());

        assertThat(allProbes).extracting(TenantProbe::getLabel).contains("A1", "B1");
        assertThat(allAgencies)
                .extracting(agency -> new AgencyId(agency.getId()))
                .contains(agencyA, agencyB);
    }

    /** Garde-fou : toute table portant agency_id doit avoir la Row Level Security activee. */
    @Test
    @SuppressWarnings("unchecked")
    void everyTableWithAnAgencyIdHasRowLevelSecurity() {
        List<String> unprotected = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relkind = 'r'
                  AND NOT c.relrowsecurity
                  AND EXISTS (SELECT 1 FROM pg_attribute a
                              WHERE a.attrelid = c.oid AND a.attname = 'agency_id' AND NOT a.attisdropped)
                """).getResultList());

        assertThat(unprotected).as("tables portant agency_id sans Row Level Security").isEmpty();
    }

    private <T> T asAgency(AgencyId agency, Supplier<T> work) {
        return TenantContext.callAsAgency(agency, () -> transaction.execute(status -> work.get()));
    }

    private <T> T asPlatform(Supplier<T> work) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> work.get()));
    }

    private <T> T withoutScope(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
