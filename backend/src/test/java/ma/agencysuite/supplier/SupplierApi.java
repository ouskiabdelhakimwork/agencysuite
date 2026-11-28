package ma.agencysuite.supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Appels de l'API du module supplier pour les tests (donnees fictives : fournisseurs inventes,
 * e-mails en @example.test, numeros de test, ICE invente). Les corps sont des cartes pour que
 * chaque test ne donne que les champs qui l'interessent.
 */
public final class SupplierApi {

    public static final String SUPPLIERS = "/api/v1/suppliers";
    public static final String PRODUCTS = "/api/v1/products";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;

    public SupplierApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    // --- Corps

    /** Corps minimal d'un fournisseur. */
    public static Map<String, Object> supplier(String type, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("name", name);
        return body;
    }

    /** Corps minimal d'une prestation, avec son nom par langue. */
    public static Map<String, Object> product(String type, Map<String, String> name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("name", name);
        return body;
    }

    /** Corps d'une prestation d'hebergement nommee en francais seulement. */
    public static Map<String, Object> room(String frenchName) {
        return product("ACCOMMODATION", Map.of("fr", frenchName));
    }

    /** Corps d'un tarif ; {@code currency} nul : devise absente du corps. */
    public static Map<String, Object> rate(String basis, String from, String to, Object amount, String currency) {
        Map<String, Object> purchase = new LinkedHashMap<>();
        purchase.put("amount", amount);
        if (currency != null) {
            purchase.put("currency", currency);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("basis", basis);
        body.put("validFrom", from);
        body.put("validTo", to);
        body.put("purchase", purchase);
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

    // --- Fournisseurs

    public ResultActions createSupplier(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(SUPPLIERS).with(token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public UUID createSupplierOk(JwtRequestPostProcessor token, Map<String, Object> body) throws Exception {
        return idOf(createSupplier(token, body).andExpect(status().isCreated()));
    }

    public ResultActions getSupplier(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(get(SUPPLIERS + "/" + id).with(token));
    }

    public ResultActions updateSupplier(JwtRequestPostProcessor token, UUID id, long version,
            Map<String, Object> body) throws Exception {
        return mvc.perform(put(SUPPLIERS + "/" + id).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(json(with(body, "version", version))));
    }

    public ResultActions archiveSupplier(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(SUPPLIERS + "/" + id + "/archive").with(token));
    }

    public ResultActions restoreSupplier(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(SUPPLIERS + "/" + id + "/restore").with(token));
    }

    /** Liste des fournisseurs ; {@code params} : paires nom, valeur (encodees par MockMvc). */
    public ResultActions listSuppliers(JwtRequestPostProcessor token, String... params) throws Exception {
        var request = get(SUPPLIERS).with(token);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    // --- Prestations

    public ResultActions createProduct(JwtRequestPostProcessor token, UUID supplierId, Map<String, Object> body)
            throws Exception {
        return mvc.perform(post(SUPPLIERS + "/" + supplierId + "/products").with(token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public UUID createProductOk(JwtRequestPostProcessor token, UUID supplierId, Map<String, Object> body)
            throws Exception {
        return idOf(createProduct(token, supplierId, body).andExpect(status().isCreated()));
    }

    public ResultActions getProduct(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(get(PRODUCTS + "/" + id).with(token));
    }

    public ResultActions updateProduct(JwtRequestPostProcessor token, UUID id, long version,
            Map<String, Object> body) throws Exception {
        return mvc.perform(put(PRODUCTS + "/" + id).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(json(with(body, "version", version))));
    }

    public ResultActions archiveProduct(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(PRODUCTS + "/" + id + "/archive").with(token));
    }

    public ResultActions restoreProduct(JwtRequestPostProcessor token, UUID id) throws Exception {
        return mvc.perform(post(PRODUCTS + "/" + id + "/restore").with(token));
    }

    public ResultActions listProducts(JwtRequestPostProcessor token, String... params) throws Exception {
        var request = get(PRODUCTS).with(token);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    // --- Tarifs

    public static String rates(UUID productId) {
        return PRODUCTS + "/" + productId + "/rates";
    }

    public ResultActions createRate(JwtRequestPostProcessor token, UUID productId, Map<String, Object> body)
            throws Exception {
        return mvc.perform(post(rates(productId)).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    /** Corps brut (pour envoyer un montant en nombre JSON tel quel). */
    public ResultActions createRateRaw(JwtRequestPostProcessor token, UUID productId, String body) throws Exception {
        return mvc.perform(post(rates(productId)).with(token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    public UUID createRateOk(JwtRequestPostProcessor token, UUID productId, Map<String, Object> body)
            throws Exception {
        return idOf(createRate(token, productId, body).andExpect(status().isCreated()));
    }

    public ResultActions updateRate(JwtRequestPostProcessor token, UUID productId, UUID rateId, long version,
            Map<String, Object> body) throws Exception {
        return mvc.perform(put(rates(productId) + "/" + rateId).with(token).contentType(MediaType.APPLICATION_JSON)
                .content(json(with(body, "version", version))));
    }

    public ResultActions archiveRate(JwtRequestPostProcessor token, UUID productId, UUID rateId) throws Exception {
        return mvc.perform(post(rates(productId) + "/" + rateId + "/archive").with(token));
    }

    public ResultActions restoreRate(JwtRequestPostProcessor token, UUID productId, UUID rateId) throws Exception {
        return mvc.perform(post(rates(productId) + "/" + rateId + "/restore").with(token));
    }

    public ResultActions listRates(JwtRequestPostProcessor token, UUID productId, String... params) throws Exception {
        var request = get(rates(productId)).with(token);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    // --- Outils

    /** Version courante lue dans la reponse de {@code read}. */
    public static long version(ResultActions read) throws Exception {
        String body = read.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    /** Verifie le statut et le type du probleme ; renvoie le corps. */
    public static String expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:" + code))
                .andReturn().getResponse().getContentAsString();
    }

    private static UUID idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public MockMvc mvc() {
        return mvc;
    }
}
