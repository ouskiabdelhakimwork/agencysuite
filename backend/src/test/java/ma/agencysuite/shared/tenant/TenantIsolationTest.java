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

    /** Tables du module iam (etape 3, lots 2 et 4) : politique attendue sur chacune. */
    @Test
    @SuppressWarnings("unchecked")
    void iamTablesHaveTheirPolicy() {
        List<Object[]> policies = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT tablename, policyname FROM pg_policies
                WHERE schemaname = 'public'
                  AND tablename IN ('user_account', 'user_role', 'platform_user', 'audit_log')
                ORDER BY tablename
                """).getResultList());

        assertThat(policies)
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactly("audit_log:tenant_isolation", "platform_user:platform_only",
                        "user_account:tenant_isolation", "user_role:tenant_isolation");
    }

    /**
     * Un role ne peut etre rattache qu'a un utilisateur de la meme agence (cle etrangere composee
     * user_role (user_id, agency_id) vers user_account (id, agency_id), migration V5) : meme en
     * perimetre plateforme, ou la RLS laisse tout passer, l'insertion incoherente est rejetee.
     */
    @Test
    void aRoleCannotBeAttachedToAUserOfAnotherAgency() {
        UUID userOfA = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO user_account (id, agency_id, external_idp_id, email, status, created_at, updated_at, version)
                VALUES (:id, :a, :sub, 'fk-check@example.test', 'ACTIVE', now(), now(), 0)
                """).setParameter("id", userOfA).setParameter("a", agencyA.value())
                .setParameter("sub", UUID.randomUUID().toString()).executeUpdate());
        try {
            assertThatThrownBy(() -> asPlatform(() -> entityManager.createNativeQuery("""
                    INSERT INTO user_role (id, agency_id, user_id, role_code, created_at, updated_at, version)
                    VALUES (:id, :b, :user, 'SALES', now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("b", agencyB.value())
                    .setParameter("user", userOfA).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("fk_user_role_user_same_agency");
        } finally {
            asPlatform(() -> entityManager.createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", userOfA).executeUpdate());
        }
    }

    /** Tables du module crm (etape 5, lot 1) : politique attendue sur chacune. */
    @Test
    @SuppressWarnings("unchecked")
    void crmTablesHaveTheirPolicy() {
        List<Object[]> policies = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT tablename, policyname FROM pg_policies
                WHERE schemaname = 'public' AND tablename IN ('customer', 'contact')
                ORDER BY tablename
                """).getResultList());

        assertThat(policies)
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactly("contact:tenant_isolation", "customer:tenant_isolation");
    }

    /**
     * Un contact reste dans l'agence de son client (cle composee contact (customer_id, agency_id)
     * vers customer (id, agency_id), migration V6), meme en perimetre plateforme.
     */
    @Test
    void aContactCannotBeAttachedToACustomerOfAnotherAgency() {
        UUID customerOfA = insertCustomer(agencyA, null);
        try {
            assertThatThrownBy(() -> asPlatform(() -> entityManager.createNativeQuery("""
                    INSERT INTO contact (id, agency_id, customer_id, full_name, is_primary, created_at, updated_at, version)
                    VALUES (:id, :b, :customer, 'Contact Intrus', false, now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("b", agencyB.value())
                    .setParameter("customer", customerOfA).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("fk_contact_customer_same_agency");
        } finally {
            deleteRow("customer", customerOfA);
        }
    }

    /** Le responsable d'un client est un utilisateur de la meme agence (cle composee, migration V6). */
    @Test
    void aCustomerOwnerMustBelongToTheSameAgency() {
        UUID userOfA = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO user_account (id, agency_id, external_idp_id, email, status, created_at, updated_at, version)
                VALUES (:id, :a, :sub, 'owner-check@example.test', 'ACTIVE', now(), now(), 0)
                """).setParameter("id", userOfA).setParameter("a", agencyA.value())
                .setParameter("sub", UUID.randomUUID().toString()).executeUpdate());
        try {
            assertThatThrownBy(() -> insertCustomer(agencyB, userOfA))
                    .rootCause()
                    .hasMessageContaining("fk_customer_owner_same_agency");
        } finally {
            deleteRow("user_account", userOfA);
        }
    }

    /** Tables des voyageurs (etape 5, lot 2) : politique attendue sur chacune. */
    @Test
    @SuppressWarnings("unchecked")
    void travelerTablesHaveTheirPolicy() {
        List<Object[]> policies = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT tablename, policyname FROM pg_policies
                WHERE schemaname = 'public' AND tablename IN ('traveler', 'customer_traveler')
                ORDER BY tablename
                """).getResultList());

        assertThat(policies)
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactly("customer_traveler:tenant_isolation", "traveler:tenant_isolation");
    }

    /**
     * Un lien voyageur &harr; client ne traverse jamais deux agences (cles composees avec agency_id
     * vers customer et traveler, migration V8), meme en perimetre plateforme ou la RLS laisse tout
     * passer.
     */
    @Test
    void aTravelerLinkCannotCrossAgencies() {
        UUID customerOfA = insertCustomer(agencyA, null);
        UUID travelerOfB = insertTraveler(agencyB);
        try {
            assertThatThrownBy(() -> insertLink(agencyA, customerOfA, travelerOfB))
                    .rootCause()
                    .hasMessageContaining("fk_customer_traveler_traveler_same_agency");
            assertThatThrownBy(() -> insertLink(agencyB, customerOfA, travelerOfB))
                    .rootCause()
                    .hasMessageContaining("fk_customer_traveler_customer_same_agency");
        } finally {
            deleteRow("traveler", travelerOfB);
            deleteRow("customer", customerOfA);
        }
    }

    /** Liens et voyageurs d'une autre agence : invisibles et non modifiables, meme en SQL direct. */
    @Test
    void travelersAndLinksAreIsolatedInSql() {
        UUID customerOfA = insertCustomer(agencyA, null);
        UUID travelerOfA = insertTraveler(agencyA);
        insertLink(agencyA, customerOfA, travelerOfA);
        try {
            Number travelersSeenByB = asAgency(agencyB, () -> (Number) entityManager
                    .createNativeQuery("SELECT count(*) FROM traveler").getSingleResult());
            Number linksSeenByB = asAgency(agencyB, () -> (Number) entityManager
                    .createNativeQuery("SELECT count(*) FROM customer_traveler").getSingleResult());
            int deletedByB = asAgency(agencyB, () -> entityManager
                    .createNativeQuery("DELETE FROM customer_traveler WHERE traveler_id = :id")
                    .setParameter("id", travelerOfA).executeUpdate());
            Number linksSeenByA = asAgency(agencyA, () -> (Number) entityManager
                    .createNativeQuery("SELECT count(*) FROM customer_traveler").getSingleResult());

            assertThat(travelersSeenByB.longValue()).isZero();
            assertThat(linksSeenByB.longValue()).isZero();
            assertThat(deletedByB).isZero();
            assertThat(linksSeenByA.longValue()).isEqualTo(1);
            assertThatThrownBy(() -> asAgency(agencyB, () -> entityManager.createNativeQuery("""
                    INSERT INTO customer_traveler (agency_id, customer_id, traveler_id, created_at)
                    VALUES (:a, :customer, :traveler, now())
                    """).setParameter("a", agencyA.value()).setParameter("customer", customerOfA)
                    .setParameter("traveler", UUID.randomUUID()).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("row-level security");
        } finally {
            asPlatform(() -> entityManager.createNativeQuery("DELETE FROM customer_traveler WHERE traveler_id = :id")
                    .setParameter("id", travelerOfA).executeUpdate());
            deleteRow("traveler", travelerOfA);
            deleteRow("customer", customerOfA);
        }
    }

    /** Tables du module supplier (etape 5, lot 3) : politique attendue sur chacune. */
    @Test
    @SuppressWarnings("unchecked")
    void supplierTablesHaveTheirPolicy() {
        List<Object[]> policies = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT tablename, policyname FROM pg_policies
                WHERE schemaname = 'public' AND tablename IN ('supplier', 'product', 'rate')
                ORDER BY tablename
                """).getResultList());

        assertThat(policies)
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactly("product:tenant_isolation", "rate:tenant_isolation", "supplier:tenant_isolation");
    }

    /**
     * Une prestation reste dans l'agence de son fournisseur et un tarif dans celle de sa prestation
     * (cles composees avec agency_id, migration V9), meme en perimetre plateforme ou la RLS laisse
     * tout passer.
     */
    @Test
    void productsAndRatesCannotCrossAgencies() {
        UUID supplierOfA = insertSupplier(agencyA);
        UUID productOfA = insertProduct(agencyA, supplierOfA);
        try {
            assertThatThrownBy(() -> insertProduct(agencyB, supplierOfA))
                    .rootCause()
                    .hasMessageContaining("fk_product_supplier_same_agency");
            assertThatThrownBy(() -> insertRate(agencyB, productOfA))
                    .rootCause()
                    .hasMessageContaining("fk_rate_product_same_agency");
        } finally {
            deleteRow("product", productOfA);
            deleteRow("supplier", supplierOfA);
        }
    }

    /** Fournisseurs, prestations et tarifs d'une autre agence : invisibles et non modifiables en SQL direct. */
    @Test
    void suppliersProductsAndRatesAreIsolatedInSql() {
        UUID supplierOfA = insertSupplier(agencyA);
        UUID productOfA = insertProduct(agencyA, supplierOfA);
        UUID rateOfA = insertRate(agencyA, productOfA);
        try {
            for (String table : List.of("supplier", "product", "rate")) {
                Number seenByB = asAgency(agencyB, () -> (Number) entityManager
                        .createNativeQuery("SELECT count(*) FROM " + table).getSingleResult());
                Number seenByA = asAgency(agencyA, () -> (Number) entityManager
                        .createNativeQuery("SELECT count(*) FROM " + table).getSingleResult());
                assertThat(seenByB.longValue()).as(table).isZero();
                assertThat(seenByA.longValue()).as(table).isEqualTo(1);
            }
            int updatedByB = asAgency(agencyB, () -> entityManager
                    .createNativeQuery("UPDATE rate SET purchase_amount = 1 WHERE id = :id")
                    .setParameter("id", rateOfA).executeUpdate());
            assertThat(updatedByB).isZero();
            assertThatThrownBy(() -> asAgency(agencyB, () -> entityManager.createNativeQuery("""
                    INSERT INTO supplier (id, agency_id, type, name, name_key, created_at, updated_at, version)
                    VALUES (:id, :a, 'HOTEL', 'Intrus', 'intrus', now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("a", agencyA.value()).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("row-level security");
        } finally {
            deleteRow("rate", rateOfA);
            deleteRow("product", productOfA);
            deleteRow("supplier", supplierOfA);
        }
    }

    /** Table des demandes (etape 5, lot 4) : politique attendue. */
    @Test
    @SuppressWarnings("unchecked")
    void requestTableHasItsPolicy() {
        List<Object[]> policies = asPlatform(() -> entityManager.createNativeQuery("""
                SELECT tablename, policyname FROM pg_policies
                WHERE schemaname = 'public' AND tablename = 'customer_request'
                """).getResultList());

        assertThat(policies)
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactly("customer_request:tenant_isolation");
    }

    /**
     * Une demande reste dans l'agence de son client et de son responsable (cles composees avec
     * agency_id, migration V10), meme en perimetre plateforme ou la RLS laisse tout passer.
     */
    @Test
    void aRequestCannotPointToACustomerOrAnOwnerOfAnotherAgency() {
        UUID ownerOfA = insertUser(agencyA);
        UUID ownerOfB = insertUser(agencyB);
        UUID customerOfA = insertCustomer(agencyA, null);
        try {
            assertThatThrownBy(() -> insertRequest(agencyB, customerOfA, ownerOfB))
                    .rootCause()
                    .hasMessageContaining("fk_request_customer_same_agency");
            assertThatThrownBy(() -> insertRequest(agencyA, customerOfA, ownerOfB))
                    .rootCause()
                    .hasMessageContaining("fk_request_owner_same_agency");
        } finally {
            deleteRow("customer", customerOfA);
            deleteRow("user_account", ownerOfA);
            deleteRow("user_account", ownerOfB);
        }
    }

    /** Une demande perdue a toujours une raison, une autre jamais (ck_request_lost, migration V10). */
    @Test
    void aLostRequestAlwaysHasAReasonAndOnlyThen() {
        UUID owner = insertUser(agencyA);
        UUID customer = insertCustomer(agencyA, null);
        try {
            assertThatThrownBy(() -> asPlatform(() -> entityManager.createNativeQuery("""
                    INSERT INTO customer_request (id, agency_id, customer_id, owner_user_id, channel, status,
                        status_changed_at, search_key, created_at, updated_at, version)
                    VALUES (:id, :a, :customer, :owner, 'PHONE', 'LOST', now(), '', now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("a", agencyA.value())
                    .setParameter("customer", customer).setParameter("owner", owner).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("ck_request_lost");
            assertThatThrownBy(() -> asPlatform(() -> entityManager.createNativeQuery("""
                    INSERT INTO customer_request (id, agency_id, customer_id, owner_user_id, channel, status,
                        lost_reason, status_changed_at, search_key, created_at, updated_at, version)
                    VALUES (:id, :a, :customer, :owner, 'PHONE', 'NEW', 'PRICE', now(), '', now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("a", agencyA.value())
                    .setParameter("customer", customer).setParameter("owner", owner).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("ck_request_lost");
        } finally {
            deleteRow("customer", customer);
            deleteRow("user_account", owner);
        }
    }

    /** Demandes d'une autre agence : invisibles, non comptees et non modifiables en SQL direct. */
    @Test
    void requestsAreIsolatedInSql() {
        UUID owner = insertUser(agencyA);
        UUID customer = insertCustomer(agencyA, null);
        UUID request = insertRequest(agencyA, customer, owner);
        try {
            Number seenByB = asAgency(agencyB, () -> (Number) entityManager
                    .createNativeQuery("SELECT count(*) FROM customer_request").getSingleResult());
            Number seenByA = asAgency(agencyA, () -> (Number) entityManager
                    .createNativeQuery("SELECT count(*) FROM customer_request").getSingleResult());
            int updatedByB = asAgency(agencyB, () -> entityManager
                    .createNativeQuery("UPDATE customer_request SET status = 'WON' WHERE id = :id")
                    .setParameter("id", request).executeUpdate());
            int deletedByB = asAgency(agencyB, () -> entityManager
                    .createNativeQuery("DELETE FROM customer_request WHERE id = :id")
                    .setParameter("id", request).executeUpdate());

            assertThat(seenByB.longValue()).isZero();
            assertThat(seenByA.longValue()).isEqualTo(1);
            assertThat(updatedByB).isZero();
            assertThat(deletedByB).isZero();
            assertThatThrownBy(() -> asAgency(agencyB, () -> entityManager.createNativeQuery("""
                    INSERT INTO customer_request (id, agency_id, customer_id, owner_user_id, channel,
                        status_changed_at, search_key, created_at, updated_at, version)
                    VALUES (:id, :a, :customer, :owner, 'PHONE', now(), '', now(), now(), 0)
                    """).setParameter("id", UUID.randomUUID()).setParameter("a", agencyA.value())
                    .setParameter("customer", customer).setParameter("owner", owner).executeUpdate()))
                    .rootCause()
                    .hasMessageContaining("row-level security");
        } finally {
            deleteRow("customer_request", request);
            deleteRow("customer", customer);
            deleteRow("user_account", owner);
        }
    }

    private UUID insertUser(AgencyId agency) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO user_account (id, agency_id, external_idp_id, email, status, created_at, updated_at, version)
                VALUES (:id, :agency, :sub, :email, 'ACTIVE', now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value())
                .setParameter("sub", UUID.randomUUID().toString())
                .setParameter("email", "user-" + id.toString().substring(0, 8) + "@example.test").executeUpdate());
        return id;
    }

    private UUID insertRequest(AgencyId agency, UUID customer, UUID owner) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO customer_request (id, agency_id, customer_id, owner_user_id, channel,
                    status_changed_at, search_key, created_at, updated_at, version)
                VALUES (:id, :agency, :customer, :owner, 'PHONE', now(), '', now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value()).setParameter("customer", customer)
                .setParameter("owner", owner).executeUpdate());
        return id;
    }

    private UUID insertSupplier(AgencyId agency) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO supplier (id, agency_id, type, name, name_key, created_at, updated_at, version)
                VALUES (:id, :agency, 'HOTEL', 'Hotel Test', 'hotel test', now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value()).executeUpdate());
        return id;
    }

    private UUID insertProduct(AgencyId agency, UUID supplier) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO product (id, agency_id, supplier_id, type, name, search_key, created_at, updated_at, version)
                VALUES (:id, :agency, :supplier, 'ACCOMMODATION', jsonb_build_object('fr', 'Chambre'), 'chambre', now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value()).setParameter("supplier", supplier)
                .executeUpdate());
        return id;
    }

    private UUID insertRate(AgencyId agency, UUID product) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO rate (id, agency_id, product_id, basis, valid_from, valid_to, purchase_amount,
                                  purchase_currency, created_at, updated_at, version)
                VALUES (:id, :agency, :product, 'DOUBLE', DATE '2027-01-01', DATE '2027-06-30', 95, 'MAD',
                        now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value()).setParameter("product", product)
                .executeUpdate());
        return id;
    }

    private UUID insertTraveler(AgencyId agency) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO traveler (id, agency_id, last_name, first_name, name_key, created_at, updated_at, version)
                VALUES (:id, :agency, 'Voyageur', 'Test', 'voyageur test', now(), now(), 0)
                """).setParameter("id", id).setParameter("agency", agency.value()).executeUpdate());
        return id;
    }

    private int insertLink(AgencyId agency, UUID customer, UUID traveler) {
        return asPlatform(() -> entityManager.createNativeQuery("""
                INSERT INTO customer_traveler (agency_id, customer_id, traveler_id, created_at)
                VALUES (:agency, :customer, :traveler, now())
                """).setParameter("agency", agency.value()).setParameter("customer", customer)
                .setParameter("traveler", traveler).executeUpdate());
    }

    private UUID insertCustomer(AgencyId agency, UUID owner) {
        UUID id = UUID.randomUUID();
        asPlatform(() -> {
            var insert = entityManager.createNativeQuery(owner == null
                    ? """
                      INSERT INTO customer (id, agency_id, type, name, created_at, updated_at, version)
                      VALUES (:id, :agency, 'COMPANY', 'Societe Test', now(), now(), 0)
                      """
                    : """
                      INSERT INTO customer (id, agency_id, type, name, owner_user_id, created_at, updated_at, version)
                      VALUES (:id, :agency, 'COMPANY', 'Societe Test', :owner, now(), now(), 0)
                      """).setParameter("id", id).setParameter("agency", agency.value());
            if (owner != null) {
                insert.setParameter("owner", owner);
            }
            return insert.executeUpdate();
        });
        return id;
    }

    private void deleteRow(String table, UUID id) {
        asPlatform(() -> entityManager.createNativeQuery("DELETE FROM " + table + " WHERE id = :id")
                .setParameter("id", id).executeUpdate());
    }

    /** L'equipe de l'editeur n'est jamais visible depuis une agence, meme en SQL direct. */
    @Test
    void platformUsersAreInvisibleFromAnAgency() {
        Number seenByAgency = asAgency(agencyA,
                () -> (Number) entityManager.createNativeQuery("SELECT count(*) FROM platform_user").getSingleResult());
        Number seenByPlatform = asPlatform(
                () -> (Number) entityManager.createNativeQuery("SELECT count(*) FROM platform_user").getSingleResult());

        assertThat(seenByAgency.longValue()).isZero();
        assertThat(seenByPlatform.longValue()).isPositive();
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
