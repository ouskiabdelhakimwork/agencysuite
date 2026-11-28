package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.RequestApi.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.RequestApi;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Statut d'une demande : transitions autorisees et interdites (tableau 2.4 du plan), etat final
 * WON, idempotence, raison de perte obligatoire et effacee a la reouverture, date du changement,
 * version perimee.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestStatusTest {

    private static final List<String> STATUSES = List.of("NEW", "QUOTING", "QUOTE_SENT", "WON", "LOST");

    /** Transitions autorisees, "depuis>vers". */
    private static final Set<String> ALLOWED = Set.of("NEW>QUOTING", "NEW>LOST", "QUOTING>QUOTE_SENT",
            "QUOTING>LOST", "QUOTE_SENT>QUOTING", "QUOTE_SENT>WON", "QUOTE_SENT>LOST", "LOST>NEW");

    /** Chemin autorise pour amener une nouvelle demande a chaque statut. */
    private static final Map<String, List<String>> PATH = Map.of(
            "NEW", List.of(),
            "QUOTING", List.of("QUOTING"),
            "QUOTE_SENT", List.of("QUOTING", "QUOTE_SENT"),
            "WON", List.of("QUOTING", "QUOTE_SENT", "WON"),
            "LOST", List.of("LOST"));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private RequestApi api;
    private CrmApi customers;
    private UUID agencyId;
    private UUID atlas;

    @BeforeEach
    void createAgency() throws Exception {
        api = new RequestApi(mvc);
        customers = new CrmApi(mvc);
        agencyId = data.createAgency("reqs-" + suffix);
        atlas = customers.createOk(agency(agencyId), CrmApi.company("Atlas Pharma Test"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void everyAllowedTransitionSucceeds() throws Exception {
        for (String transition : ALLOWED) {
            String from = transition.substring(0, transition.indexOf('>'));
            String to = transition.substring(transition.indexOf('>') + 1);
            UUID id = requestIn(from);

            api.changeStatus(agency(agencyId), id, to, "LOST".equals(to) ? "NO_RESPONSE" : null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(to));
        }
    }

    @Test
    void everyOtherTransitionIsRefusedWithFromAndTo() throws Exception {
        int refused = 0;
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                if (from.equals(to) || ALLOWED.contains(from + ">" + to)) {
                    continue;
                }
                UUID id = requestIn(from);
                long version = api.version(agency(agencyId), id);
                customers.expectProblem(api.changeStatus(agency(agencyId), id, to,
                                "LOST".equals(to) ? "PRICE" : null, null), 409, "invalid-status-transition");
                api.changeStatus(agency(agencyId), id, to, "LOST".equals(to) ? "PRICE" : null, null)
                        .andExpect(jsonPath("$.from").value(from))
                        .andExpect(jsonPath("$.to").value(to));
                api.get(agency(agencyId), id)
                        .andExpect(jsonPath("$.status").value(from))
                        .andExpect(jsonPath("$.version").value(version));
                refused++;
            }
        }
        assertThat(refused).isEqualTo(12);
    }

    @Test
    void wonIsFinal() throws Exception {
        UUID id = requestIn("WON");
        for (String to : List.of("NEW", "QUOTING", "QUOTE_SENT", "LOST")) {
            customers.expectProblem(api.changeStatus(agency(agencyId), id, to, "LOST".equals(to) ? "PRICE" : null,
                    null), 409, "invalid-status-transition");
        }
    }

    @Test
    void movingToTheCurrentStatusChangesNothing() throws Exception {
        UUID id = requestIn("LOST");
        String changedAt = api.read(agency(agencyId), id, "$.statusChangedAt");
        long version = api.version(agency(agencyId), id);

        api.changeStatus(agency(agencyId), id, "LOST", "COMPETITOR", "autre agence")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReason").value("PRICE"))
                .andExpect(jsonPath("$.lostComment").value(nullValue()))
                .andExpect(jsonPath("$.statusChangedAt").value(changedAt))
                .andExpect(jsonPath("$.version").value(version));

        UUID fresh = requestIn("NEW");
        api.changeStatus(agency(agencyId), fresh, "NEW", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void losingARequestRequiresAReason() throws Exception {
        UUID id = requestIn("QUOTE_SENT");

        customers.expectProblem(api.changeStatus(agency(agencyId), id, "LOST", null, "trop cher"), 400,
                "validation-failed");
        customers.expectProblem(api.changeStatus(agency(agencyId), id, "LOST", "TOO_EXPENSIVE", null), 400,
                "malformed-request");
        customers.expectProblem(api.changeStatus(agency(agencyId), id, "LOST", "OTHER", "x".repeat(501)), 400,
                "validation-failed");
        api.get(agency(agencyId), id).andExpect(jsonPath("$.status").value("QUOTE_SENT"));

        api.changeStatus(agency(agencyId), id, "LOST", "PRICE", "  Budget trop juste  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReason").value("PRICE"))
                .andExpect(jsonPath("$.lostComment").value("Budget trop juste"));
    }

    @Test
    void otherIsAcceptedWithoutAComment() throws Exception {
        UUID id = requestIn("NEW");

        api.changeStatus(agency(agencyId), id, "LOST", "OTHER", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").value("OTHER"))
                .andExpect(jsonPath("$.lostComment").value(nullValue()));
    }

    @Test
    void aReasonSentForAnotherStatusIsIgnored() throws Exception {
        UUID id = requestIn("NEW");

        api.changeStatus(agency(agencyId), id, "QUOTING", "PRICE", "sans objet")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").value(nullValue()))
                .andExpect(jsonPath("$.lostComment").value(nullValue()));
    }

    @Test
    void reopeningClearsTheReasonAndTheComment() throws Exception {
        UUID id = requestIn("NEW");
        api.changeStatus(agency(agencyId), id, "LOST", "DATES_UNAVAILABLE", "hotel complet")
                .andExpect(status().isOk());

        api.changeStatus(agency(agencyId), id, "NEW", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.lostReason").value(nullValue()))
                .andExpect(jsonPath("$.lostComment").value(nullValue()));
    }

    @Test
    void statusChangedAtMovesWithEachEffectiveChange() throws Exception {
        UUID id = requestIn("NEW");
        Instant created = Instant.parse(api.read(agency(agencyId), id, "$.statusChangedAt"));
        Instant createdAt = Instant.parse(api.read(agency(agencyId), id, "$.createdAt"));
        assertThat(created).isCloseTo(createdAt, org.assertj.core.api.Assertions.within(5,
                java.time.temporal.ChronoUnit.SECONDS));

        api.moveTo(agency(agencyId), id, "QUOTING");
        Instant quoting = Instant.parse(api.read(agency(agencyId), id, "$.statusChangedAt"));
        assertThat(quoting).isAfter(created);

        api.update(agency(agencyId), id, api.version(agency(agencyId), id), RequestApi.with(request(atlas, "EMAIL"),
                "ownerUserId", api.read(agency(agencyId), id, "$.ownerUserId"))).andExpect(status().isOk());
        assertThat(Instant.parse(api.read(agency(agencyId), id, "$.statusChangedAt"))).isEqualTo(quoting);
    }

    @Test
    void aStaleVersionIsRefused() throws Exception {
        UUID id = requestIn("NEW");
        api.changeStatus(agency(agencyId), id, 0, "QUOTING", null, null).andExpect(status().isOk());

        customers.expectProblem(api.changeStatus(agency(agencyId), id, 0, "QUOTE_SENT", null, null), 409,
                "stale-version");
        customers.expectProblem(api.changeStatus(agency(agencyId), id, 0, "QUOTING", null, null), 409,
                "stale-version");
        api.get(agency(agencyId), id).andExpect(jsonPath("$.status").value("QUOTING"));
    }

    @Test
    void theVersionIsRequiredAndAnUnknownRequestIsNotFound() throws Exception {
        UUID id = requestIn("NEW");

        customers.expectProblem(api.perform(MockMvcRequestBuilders.post(RequestApi.REQUESTS + "/" + id + "/status")
                .with(agency(agencyId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"QUOTING\"}")), 400, "validation-failed");
        customers.expectProblem(api.changeStatus(agency(agencyId), UUID.randomUUID(), 0, "QUOTING", null, null),
                404, "not-found");
    }

    /** Nouvelle demande amenee au statut par le chemin autorise. */
    private UUID requestIn(String target) throws Exception {
        UUID id = api.createOk(agency(agencyId), request(atlas, "PHONE"));
        for (String step : PATH.get(target)) {
            api.moveTo(agency(agencyId), id, step);
        }
        return id;
    }
}
