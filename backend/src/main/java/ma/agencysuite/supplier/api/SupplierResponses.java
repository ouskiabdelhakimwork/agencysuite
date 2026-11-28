package ma.agencysuite.supplier.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.shared.i18n.LocalizedText;
import ma.agencysuite.shared.money.MoneyJson;
import ma.agencysuite.supplier.application.ProductCatalog.ProductSummary;
import ma.agencysuite.supplier.application.ProductCatalog.ProductView;
import ma.agencysuite.supplier.application.SupplierRegistry.SupplierSummary;
import ma.agencysuite.supplier.domain.Product;
import ma.agencysuite.supplier.domain.ProductType;
import ma.agencysuite.supplier.domain.Rate;
import ma.agencysuite.supplier.domain.RateBasis;
import ma.agencysuite.supplier.domain.Supplier;
import ma.agencysuite.supplier.domain.SupplierType;
import org.springframework.data.domain.Page;

/** Reponses de l'API du module. Les cles de recherche ({@code name_key}, {@code search_key}) ne sortent jamais. */
final class SupplierResponses {

    private SupplierResponses() {
    }

    /** Ligne de la liste des fournisseurs ; {@code productCount} : prestations non archivees. */
    record SupplierItem(UUID id, SupplierType type, String name, String city, String country, String defaultCurrency,
            String phone, String email, boolean archived, long productCount) {

        static SupplierItem from(SupplierSummary summary) {
            Supplier s = summary.supplier();
            return new SupplierItem(s.getId(), s.getType(), s.getName(), s.getCity(), s.getCountry(),
                    s.getDefaultCurrency(), s.getPhone(), s.getEmail(), s.isArchived(), summary.productCount());
        }
    }

    /** Page de resultats. */
    record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

        static <S, T> PageResponse<T> from(Page<S> result, java.util.function.Function<S, T> mapper) {
            return new PageResponse<>(result.map(mapper).getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    /** Fiche complete du fournisseur. */
    record SupplierResponse(UUID id, SupplierType type, String name, String ice, String email, String phone,
            String contactName, String addressLine, String city, String country, String defaultCurrency,
            String paymentTerms, String notes, boolean archived, Instant archivedAt, Long version, Instant createdAt,
            Instant updatedAt) {

        static SupplierResponse from(Supplier s) {
            return new SupplierResponse(s.getId(), s.getType(), s.getName(), s.getIce(), s.getEmail(), s.getPhone(),
                    s.getContactName(), s.getAddressLine(), s.getCity(), s.getCountry(), s.getDefaultCurrency(),
                    s.getPaymentTerms(), s.getNotes(), s.isArchived(), s.getArchivedAt(), s.getVersion(),
                    s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    /** Fournisseur d'une prestation, en abrege. */
    record SupplierRef(UUID id, String name, boolean archived) {

        static SupplierRef from(Supplier s) {
            return new SupplierRef(s.getId(), s.getName(), s.isArchived());
        }
    }

    /** Ligne de la liste des prestations ; {@code name} : objet par langue. */
    record ProductItem(UUID id, ProductType type, LocalizedText name, SupplierRef supplier, boolean archived) {

        static ProductItem from(ProductSummary summary) {
            Product p = summary.product();
            return new ProductItem(p.getId(), p.getType(), p.getName(), SupplierRef.from(summary.supplier()),
                    p.isArchived());
        }
    }

    /** Tarif d'achat ; {@code purchase.amount} en chaine, a l'echelle de la devise. */
    record RateResponse(UUID id, UUID productId, RateBasis basis, LocalDate validFrom, LocalDate validTo,
            MoneyJson purchase, String label, boolean archived, Instant archivedAt, Long version, Instant createdAt,
            Instant updatedAt) {

        static RateResponse from(Rate r) {
            return new RateResponse(r.getId(), r.getProductId(), r.getBasis(), r.getValidFrom(), r.getValidTo(),
                    MoneyJson.from(r.getPurchase()), r.getLabel(), r.isArchived(), r.getArchivedAt(), r.getVersion(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    /** Fiche complete de la prestation, avec ses tarifs non archives (par base puis debut de validite). */
    record ProductResponse(UUID id, SupplierRef supplier, ProductType type, LocalizedText name,
            LocalizedText description, String cancellationPolicy, boolean archived, Instant archivedAt,
            List<RateResponse> rates, Long version, Instant createdAt, Instant updatedAt) {

        static ProductResponse from(ProductView view) {
            Product p = view.product();
            return new ProductResponse(p.getId(), SupplierRef.from(view.supplier()), p.getType(), p.getName(),
                    p.getDescription().isEmpty() ? null : p.getDescription(), p.getCancellationPolicy(),
                    p.isArchived(), p.getArchivedAt(),
                    view.rates().stream().map(RateResponse::from).toList(), p.getVersion(), p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }
}
