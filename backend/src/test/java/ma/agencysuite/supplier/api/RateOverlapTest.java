package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.supplier.SupplierApi.expectProblem;
import static ma.agencysuite.supplier.SupplierApi.rate;
import static ma.agencysuite.supplier.SupplierApi.room;
import static ma.agencysuite.supplier.SupplierApi.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.supplier.SupplierApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Non-chevauchement des tarifs : pour une meme prestation et une meme base, deux tarifs non
 * archives n'ont jamais de jour commun (dates incluses), y compris pour deux creations simultanees.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateOverlapTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private SupplierApi api;
    private UUID agencyId;
    private UUID room;
    private UUID winter;

    @BeforeEach
    void createCatalog() throws Exception {
        api = new SupplierApi(mvc);
        agencyId = data.createAgency("overlap-" + suffix);
        UUID riad = api.createSupplierOk(agency(agencyId), supplier("RIAD", "Riad Atlas Test"));
        room = api.createProductOk(agency(agencyId), riad, room("Chambre double"));
        winter = api.createRateOk(agency(agencyId), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aPartialOverlapIsRefusedWithTheConflictingRate() throws Exception {
        api.createRateOk(agency(agencyId), room, rate("DOUBLE", "2027-07-01", "2027-12-31", "120", null));

        api.createRate(agency(agencyId), room, rate("DOUBLE", "2027-06-15", "2027-07-15", "110", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:rate-overlap"))
                .andExpect(jsonPath("$.title").value("Périodes de tarifs qui se chevauchent"))
                .andExpect(jsonPath("$.conflictingRateId").value(winter.toString()))
                .andExpect(jsonPath("$.validFrom").value("2027-01-01"))
                .andExpect(jsonPath("$.validTo").value("2027-06-30"));
    }

    @Test
    void aPeriodInsideOrAroundAnotherIsRefused() throws Exception {
        expectProblem(api.createRate(agency(agencyId), room, rate("DOUBLE", "2027-03-01", "2027-03-31", "90", null)),
                409, "rate-overlap");
        expectProblem(api.createRate(agency(agencyId), room, rate("DOUBLE", "2026-12-01", "2027-07-31", "90", null)),
                409, "rate-overlap");
    }

    @Test
    void theSameBoundaryDayIsAnOverlap() throws Exception {
        expectProblem(api.createRate(agency(agencyId), room, rate("DOUBLE", "2027-06-30", "2027-12-31", "120", null)),
                409, "rate-overlap");
        expectProblem(api.createRate(agency(agencyId), room, rate("DOUBLE", "2026-07-01", "2027-01-01", "80", null)),
                409, "rate-overlap");
        api.createRate(agency(agencyId), room, rate("DOUBLE", "2027-07-01", "2027-12-31", "120", null))
                .andExpect(status().isCreated());
        api.createRate(agency(agencyId), room, rate("DOUBLE", "2026-07-01", "2026-12-31", "80", null))
                .andExpect(status().isCreated());
    }

    @Test
    void differentBasesOrProductsMayOverlap() throws Exception {
        api.createRate(agency(agencyId), room, rate("SINGLE", "2027-01-01", "2027-06-30", "80", null))
                .andExpect(status().isCreated());
        api.createRate(agency(agencyId), room, rate("PER_PERSON", "2027-03-01", "2027-03-31", "50", null))
                .andExpect(status().isCreated());
        UUID riad = UUID.fromString(com.jayway.jsonpath.JsonPath.read(api.getProduct(agency(agencyId), room)
                .andReturn().getResponse().getContentAsString(), "$.supplier.id"));
        UUID suite = api.createProductOk(agency(agencyId), riad, room("Suite"));
        api.createRate(agency(agencyId), suite, rate("DOUBLE", "2027-01-01", "2027-06-30", "150", null))
                .andExpect(status().isCreated());
    }

    @Test
    void anArchivedRateIsIgnoredButItsRestorationIsChecked() throws Exception {
        api.archiveRate(agency(agencyId), room, winter).andExpect(status().isOk());
        UUID replacement = api.createRateOk(agency(agencyId), room,
                rate("DOUBLE", "2027-01-01", "2027-06-30", "97", null));

        api.restoreRate(agency(agencyId), room, winter)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:rate-overlap"))
                .andExpect(jsonPath("$.conflictingRateId").value(replacement.toString()));
        api.listRates(agency(agencyId), room, "archived", "true")
                .andExpect(jsonPath("$[*].id").value(contains(winter.toString())));

        api.archiveRate(agency(agencyId), room, replacement).andExpect(status().isOk());
        api.restoreRate(agency(agencyId), room, winter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void anUpdateIsCheckedAgainstTheOtherRatesOnly() throws Exception {
        UUID summer = api.createRateOk(agency(agencyId), room, rate("DOUBLE", "2027-07-01", "2027-12-31", "120", null));

        // Le tarif ne se chevauche pas avec lui-meme.
        api.updateRate(agency(agencyId), room, winter, 0, rate("DOUBLE", "2027-01-01", "2027-06-15", "95", null))
                .andExpect(status().isOk());
        api.updateRate(agency(agencyId), room, summer, 0, rate("DOUBLE", "2027-06-15", "2027-12-31", "120", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictingRateId").value(winter.toString()));
        api.updateRate(agency(agencyId), room, summer, 0, rate("DOUBLE", "2027-06-16", "2027-12-31", "120", null))
                .andExpect(status().isOk());
    }

    /**
     * Deux creations simultanees de tarifs qui se chevauchent pour la meme prestation et la meme
     * base : le verrou de la prestation les serialise, une seule reussit, l'autre recoit 409.
     */
    @Test
    void twoSimultaneousOverlappingCreationsOnlyOneSucceeds() throws Exception {
        for (int round = 0; round < 5; round++) {
            String from = "2028-0" + (round + 1) + "-01";
            String to = "2028-0" + (round + 1) + "-20";
            List<Integer> statuses = createConcurrently(
                    rate("DOUBLE", from, to, "100", null), rate("DOUBLE", from, to, "101", null));

            assertThat(statuses).as("tour " + round).containsExactlyInAnyOrder(201, 409);
        }
        api.listRates(agency(agencyId), room, "on", "2028-01-10")
                .andExpect(jsonPath("$.length()").value(1));
    }

    private List<Integer> createConcurrently(java.util.Map<String, Object> first, java.util.Map<String, Object> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var body : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return api.createRate(agency(agencyId), room, body).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
