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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Appels de l'API des demandes pour les tests (donnees fictives). Les corps sont des cartes pour
 * que chaque test ne donne que les champs qui l'interessent.
 */
public final class RequestApi {

    public static final String REQUESTS = "/api/v1/requests";

    private final MockMvc mvc;

    public RequestApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Corps minimal : client et canal. */
    public static Map<String, Object> request(UUID customerId, String channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("channel", channel);
        return body;
    }

    public static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        return CrmApi.with(body, field, value);
    }

    public ResultActions create(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(REQUESTS).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(body)));
    }

    /** Cree la demande (201 attendu) et renvoie son identifiant. */
    public UUID createOk(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        String response = create(token, body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    public ResultActions get(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(REQUESTS + "/" + id).with(token));
    }

    /** Valeur d'un champ de la fiche (lue par l'API). */
    public <T> T read(JwtRequestPostProcessor token, UUID id, String path) throws Exception {
        String response = get(token, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, path);
    }

    /** Version courante de la fiche. */
    public long version(JwtRequestPostProcessor token, UUID id) throws Exception {
        return this.<Number>read(token, id, "$.version").longValue();
    }

    public ResultActions update(JwtRequestPostProcessor token, UUID id, long version, Map<String, Object> body)
            throws Exception {
        return mvc.perform(put(REQUESTS + "/" + id).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(with(body, "version", version))));
    }

    /** Changement de statut avec la version courante ; raison et commentaire facultatifs (nuls : absents). */
    public ResultActions changeStatus(JwtRequestPostProcessor token, UUID id, String target, String lostReason,
            String lostComment) throws Exception {
        return changeStatus(token, id, version(token, id), target, lostReason, lostComment);
    }

    public ResultActions changeStatus(JwtRequestPostProcessor token, UUID id, long version, String target,
            String lostReason, String lostComment) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("status", target);
        if (lostReason != null) {
            body.put("lostReason", lostReason);
        }
        if (lostComment != null) {
            body.put("lostComment", lostComment);
        }
        return mvc.perform(post(REQUESTS + "/" + id + "/status").with(token).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(body)));
    }

    /** Passe au statut (200 attendu). */
    public void moveTo(JwtRequestPostProcessor token, UUID id, String target) throws Exception {
        changeStatus(token, id, target, "LOST".equals(target) ? "PRICE" : null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target));
    }

    public ResultActions list(JwtRequestPostProcessor token, String query) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(REQUESTS + (query.isEmpty() ? "" : "?" + query)).with(token));
    }

    /** Recherche par texte libre (parametre q transmis tel quel). */
    public ResultActions search(JwtRequestPostProcessor token, String q) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(REQUESTS).param("q", q).with(token));
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request);
    }
}
