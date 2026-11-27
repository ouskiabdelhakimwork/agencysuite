package ma.agencysuite.crm.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.crm.application.PossibleDuplicateException.Candidate;
import ma.agencysuite.crm.application.PossibleDuplicateException.Criterion;
import ma.agencysuite.crm.domain.Contact;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerData;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.crm.infrastructure.ContactRepository;
import ma.agencysuite.crm.infrastructure.CrmConstraints;
import ma.agencysuite.crm.infrastructure.CustomerRepository;
import ma.agencysuite.crm.infrastructure.CustomerSpecifications;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clients de l'agence courante (US-401) : recherche, fiche, creation et modification avec alerte de
 * doublon probable, archivage et restauration.
 *
 * <p>N'ouvre aucun perimetre : agit dans celui de l'appelant, qui doit etre une agence (sinon
 * {@link IllegalStateException}, erreur de programmation). La permission d'ecriture est controlee
 * par le controleur ({@code @RequiresPermission(CRM_WRITE)}) ; la lecture est ouverte a tous.
 */
@Service
public class CustomerRegistry {

    public static final int MAX_PAGE_SIZE = 100;

    static final String AUDIT_ENTITY = "CUSTOMER";
    static final String CUSTOMER_ARCHIVED = "CUSTOMER_ARCHIVED";
    static final String CUSTOMER_RESTORED = "CUSTOMER_RESTORED";

    /** Fiche complete : le client et ses contacts (principal d'abord). */
    public record CustomerView(Customer customer, List<Contact> contacts) {
    }

    private final CustomerRepository customers;
    private final ContactRepository contacts;
    private final ApplicationEventPublisher events;

    CustomerRegistry(CustomerRepository customers, ContactRepository contacts, ApplicationEventPublisher events) {
        this.customers = customers;
        this.contacts = contacts;
        this.events = events;
    }

    /** Page de clients ; {@code archived} vrai : les seuls clients archives. */
    @Transactional(readOnly = true)
    public Page<Customer> search(String q, CustomerType type, UUID ownerUserId, boolean archived, CustomerSort sort,
            int page, int size) {
        TenantContext.requireAgency();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        return customers.findAll(CustomerSpecifications.matching(q, type, ownerUserId, archived),
                PageRequest.of(page, size, sort.sort()));
    }

    /** Fiche du client, archive ou non. */
    @Transactional(readOnly = true)
    public CustomerView get(UUID id) {
        return view(find(id));
    }

    /**
     * Cree le client. Sans {@code confirmDuplicate}, refuse (409) s'il ressemble a un client non
     * archive de l'agence (meme e-mail, telephone ou ICE).
     */
    @Transactional
    public CustomerView create(CustomerData data, boolean confirmDuplicate) {
        TenantContext.requireAgency();
        Customer customer = Customer.create(data);
        if (!confirmDuplicate) {
            rejectDuplicates(customer.getEmail(), customer.getPhone(), customer.getIce(), null);
        }
        saveChecked(() -> customers.saveAndFlush(customer));
        return new CustomerView(customer, List.of());
    }

    /**
     * Remplace les champs modifiables. {@code version} doit etre celle de la fiche lue (sinon 409
     * stale-version). Le controle de doublon n'est refait que si l'e-mail, le telephone ou l'ICE
     * change. Une entreprise qui a des contacts ne peut pas devenir un particulier.
     */
    @Transactional
    public CustomerView update(UUID id, long version, CustomerData data, boolean confirmDuplicate) {
        Customer customer = find(id);
        checkVersion(customer, version);
        if (data.type() == CustomerType.INDIVIDUAL && customer.isCompany() && contacts.existsByCustomerId(id)) {
            throw new CustomerHasContactsException();
        }
        // Avant la modification : une requete sur une entite modifiee declencherait son ecriture.
        String email = EmailAddresses.normalize(data.email());
        String phone = PhoneNumbers.normalize(data.phone());
        String ice = data.type() == CustomerType.COMPANY ? trimToNull(data.ice()) : null;
        boolean identifiersChanged = !Objects.equals(email, customer.getEmail())
                || !Objects.equals(phone, customer.getPhone())
                || !Objects.equals(ice, customer.getIce());
        if (identifiersChanged && !confirmDuplicate) {
            rejectDuplicates(email, phone, ice, id);
        }
        customer.update(data);
        saveChecked(customers::flush);
        return view(customer);
    }

    /** Archive le client (idempotent ; une seule entree d'audit). */
    @Transactional
    public CustomerView archive(UUID id) {
        Customer customer = find(id);
        if (customer.archive(Instant.now())) {
            customers.flush();
            audit(CUSTOMER_ARCHIVED, customer);
        }
        return view(customer);
    }

    /** Restaure un client archive (idempotent ; une seule entree d'audit). */
    @Transactional
    public CustomerView restore(UUID id) {
        Customer customer = find(id);
        if (customer.restore()) {
            customers.flush();
            audit(CUSTOMER_RESTORED, customer);
        }
        return view(customer);
    }

    private Customer find(UUID id) {
        TenantContext.requireAgency();
        return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private CustomerView view(Customer customer) {
        return new CustomerView(customer, contacts.findByCustomerIdOrderByPrimaryDescFullNameAsc(customer.getId()));
    }

    static void checkVersion(Object entity, UUID id, Long current, long expected) {
        if (!Objects.equals(current, expected)) {
            throw new ObjectOptimisticLockingFailureException(entity.getClass(), id);
        }
    }

    private static void checkVersion(Customer customer, long expected) {
        checkVersion(customer, customer.getId(), customer.getVersion(), expected);
    }

    /** Details : codes techniques seulement (type du client), jamais le nom ni l'e-mail. */
    private void audit(String action, Customer customer) {
        AgencyId agency = TenantContext.requireAgency();
        events.publishEvent(new AuditableEvent(agency, action, AUDIT_ENTITY, customer.getId().toString(),
                Map.of("type", customer.getType().name())));
    }

    /** 409 possible-duplicate si un client non archive (autre que {@code self}) a l'un de ces identifiants. */
    private void rejectDuplicates(String email, String phone, String ice, UUID self) {
        Map<UUID, Customer> found = new LinkedHashMap<>();
        Map<UUID, List<Criterion>> criteria = new LinkedHashMap<>();
        if (email != null) {
            collect(customers.findTop10ByArchivedAtIsNullAndEmailOrderByName(email), Criterion.EMAIL, self, found,
                    criteria);
        }
        if (phone != null) {
            collect(customers.findTop10ByArchivedAtIsNullAndPhoneOrderByName(phone), Criterion.PHONE, self, found,
                    criteria);
        }
        if (ice != null) {
            collect(customers.findTop10ByArchivedAtIsNullAndIceOrderByName(ice), Criterion.ICE, self, found, criteria);
        }
        if (!found.isEmpty()) {
            List<Candidate> candidates = found.values().stream()
                    .map(c -> new Candidate(c.getId(), c.getName(), List.copyOf(criteria.get(c.getId()))))
                    .toList();
            throw new PossibleDuplicateException(candidates);
        }
    }

    private static void collect(List<Customer> matches, Criterion criterion, UUID self, Map<UUID, Customer> found,
            Map<UUID, List<Criterion>> criteria) {
        for (Customer match : matches) {
            if (match.getId().equals(self)) {
                continue;
            }
            found.putIfAbsent(match.getId(), match);
            criteria.computeIfAbsent(match.getId(), key -> new ArrayList<>()).add(criterion);
        }
    }

    /** Le responsable doit etre un utilisateur de l'agence : garanti par la cle composee, traduit en 400. */
    private static void saveChecked(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException e) {
            if (CrmConstraints.isUnknownOwner(e)) {
                throw new InvalidOwnerException();
            }
            throw e;
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
