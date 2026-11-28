package ma.agencysuite.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Appels de l'API des voyageurs pour les tests (donnees fictives : noms inventes, e-mails en
 * {@code @example.test}, numeros de test). Aucune piece d'identite.
 */
public final class TravelerApi {

    public static final String TRAVELERS = "/api/v1/travelers";

    private final MockMvc mvc;

    public TravelerApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Corps minimal : nom et prenom. */
    public static Map<String, Object> traveler(String lastName, String firstName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lastName", lastName);
        body.put("firstName", firstName);
        return body;
    }

    /** Copie du corps avec ce champ en plus (ou remplace). */
    public static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        return CrmApi.with(body, field, value);
    }

    public ResultActions create(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(TRAVELERS).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(body)));
    }

    /** Cree le voyageur (201 attendu) et renvoie son identifiant. */
    public UUID createOk(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        String response = create(token, body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    public ResultActions get(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(TRAVELERS + "/" + id).with(token));
    }

    /** Version courante de la fiche (lue par l'API). */
    public long version(JwtRequestPostProcessor token, UUID id) throws Exception {
        String response = get(token, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.version")).longValue();
    }

    public ResultActions update(JwtRequestPostProcessor token, UUID id, long version, Map<String, Object> body)
            throws Exception {
        return mvc.perform(put(TRAVELERS + "/" + id).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(with(body, "version", version))));
    }

    public ResultActions archive(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(TRAVELERS + "/" + id + "/archive").with(token));
    }

    public ResultActions restore(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(TRAVELERS + "/" + id + "/restore").with(token));
    }

    public ResultActions link(JwtRequestPostProcessor token, UUID id, UUID customerId) throws Exception {
        return mvc.perform(put(TRAVELERS + "/" + id + "/customers/" + customerId).with(token));
    }

    public ResultActions unlink(JwtRequestPostProcessor token, UUID id, UUID customerId) throws Exception {
        return mvc.perform(delete(TRAVELERS + "/" + id + "/customers/" + customerId).with(token));
    }

    public ResultActions list(JwtRequestPostProcessor token, String query) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(TRAVELERS + (query.isEmpty() ? "" : "?" + query)).with(token));
    }

    /** Recherche par texte libre (parametre q transmis tel quel, sans encodage manuel). */
    public ResultActions search(JwtRequestPostProcessor token, String q) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(TRAVELERS).param("q", q).with(token));
    }

    /** Verifie le statut et le type du probleme ; renvoie le corps. */
    public String expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:" + code))
                .andReturn().getResponse().getContentAsString();
    }
}
