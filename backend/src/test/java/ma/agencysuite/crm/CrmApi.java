package ma.agencysuite.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appels de l'API des clients pour les tests (donnees fictives : e-mails en @example.test,
 * numeros de test, ICE invente). Les corps sont construits a partir de cartes pour que chaque test
 * ne donne que les champs qui l'interessent.
 */
public final class CrmApi {

    public static final String CUSTOMERS = "/api/v1/customers";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;

    public CrmApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Corps minimal d'une entreprise. */
    public static Map<String, Object> company(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "COMPANY");
        body.put("name", name);
        return body;
    }

    /** Corps minimal d'un particulier. */
    public static Map<String, Object> individual(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "INDIVIDUAL");
        body.put("name", name);
        return body;
    }

    /** Copie du corps avec ce champ en plus (ou remplace). */
    public static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put(field, value);
        return copy;
    }

    public static String json(Object body) {
        return JSON.writeValueAsString(body);
    }

    public ResultActions create(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(CUSTOMERS).with(token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    /** Cree le client (201 attendu) et renvoie son identifiant. */
    public UUID createOk(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        String response = create(token, body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    public ResultActions get(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(MockMvcRequestBuildersAccess.get(CUSTOMERS + "/" + id).with(token));
    }

    /** Version courante de la fiche (lue par l'API). */
    public long version(JwtRequestPostProcessor token, UUID id) throws Exception {
        String response = get(token, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.version")).longValue();
    }

    public ResultActions update(JwtRequestPostProcessor token, UUID id, long version, Map<String, Object> body)
            throws Exception {
        return mvc.perform(put(CUSTOMERS + "/" + id).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(json(with(body, "version", version))));
    }

    public ResultActions archive(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(CUSTOMERS + "/" + id + "/archive").with(token));
    }

    public ResultActions restore(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(CUSTOMERS + "/" + id + "/restore").with(token));
    }

    public ResultActions list(JwtRequestPostProcessor token, String query) throws Exception {
        return mvc.perform(MockMvcRequestBuildersAccess.get(CUSTOMERS + (query.isEmpty() ? "" : "?" + query)).with(token));
    }

    /** Recherche par texte libre (parametre q transmis tel quel, sans encodage manuel). */
    public ResultActions search(JwtRequestPostProcessor token, String q) throws Exception {
        return mvc.perform(MockMvcRequestBuildersAccess.get(CUSTOMERS).param("q", q).with(token));
    }

    public ResultActions addContact(JwtRequestPostProcessor token, UUID customerId, Map<String, Object> body)
            throws Exception {
        return mvc.perform(post(CUSTOMERS + "/" + customerId + "/contacts").with(token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    /** Ajoute le contact (201 attendu) et renvoie son identifiant. */
    public UUID addContactOk(JwtRequestPostProcessor token, UUID customerId, String fullName, boolean primary)
            throws Exception {
        String response = addContact(token, customerId, contact(fullName, primary))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    public static Map<String, Object> contact(String fullName, boolean primary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("isPrimary", primary);
        return body;
    }

    /** Verifie le statut et le type du probleme ; renvoie le corps. */
    public String expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:" + code))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Valeur non reconnue dans un parametre d'URL (filtre enumere) : 400 validation-failed, titre et detail
     * traduits en FR, AR et EN, sans la valeur recue ni le message technique de Spring dans la reponse.
     */
    public void expectUnrecognizedParameter(JwtRequestPostProcessor token, String path, String parameter, String value)
            throws Exception {
        String[][] languages = {
                {"fr", "Données invalides",
                        "Un ou plusieurs paramètres de la requête ont une valeur non reconnue ou un format invalide."},
                {"ar", "بيانات غير صالحة", "معامل واحد أو أكثر في الطلب له قيمة غير معروفة أو تنسيق غير صالح."},
                {"en", "Invalid data",
                        "One or more request parameters have an unrecognized value or an invalid format."}};
        for (String[] expected : languages) {
            String body = expectProblem(mvc.perform(MockMvcRequestBuildersAccess.get(path).param(parameter, value)
                            .with(token).header("Accept-Language", expected[0]))
                    .andExpect(jsonPath("$.title").value(expected[1]))
                    .andExpect(jsonPath("$.detail").value(expected[2])), 400, "validation-failed");
            org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain(value)
                    .doesNotContain("Failed to convert")
                    .doesNotContain("Exception");
        }
    }

    public MockMvc mvc() {
        return mvc;
    }

    /** Requete brute (en-tetes particuliers, par exemple Accept-Language). */
    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request);
    }

    /** Evite le conflit de nom entre la methode {@link #get} et le constructeur de requete GET. */
    private static final class MockMvcRequestBuildersAccess {
        static MockHttpServletRequestBuilder get(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        }
    }
}
