package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static ma.agencysuite.crm.TravelerApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.TravelerApi;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.application.CurrentUserResolver;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.PermissionCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Date et lieu de naissance proteges par la permission {@code identity.read} (RG-14, a valider) ;
 * le lieu suit exactement la regle de la date.
 *
 * <p>Repartition de V4 (inchangee) : MANAGER, SALES et OPERATIONS ont {@code identity.read} ;
 * ACCOUNTANT ne l'a pas, mais n'a pas non plus {@code crm.write}. Aucun role systeme n'a
 * {@code crm.write} sans {@code identity.read} : pour tester les regles d'ecriture, un utilisateur
 * SALES reel voit {@code identity.read} retiree de ses permissions par un espion sur
 * {@link CurrentUserResolver} (le reste de la chaine de securite est le vrai).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerBirthDateTest {

    private static final String BIRTH_DATE = "1990-05-14";
    private static final String BIRTH_PLACE = "Meknes";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @MockitoSpyBean
    private CurrentUserResolver resolver;

    /** sub des utilisateurs dont on retire identity.read. */
    private final Set<String> withoutIdentityRead = ConcurrentHashMap.newKeySet();
    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new TravelerApi(mvc);
        agencyId = data.createAgency("birth-" + suffix);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<CurrentUser> real = (Optional<CurrentUser>) invocation.callRealMethod();
            if (!withoutIdentityRead.contains(invocation.<String>getArgument(0))) {
                return real;
            }
            return real.map(user -> {
                Set<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
                permissions.addAll(user.permissions());
                permissions.remove(PermissionCode.IDENTITY_READ);
                return new CurrentUser(user.userId(), permissions);
            });
        }).when(resolver).resolveAgencyUser(any());
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    /** Utilisateur SALES (crm.write) prive de identity.read. */
    private JwtRequestPostProcessor writerWithoutIdentityRead() {
        String sub = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");
        withoutIdentityRead.add(sub);
        return agencyUser(agencyId, sub);
    }

    /** Voyageur avec date et lieu de naissance (lieu fictif quelconque). */
    private static java.util.Map<String, Object> born(String lastName, String firstName) {
        var body = with(traveler(lastName, firstName), "birthDate", BIRTH_DATE);
        body.put("birthPlace", BIRTH_PLACE);
        return body;
    }

    @Test
    void withIdentityReadTheDateAndPlaceAreReadAndWritten() throws Exception {
        var sales = agencyUser(agencyId, iam.createUser(agencyId, UserStatus.ACTIVE, "SALES"));
        UUID id = api.createOk(sales, born("El Fassi", "Zineb"));

        api.get(sales, id)
                .andExpect(jsonPath("$.birthDate").value(BIRTH_DATE))
                .andExpect(jsonPath("$.birthDateHidden").value(false))
                .andExpect(jsonPath("$.birthPlace").value(BIRTH_PLACE))
                .andExpect(jsonPath("$.birthPlaceHidden").value(false));
        var changed = with(traveler("El Fassi", "Zineb"), "birthDate", "1991-06-15");
        changed.put("birthPlace", " Fes ");
        api.update(sales, id, 0, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1991-06-15"))
                .andExpect(jsonPath("$.birthPlace").value("Fes"));
        api.update(sales, id, 1, traveler("El Fassi", "Zineb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.birthDateHidden").value(false))
                .andExpect(jsonPath("$.birthPlace").value(nullValue()))
                .andExpect(jsonPath("$.birthPlaceHidden").value(false));
        api.get(agency(agencyId), id)
                .andExpect(jsonPath("$.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.birthPlace").value(nullValue()));
    }

    @Test
    void withoutIdentityReadTheDateAndPlaceAreHidden() throws Exception {
        UUID dated = api.createOk(agency(agencyId), born("El Fassi", "Zineb"));
        UUID placeOnly = api.createOk(agency(agencyId), with(traveler("Lieu", "Seul"), "birthPlace", BIRTH_PLACE));
        UUID undated = api.createOk(agency(agencyId), traveler("Sans", "Date"));
        var accountant = agencyUser(agencyId, iam.createUser(agencyId, UserStatus.ACTIVE, "ACCOUNTANT"));
        var writer = writerWithoutIdentityRead();

        for (JwtRequestPostProcessor reader : java.util.List.of(accountant, writer)) {
            String body = api.get(reader, dated)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.birthDate").value(nullValue()))
                    .andExpect(jsonPath("$.birthDateHidden").value(true))
                    .andExpect(jsonPath("$.birthPlace").value(nullValue()))
                    .andExpect(jsonPath("$.birthPlaceHidden").value(true))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(BIRTH_DATE).doesNotContain("1990").doesNotContain(BIRTH_PLACE);
            api.get(reader, placeOnly)
                    .andExpect(jsonPath("$.birthDateHidden").value(false))
                    .andExpect(jsonPath("$.birthPlaceHidden").value(true));
            api.get(reader, undated)
                    .andExpect(jsonPath("$.birthDate").value(nullValue()))
                    .andExpect(jsonPath("$.birthDateHidden").value(false))
                    .andExpect(jsonPath("$.birthPlace").value(nullValue()))
                    .andExpect(jsonPath("$.birthPlaceHidden").value(false));
        }
    }

    @Test
    void withoutIdentityReadSendingADateOrAPlaceIs403() throws Exception {
        UUID id = api.createOk(agency(agencyId), born("El Fassi", "Zineb"));
        var writer = writerWithoutIdentityRead();

        String created = api.expectProblem(api.create(writer, with(traveler("Autre", "Test"), "birthDate",
                "1985-01-01")), 403, "permission-denied");
        assertThat(created).contains("identity.read");
        api.expectProblem(api.create(writer, with(traveler("Autre", "Test"), "birthPlace", "Fes")), 403,
                "permission-denied");
        api.expectProblem(api.update(writer, id, 0, with(traveler("El Fassi", "Zineb"), "birthDate", "1985-01-01")),
                403, "permission-denied");
        api.expectProblem(api.update(writer, id, 0, with(traveler("El Fassi", "Zineb"), "birthPlace", "Fes")),
                403, "permission-denied");

        api.list(agency(agencyId), "").andExpect(jsonPath("$.totalElements").value(1));
        api.get(agency(agencyId), id)
                .andExpect(jsonPath("$.birthDate").value(BIRTH_DATE))
                .andExpect(jsonPath("$.birthPlace").value(BIRTH_PLACE))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void withoutIdentityReadAnUpdateWithoutDateOrPlaceKeepsThem() throws Exception {
        UUID id = api.createOk(agency(agencyId), born("El Fassi", "Zineb"));
        var writer = writerWithoutIdentityRead();

        var absent = with(traveler("El Fassi", "Zineb Test"), "birthDate", null);
        absent.put("birthPlace", null);
        api.update(writer, id, 0, absent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Zineb Test"))
                .andExpect(jsonPath("$.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.birthDateHidden").value(true))
                .andExpect(jsonPath("$.birthPlace").value(nullValue()))
                .andExpect(jsonPath("$.birthPlaceHidden").value(true));
        api.update(writer, id, 1, traveler("El Fassi", "Zineb")).andExpect(status().isOk());
        // Un lieu vide vaut absent : pas de 403, rien n'est efface.
        api.update(writer, id, 2, with(traveler("El Fassi", "Zineb"), "birthPlace", "  ")).andExpect(status().isOk());

        api.get(agency(agencyId), id)
                .andExpect(jsonPath("$.birthDate").value(BIRTH_DATE))
                .andExpect(jsonPath("$.birthPlace").value(BIRTH_PLACE));
        api.create(writer, traveler("Nouveau", "Test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.birthDateHidden").value(false))
                .andExpect(jsonPath("$.birthPlaceHidden").value(false));
    }

    @Test
    void theListAndTheSearchNeverUseTheDateOrPlace() throws Exception {
        api.createOk(agency(agencyId), born("El Fassi", "Zineb"));

        String body = api.list(agency(agencyId), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$.items[0].birthPlace").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(BIRTH_DATE).doesNotContain(BIRTH_PLACE);
        api.search(agency(agencyId), BIRTH_DATE).andExpect(jsonPath("$.totalElements").value(0));
        api.search(agency(agencyId), "1990").andExpect(jsonPath("$.totalElements").value(0));
        api.search(agency(agencyId), BIRTH_PLACE).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void nameAndBirthDateCriterionNeedsADateSentByTheCaller() throws Exception {
        UUID existing = api.createOk(agency(agencyId), with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE));

        String body = api.expectProblem(api.create(agency(agencyId),
                with(traveler("EL FASSI", "zineb"), "birthDate", BIRTH_DATE)), 409, "possible-duplicate");
        assertThat(body).doesNotContain(BIRTH_DATE);
        api.create(agency(agencyId), with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE))
                .andExpect(jsonPath("$.candidates[0].id").value(existing.toString()))
                .andExpect(jsonPath("$.candidates[0].lastName").value("El Fassi"))
                .andExpect(jsonPath("$.candidates[0].firstName").value("Zineb"))
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("NAME_AND_BIRTH_DATE")));

        // Sans identity.read, aucune date n'est envoyee : le critere n'est pas calcule.
        api.create(writerWithoutIdentityRead(), traveler("El Fassi", "Zineb")).andExpect(status().isCreated());
    }
}
