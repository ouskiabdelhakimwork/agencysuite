package ma.agencysuite.supplier.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.supplier.application.CatalogSort.ProductSort;
import ma.agencysuite.supplier.domain.Product;
import ma.agencysuite.supplier.domain.ProductData;
import ma.agencysuite.supplier.domain.ProductType;
import ma.agencysuite.supplier.domain.Rate;
import ma.agencysuite.supplier.domain.Supplier;
import ma.agencysuite.supplier.infrastructure.ProductRepository;
import ma.agencysuite.supplier.infrastructure.ProductSpecifications;
import ma.agencysuite.supplier.infrastructure.RateRepository;
import ma.agencysuite.supplier.infrastructure.SupplierRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prestations des fournisseurs de l'agence courante : recherche, fiche avec ses tarifs non
 * archives, creation (refusee pour un fournisseur archive), modification, archivage et restauration
 * audites.
 *
 * <p>N'ouvre aucun perimetre (voir {@link SupplierRegistry}). Ecriture : {@code supplier.write},
 * controlee par le controleur.
 */
@Service
public class ProductCatalog {

    public static final int MAX_PAGE_SIZE = SupplierRegistry.MAX_PAGE_SIZE;

    static final String AUDIT_ENTITY = "PRODUCT";
    static final String PRODUCT_ARCHIVED = "PRODUCT_ARCHIVED";
    static final String PRODUCT_RESTORED = "PRODUCT_RESTORED";

    /** Ordre d'affichage des tarifs : base (ordre de l'enum), puis debut de validite. */
    static final Comparator<Rate> RATE_ORDER = Comparator.comparing(Rate::getBasis)
            .thenComparing(Rate::getValidFrom)
            .thenComparing(Rate::getId);

    /** Ligne de la liste : la prestation et son fournisseur. */
    public record ProductSummary(Product product, Supplier supplier) {
    }

    /** Fiche complete : la prestation, son fournisseur et ses tarifs non archives. */
    public record ProductView(Product product, Supplier supplier, List<Rate> rates) {
    }

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final RateRepository rates;
    private final ApplicationEventPublisher events;

    ProductCatalog(ProductRepository products, SupplierRepository suppliers, RateRepository rates,
            ApplicationEventPublisher events) {
        this.products = products;
        this.suppliers = suppliers;
        this.rates = rates;
        this.events = events;
    }

    /**
     * Page de prestations ; {@code archived} vrai : les seules prestations archivees ;
     * {@code supplierArchived} faux : celles des fournisseurs non archives, vrai : celles des
     * fournisseurs archives.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummary> search(String q, UUID supplierId, ProductType type, boolean archived,
            boolean supplierArchived, ProductSort sort, int page, int size) {
        TenantContext.requireAgency();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        Page<Product> found = products.findAll(
                ProductSpecifications.matching(q, supplierId, type, archived, supplierArchived),
                PageRequest.of(page, size, sort.sort()));
        Set<UUID> supplierIds = found.stream().map(Product::getSupplierId).collect(Collectors.toSet());
        Map<UUID, Supplier> bySupplier = suppliers.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        return found.map(p -> new ProductSummary(p, bySupplier.get(p.getSupplierId())));
    }

    /** Fiche de la prestation (archivee ou non), avec ses tarifs non archives. */
    @Transactional(readOnly = true)
    public ProductView get(UUID id) {
        return view(find(id));
    }

    /** Cree une prestation du fournisseur : 404 s'il est inconnu, 409 supplier-archived s'il est archive. */
    @Transactional
    public ProductView create(UUID supplierId, ProductData data) {
        TenantContext.requireAgency();
        Supplier supplier = suppliers.findById(supplierId)
                .orElseThrow(() -> new SupplierException.NotFound("Fournisseur", supplierId));
        if (supplier.isArchived()) {
            throw new SupplierException.SupplierArchived();
        }
        Product product = products.saveAndFlush(Product.create(supplier.getId(), data));
        return new ProductView(product, supplier, List.of());
    }

    /** Remplace les champs modifiables (pas le fournisseur) ; {@code version} : 409 stale-version. */
    @Transactional
    public ProductView update(UUID id, long version, ProductData data) {
        Product product = find(id);
        SupplierRegistry.checkVersion(product, product.getId(), product.getVersion(), version);
        product.update(data);
        products.flush();
        return view(product);
    }

    /** Archive la prestation (idempotent ; une seule entree d'audit). Ses tarifs ne changent pas. */
    @Transactional
    public ProductView archive(UUID id) {
        Product product = find(id);
        if (product.archive(Instant.now())) {
            products.flush();
            audit(PRODUCT_ARCHIVED, product);
        }
        return view(product);
    }

    /** Restaure une prestation archivee (idempotent ; une seule entree d'audit). */
    @Transactional
    public ProductView restore(UUID id) {
        Product product = find(id);
        if (product.restore()) {
            products.flush();
            audit(PRODUCT_RESTORED, product);
        }
        return view(product);
    }

    private Product find(UUID id) {
        TenantContext.requireAgency();
        return products.findById(id).orElseThrow(() -> new SupplierException.NotFound("Prestation", id));
    }

    private ProductView view(Product product) {
        Supplier supplier = suppliers.findById(product.getSupplierId()).orElseThrow();
        List<Rate> active = rates.findByProductIdAndArchivedAtIsNull(product.getId()).stream()
                .sorted(RATE_ORDER).toList();
        return new ProductView(product, supplier, active);
    }

    /** Details : codes techniques seulement (type de la prestation), jamais le nom. */
    private void audit(String action, Product product) {
        AgencyId agency = TenantContext.requireAgency();
        events.publishEvent(new AuditableEvent(agency, action, AUDIT_ENTITY, product.getId().toString(),
                Map.of("type", product.getType().name())));
    }
}
