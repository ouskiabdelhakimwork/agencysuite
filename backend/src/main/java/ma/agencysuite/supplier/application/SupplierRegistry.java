package ma.agencysuite.supplier.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.supplier.application.CatalogSort.SupplierSort;
import ma.agencysuite.supplier.application.PossibleDuplicateException.Candidate;
import ma.agencysuite.supplier.application.PossibleDuplicateException.Criterion;
import ma.agencysuite.supplier.domain.Supplier;
import ma.agencysuite.supplier.domain.SupplierData;
import ma.agencysuite.supplier.domain.SupplierType;
import ma.agencysuite.supplier.infrastructure.ProductRepository;
import ma.agencysuite.supplier.infrastructure.SupplierRepository;
import ma.agencysuite.supplier.infrastructure.SupplierSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fournisseurs de l'agence courante (US-601) : recherche, fiche, creation et modification avec
 * alerte de doublon probable, archivage et restauration audites.
 *
 * <p>N'ouvre aucun perimetre : agit dans celui de l'appelant, qui doit etre une agence (sinon
 * {@link IllegalStateException}, erreur de programmation). La permission d'ecriture est controlee
 * par le controleur ({@code @RequiresPermission(SUPPLIER_WRITE)}) ; la lecture est ouverte a tous.
 */
@Service
public class SupplierRegistry {

    public static final int MAX_PAGE_SIZE = 100;

    static final String AUDIT_ENTITY = "SUPPLIER";
    static final String SUPPLIER_ARCHIVED = "SUPPLIER_ARCHIVED";
    static final String SUPPLIER_RESTORED = "SUPPLIER_RESTORED";

    /** Ligne de la liste : le fournisseur et son nombre de prestations non archivees. */
    public record SupplierSummary(Supplier supplier, long productCount) {
    }

    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    SupplierRegistry(SupplierRepository suppliers, ProductRepository products, ApplicationEventPublisher events) {
        this.suppliers = suppliers;
        this.products = products;
        this.events = events;
    }

    /** Page de fournisseurs ; {@code archived} vrai : les seuls fournisseurs archives. */
    @Transactional(readOnly = true)
    public Page<SupplierSummary> search(String q, SupplierType type, String city, boolean archived, SupplierSort sort,
            int page, int size) {
        TenantContext.requireAgency();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        Page<Supplier> found = suppliers.findAll(SupplierSpecifications.matching(q, type, city, archived),
                PageRequest.of(page, size, sort.sort()));
        Map<UUID, Long> counts = new HashMap<>();
        if (!found.isEmpty()) {
            List<UUID> ids = found.map(Supplier::getId).getContent();
            for (Object[] row : products.countActiveBySupplier(ids)) {
                counts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return found.map(s -> new SupplierSummary(s, counts.getOrDefault(s.getId(), 0L)));
    }

    /** Fiche du fournisseur, archive ou non. */
    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return find(id);
    }

    /**
     * Cree le fournisseur. Sans {@code confirmDuplicate}, refuse (409) s'il ressemble a un
     * fournisseur non archive de l'agence (meme ICE, e-mail ou telephone).
     */
    @Transactional
    public Supplier create(SupplierData data, boolean confirmDuplicate) {
        TenantContext.requireAgency();
        Supplier supplier = Supplier.create(data);
        if (!confirmDuplicate) {
            rejectDuplicates(supplier.getIce(), supplier.getEmail(), supplier.getPhone(), null);
        }
        return suppliers.saveAndFlush(supplier);
    }

    /**
     * Remplace les champs modifiables. {@code version} doit etre celle de la fiche lue (sinon 409
     * stale-version). Le controle de doublon n'est refait que si l'ICE, l'e-mail ou le telephone
     * change. Changer la devise par defaut ne modifie pas les tarifs existants.
     */
    @Transactional
    public Supplier update(UUID id, long version, SupplierData data, boolean confirmDuplicate) {
        Supplier supplier = find(id);
        checkVersion(supplier, supplier.getId(), supplier.getVersion(), version);
        // Avant la modification : une requete sur une entite modifiee declencherait son ecriture.
        String ice = trimToNull(data.ice());
        String email = EmailAddresses.normalize(data.email());
        String phone = PhoneNumbers.normalize(data.phone());
        boolean identifiersChanged = !Objects.equals(ice, supplier.getIce())
                || !Objects.equals(email, supplier.getEmail())
                || !Objects.equals(phone, supplier.getPhone());
        if (identifiersChanged && !confirmDuplicate) {
            rejectDuplicates(ice, email, phone, id);
        }
        supplier.update(data);
        suppliers.flush();
        return supplier;
    }

    /** Archive le fournisseur (idempotent ; une seule entree d'audit). Ses prestations ne changent pas. */
    @Transactional
    public Supplier archive(UUID id) {
        Supplier supplier = find(id);
        if (supplier.archive(Instant.now())) {
            suppliers.flush();
            audit(SUPPLIER_ARCHIVED, supplier);
        }
        return supplier;
    }

    /** Restaure un fournisseur archive (idempotent ; une seule entree d'audit). */
    @Transactional
    public Supplier restore(UUID id) {
        Supplier supplier = find(id);
        if (supplier.restore()) {
            suppliers.flush();
            audit(SUPPLIER_RESTORED, supplier);
        }
        return supplier;
    }

    private Supplier find(UUID id) {
        TenantContext.requireAgency();
        return suppliers.findById(id).orElseThrow(() -> new SupplierException.NotFound("Fournisseur", id));
    }

    static void checkVersion(Object entity, UUID id, Long current, long expected) {
        if (!Objects.equals(current, expected)) {
            throw new ObjectOptimisticLockingFailureException(entity.getClass(), id);
        }
    }

    /** Details : codes techniques seulement (type du fournisseur), jamais le nom ni l'e-mail. */
    private void audit(String action, Supplier supplier) {
        AgencyId agency = TenantContext.requireAgency();
        events.publishEvent(new AuditableEvent(agency, action, AUDIT_ENTITY, supplier.getId().toString(),
                Map.of("type", supplier.getType().name())));
    }

    /** 409 possible-duplicate si un fournisseur non archive (autre que {@code self}) a l'un de ces identifiants. */
    private void rejectDuplicates(String ice, String email, String phone, UUID self) {
        Map<UUID, Supplier> found = new LinkedHashMap<>();
        Map<UUID, List<Criterion>> criteria = new LinkedHashMap<>();
        if (ice != null) {
            collect(suppliers.findTop10ByArchivedAtIsNullAndIceOrderByNameKeyAscIdAsc(ice), Criterion.ICE, self,
                    found, criteria);
        }
        if (email != null) {
            collect(suppliers.findTop10ByArchivedAtIsNullAndEmailOrderByNameKeyAscIdAsc(email), Criterion.EMAIL, self,
                    found, criteria);
        }
        if (phone != null) {
            collect(suppliers.findTop10ByArchivedAtIsNullAndPhoneOrderByNameKeyAscIdAsc(phone), Criterion.PHONE, self,
                    found, criteria);
        }
        if (!found.isEmpty()) {
            List<Candidate> candidates = found.values().stream()
                    .map(s -> new Candidate(s.getId(), s.getName(), List.copyOf(criteria.get(s.getId()))))
                    .toList();
            throw new PossibleDuplicateException(candidates);
        }
    }

    private static void collect(List<Supplier> matches, Criterion criterion, UUID self, Map<UUID, Supplier> found,
            Map<UUID, List<Criterion>> criteria) {
        for (Supplier match : matches) {
            if (match.getId().equals(self)) {
                continue;
            }
            found.putIfAbsent(match.getId(), match);
            criteria.computeIfAbsent(match.getId(), key -> new ArrayList<>()).add(criterion);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
