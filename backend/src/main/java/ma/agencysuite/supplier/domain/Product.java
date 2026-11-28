package ma.agencysuite.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.agencysuite.shared.i18n.LocalizedText;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;
import ma.agencysuite.shared.text.SearchKeys;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Prestation d'un fournisseur (chambre, transfert, excursion, location de salle...), avec ses
 * tarifs d'achat ({@link Rate}).
 *
 * <p>Nom (obligatoire, au moins une langue) et description (facultative) en {@link LocalizedText},
 * stockes en JSONB comme {@code audit_log.details} (carte {@code @JdbcTypeCode(SqlTypes.JSON)}).
 * {@code search_key} : cles de recherche par mots ({@link SearchKeys#words(String)}) du nom dans
 * chacune de ses langues, separees par {@value #SEARCH_KEY_SEPARATOR} ; recalculee a chaque ecriture,
 * jamais renvoyee. Le fournisseur ne change jamais. Pas de suppression : archivage et restauration.
 */
@Entity
@Table(name = "product")
public class Product extends AuthoredAgencyEntity {

    /** Longueur maximale du nom dans chaque langue. */
    public static final int NAME_MAX_LENGTH = 200;
    /** Longueur maximale de la description dans chaque langue. */
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    static final String SEARCH_KEY_SEPARATOR = " | ";

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> description;

    @Column(name = "search_key", nullable = false, length = 700)
    private String searchKey;

    @Column(name = "cancellation_policy", length = 2000)
    private String cancellationPolicy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Product() {
        // pour JPA
    }

    /** Nouvelle prestation du fournisseur (le cas d'usage a verifie qu'il existe et n'est pas archive). */
    public static Product create(UUID supplierId, ProductData data) {
        Product product = new Product();
        product.supplierId = Objects.requireNonNull(supplierId, "supplierId");
        product.update(data);
        return product;
    }

    /** Remplace les champs modifiables (pas le fournisseur). */
    public void update(ProductData data) {
        this.type = Objects.requireNonNull(data.type(), "type");
        LocalizedText validName = Objects.requireNonNull(data.name(), "name").requireValid(true, NAME_MAX_LENGTH);
        LocalizedText validDescription = (data.description() == null ? LocalizedText.empty() : data.description())
                .requireValid(false, DESCRIPTION_MAX_LENGTH);
        this.name = Map.copyOf(validName.values());
        this.description = validDescription.isEmpty() ? null : Map.copyOf(validDescription.values());
        this.searchKey = searchKeyOf(validName);
        this.cancellationPolicy = Texts.trimToNull(data.cancellationPolicy());
    }

    /** Cles par mots du nom dans chaque langue (ordre fr, ar, en). */
    static String searchKeyOf(LocalizedText name) {
        return name.values().values().stream()
                .map(SearchKeys::words)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.joining(SEARCH_KEY_SEPARATOR));
    }

    /** @return vrai si la prestation vient d'etre archivee (faux si elle l'etait deja) */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** @return vrai si la prestation vient d'etre restauree (faux si elle ne l'etait pas) */
    public boolean restore() {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        return true;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public ProductType getType() {
        return type;
    }

    public LocalizedText getName() {
        return LocalizedText.of(name);
    }

    /** Description, vide si elle est absente. */
    public LocalizedText getDescription() {
        return LocalizedText.of(description);
    }

    /** Cle technique de recherche ; jamais renvoyee par l'API. */
    public String getSearchKey() {
        return searchKey;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
