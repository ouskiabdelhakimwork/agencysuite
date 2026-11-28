package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.supplier.SupplierApi.rate;
import static ma.agencysuite.supplier.SupplierApi.room;
import static ma.agencysuite.supplier.SupplierApi.supplier;
import static ma.agencysuite.supplier.SupplierApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.iam.IamTestData;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Archivage et restauration des fournisseurs, prestations et tarifs : idempotents, sans
 * suppression ; audit des fournisseurs et des prestations avec le type seulement, pas des tarifs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupplierArchiveTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();
    private SupplierApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new SupplierApi(mvc);
        agencyId = data.createAgency("sarch-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void supplierArchiveAndRestoreAreIdempotentAndAuditedWithTheTypeOnly() throws Exception {
        UUID id = api.createSupplierOk(agency(agencyId),
                with(with(supplier("RIAD", "Riad Atlas Test"), "email", "riad@example.test"), "phone", "0524000000"));
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));

        api.archiveSupplier(agency(agencyId), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        api.archiveSupplier(agency(agencyId), id).andExpect(status().isOk());
        api.listSuppliers(agency(agencyId)).andExpect(jsonPath("$.totalElements").value(0));
        api.listSuppliers(agency(agencyId), "archived", "true")
                .andExpect(jsonPath("$.items[*].id").value(contains(id.toString())))
                .andExpect(jsonPath("$.items[0].archived").value(true));
        api.getSupplier(agency(agencyId), id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        api.restoreSupplier(agency(agencyId), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));
        api.restoreSupplier(agency(agencyId), id).andExpect(status().isOk());

        List<Row> entries = entries("SUPPLIER_");
        assertThat(entries).extracting(Row::action).containsExactly("SUPPLIER_ARCHIVED", "SUPPLIER_RESTORED");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.agencyId()).isEqualTo(agencyId);
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(manager);
            assertThat(row.entityType()).isEqualTo("SUPPLIER");
            assertThat(row.entityId()).isEqualTo(id.toString());
            assertThat(row.details()).isEqualTo("{\"type\": \"RIAD\"}");
        });
    }

    @Test
    void productArchiveAndRestoreAreIdempotentAndAuditedWithTheTypeOnly() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyId), supplier("RIAD", "Riad Atlas Test"));
        UUID product = api.createProductOk(agency(agencyId), riad, room("Chambre double"));
        UUID other = api.createProductOk(agency(agencyId), riad, room("Suite"));

        api.archiveProduct(agency(agencyId), product).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        api.archiveProduct(agency(agencyId), product).andExpect(status().isOk());
        api.listProducts(agency(agencyId)).andExpect(jsonPath("$.items[*].id").value(contains(other.toString())));
        api.listProducts(agency(agencyId), "archived", "true")
                .andExpect(jsonPath("$.items[*].id").value(contains(product.toString())));
        api.getProduct(agency(agencyId), product).andExpect(status().isOk());
        api.restoreProduct(agency(agencyId), product).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        api.restoreProduct(agency(agencyId), product).andExpect(status().isOk());

        List<Row> entries = entries("PRODUCT_");
        assertThat(entries).extracting(Row::action).containsExactly("PRODUCT_ARCHIVED", "PRODUCT_RESTORED");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.entityType()).isEqualTo("PRODUCT");
            assertThat(row.entityId()).isEqualTo(product.toString());
            assertThat(row.details()).isEqualTo("{\"type\": \"ACCOMMODATION\"}").doesNotContain("Chambre");
        });
    }

    @Test
    void rateArchiveAndRestoreAreIdempotentAndNotAudited() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyId), supplier("RIAD", "Riad Atlas Test"));
        UUID room = api.createProductOk(agency(agencyId), riad, room("Chambre double"));
        UUID rate = api.createRateOk(agency(agencyId), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));

        api.archiveRate(agency(agencyId), room, rate).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        api.archiveRate(agency(agencyId), room, rate).andExpect(status().isOk());
        api.getProduct(agency(agencyId), room).andExpect(jsonPath("$.rates").isEmpty());
        api.restoreRate(agency(agencyId), room, rate).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        api.restoreRate(agency(agencyId), room, rate).andExpect(status().isOk());

        api.getProduct(agency(agencyId), room).andExpect(jsonPath("$.rates[*].id").value(contains(rate.toString())));
        assertThat(audit.entries(agencyId)).extracting(Row::action).noneMatch(action -> action.startsWith("RATE"));
    }

    @Test
    void archivingASupplierKeepsItsProductsAndCreatingOrUpdatingIsNotAudited() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyId), supplier("RIAD", "Riad Atlas Test"));
        UUID product = api.createProductOk(agency(agencyId), riad, room("Chambre double"));
        api.updateSupplier(agency(agencyId), riad, 0, supplier("RIAD", "Riad Atlas Renomme Test"))
                .andExpect(status().isOk());

        api.archiveSupplier(agency(agencyId), riad).andExpect(status().isOk());

        api.getProduct(agency(agencyId), product).andExpect(jsonPath("$.archived").value(false));
        assertThat(audit.entries(agencyId)).extracting(Row::action).containsExactly("SUPPLIER_ARCHIVED");
    }

    @Test
    void thereIsNoDeleteEndpoint() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyId), supplier("RIAD", "Riad Atlas Test"));
        UUID room = api.createProductOk(agency(agencyId), riad, room("Chambre double"));
        UUID rate = api.createRateOk(agency(agencyId), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));

        for (String url : List.of(SupplierApi.SUPPLIERS + "/" + riad, SupplierApi.PRODUCTS + "/" + room,
                SupplierApi.rates(room) + "/" + rate)) {
            mvc.perform(MockMvcRequestBuilders.delete(url).with(agency(agencyId))).andExpect(status().is4xxClientError());
        }
        api.listRates(agency(agencyId), room).andExpect(jsonPath("$[*].id").value(contains(rate.toString())));
    }

    private List<Row> entries(String prefix) {
        return audit.entries(agencyId).stream().filter(row -> row.action().startsWith(prefix)).toList();
    }
}
