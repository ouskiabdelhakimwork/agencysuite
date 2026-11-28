package ma.agencysuite.crm.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.crm.application.PossibleDuplicateException.Criterion;
import ma.agencysuite.crm.application.PossibleDuplicateException.TravelerCandidate;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerTraveler;
import ma.agencysuite.crm.domain.Traveler;
import ma.agencysuite.crm.domain.TravelerData;
import ma.agencysuite.crm.infrastructure.CrmConstraints;
import ma.agencysuite.crm.infrastructure.CustomerRepository;
import ma.agencysuite.crm.infrastructure.CustomerTravelerRepository;
import ma.agencysuite.crm.infrastructure.TravelerRepository;
import ma.agencysuite.crm.infrastructure.TravelerSpecifications;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.text.SearchKeys;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voyageurs de l'agence courante (etape 5, lot 2) : recherche, fiche, creation et modification avec
 * alerte de doublon probable, archivage et restauration, rattachement a zero, un ou plusieurs
 * clients.
 *
 * <p><b>Date de naissance</b> (RG-14, a valider) : lisible et modifiable seulement avec la
 * permission {@code identity.read} de l'utilisateur courant ({@link UserContext}, alimente par le
 * filtre de securite, meme source que {@code @RequiresPermission}). Sans elle : la fiche la masque
 * ({@code birthDate} nul, {@code birthDateHidden} vrai si une date existe) ; une date envoyee donne
 * 403 permission-denied ; une date absente ne modifie pas la date enregistree. Lecture non auditee.
 * Le <b>lieu de naissance</b> suit exactement la meme regle ({@code birthPlace},
 * {@code birthPlaceHidden}) ; ni l'un ni l'autre n'est dans la liste ou un critere de recherche.
 *
 * <p>N'ouvre aucun perimetre : agit dans celui de l'appelant, qui doit etre une agence (sinon
 * {@link IllegalStateException}). L'ecriture est reservee a {@code crm.write} par le controleur.
 */
@Service
public class TravelerRegistry {

    public static final int MAX_PAGE_SIZE = 100;

    static final String AUDIT_ENTITY = "TRAVELER";
    static final String TRAVELER_ARCHIVED = "TRAVELER_ARCHIVED";
    static final String TRAVELER_RESTORED = "TRAVELER_RESTORED";

    /**
     * Fiche complete telle que l'utilisateur courant peut la voir.
     *
     * @param birthDate date de naissance, nulle si elle est absente ou masquee
     * @param birthDateHidden vrai si une date existe mais que l'utilisateur n'a pas {@code identity.read}
     * @param birthPlace lieu de naissance, nul s'il est absent ou masque
     * @param birthPlaceHidden vrai si un lieu existe mais que l'utilisateur n'a pas {@code identity.read}
     * @param customers clients rattaches (archives compris), par nom
     */
    public record TravelerView(Traveler traveler, LocalDate birthDate, boolean birthDateHidden, String birthPlace,
            boolean birthPlaceHidden, List<Customer> customers) {
    }

    private final TravelerRepository travelers;
    private final CustomerRepository customers;
    private final CustomerTravelerRepository links;
    private final ApplicationEventPublisher events;

    TravelerRegistry(TravelerRepository travelers, CustomerRepository customers, CustomerTravelerRepository links,
            ApplicationEventPublisher events) {
        this.travelers = travelers;
        this.customers = customers;
        this.links = links;
        this.events = events;
    }

    /**
     * Page de voyageurs ; {@code archived} vrai : les seuls archives ; {@code customerId} : les
     * voyageurs rattaches a ce client. La date de naissance n'est jamais un critere.
     */
    @Transactional(readOnly = true)
    public Page<Traveler> search(String q, UUID customerId, boolean archived, TravelerSort sort, int page, int size) {
        TenantContext.requireAgency();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        return travelers.findAll(TravelerSpecifications.matching(q, customerId, archived),
                PageRequest.of(page, size, sort.sort()));
    }

    /** Fiche du voyageur, archive ou non. */
    @Transactional(readOnly = true)
    public TravelerView get(UUID id) {
        return view(find(id));
    }

    /**
     * Cree le voyageur et le rattache aux clients indiques (chacun connu dans l'agence et non
     * archive). Sans {@code confirmDuplicate}, refuse (409) s'il ressemble a un voyageur non archive.
     */
    @Transactional
    public TravelerView create(TravelerData data, Set<UUID> customerIds, boolean confirmDuplicate) {
        TenantContext.requireAgency();
        boolean withBirthDate = checkBirthDateWrite(data);
        List<Customer> linked = new ArrayList<>();
        for (UUID customerId : new LinkedHashSet<>(customerIds)) {
            linked.add(linkable(customerId));
        }
        Traveler traveler = Traveler.create(data, withBirthDate);
        if (!confirmDuplicate) {
            rejectDuplicates(traveler.getNameKey(), withBirthDate ? traveler.getBirthDate() : null,
                    traveler.getEmail(), traveler.getPhone(), null);
        }
        travelers.saveAndFlush(traveler);
        for (Customer customer : linked) {
            saveLink(new CustomerTraveler(customer.getId(), traveler.getId()));
        }
        return view(traveler);
    }

    /**
     * Remplace les champs modifiables (la date et le lieu de naissance seulement avec
     * {@code identity.read}) ; ne touche pas aux liens. {@code version} doit etre celle de la fiche lue (sinon 409
     * stale-version). Le controle de doublon n'est refait que si le nom, l'e-mail, le telephone ou
     * la date envoyee change.
     */
    @Transactional
    public TravelerView update(UUID id, long version, TravelerData data, boolean confirmDuplicate) {
        Traveler traveler = find(id);
        CustomerRegistry.checkVersion(traveler, id, traveler.getVersion(), version);
        boolean replaceBirthDate = checkBirthDateWrite(data);
        // Avant la modification : une requete sur une entite modifiee declencherait son ecriture.
        String nameKey = SearchKeys.of(data.lastName(), data.firstName());
        String email = EmailAddresses.normalize(data.email());
        String phone = PhoneNumbers.normalize(data.phone());
        LocalDate sentBirthDate = replaceBirthDate ? data.birthDate() : null;
        boolean criteriaChanged = !Objects.equals(nameKey, traveler.getNameKey())
                || !Objects.equals(email, traveler.getEmail())
                || !Objects.equals(phone, traveler.getPhone())
                || (replaceBirthDate && !Objects.equals(sentBirthDate, traveler.getBirthDate()));
        if (criteriaChanged && !confirmDuplicate) {
            rejectDuplicates(nameKey, sentBirthDate, email, phone, id);
        }
        traveler.update(data, replaceBirthDate);
        travelers.flush();
        return view(traveler);
    }

    /** Archive le voyageur (idempotent ; une seule entree d'audit). Les liens restent. */
    @Transactional
    public TravelerView archive(UUID id) {
        Traveler traveler = find(id);
        if (traveler.archive(Instant.now())) {
            travelers.flush();
            audit(TRAVELER_ARCHIVED, traveler);
        }
        return view(traveler);
    }

    /** Restaure un voyageur archive (idempotent ; une seule entree d'audit). */
    @Transactional
    public TravelerView restore(UUID id) {
        Traveler traveler = find(id);
        if (traveler.restore()) {
            travelers.flush();
            audit(TRAVELER_RESTORED, traveler);
        }
        return view(traveler);
    }

    /**
     * Rattache le voyageur au client (idempotent). Client inconnu dans l'agence : 404 ; client
     * archive : 409 customer-archived (meme si le lien existe deja : on ne le recree pas).
     */
    @Transactional
    public TravelerView link(UUID id, UUID customerId) {
        Traveler traveler = find(id);
        Customer customer = linkable(customerId);
        if (!links.existsById(new CustomerTraveler.Key(customer.getId(), traveler.getId()))) {
            saveLink(new CustomerTraveler(customer.getId(), traveler.getId()));
        }
        return view(traveler);
    }

    /** Detache le voyageur du client (suppression reelle ; idempotent si le lien n'existe pas). */
    @Transactional
    public void unlink(UUID id, UUID customerId) {
        Traveler traveler = find(id);
        links.findById(new CustomerTraveler.Key(customerId, traveler.getId())).ifPresent(links::delete);
    }

    private Traveler find(UUID id) {
        TenantContext.requireAgency();
        return travelers.findById(id).orElseThrow(() -> new TravelerNotFoundException(id));
    }

    /** Client de l'agence auquel on peut rattacher un voyageur : connu (sinon 404) et non archive (sinon 409). */
    private Customer linkable(UUID customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (customer.isArchived()) {
            throw new CustomerArchivedException();
        }
        return customer;
    }

    /**
     * Le meme lien cree en meme temps par une autre transaction (cle primaire pk_customer_traveler) :
     * traduit en 409 stale-version pour que l'utilisateur recharge la fiche. Toute autre violation
     * reste une erreur imprevue.
     */
    private void saveLink(CustomerTraveler link) {
        try {
            links.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            if (CrmConstraints.isTravelerAlreadyLinked(e)) {
                throw new ObjectOptimisticLockingFailureException(CustomerTraveler.class, link.getId(), e);
            }
            throw e;
        }
    }

    private TravelerView view(Traveler traveler) {
        boolean readable = canReadIdentity();
        return new TravelerView(traveler,
                readable ? traveler.getBirthDate() : null, !readable && traveler.getBirthDate() != null,
                readable ? traveler.getBirthPlace() : null, !readable && traveler.getBirthPlace() != null,
                links.findCustomersOfTraveler(traveler.getId()));
    }

    /**
     * Regle d'ecriture de la date et du lieu de naissance : avec {@code identity.read}, les valeurs
     * envoyees remplacent les anciennes (nulles : effacees) ; sans, une date ou un lieu envoye est
     * refuse (403) et des valeurs absentes ne changent rien.
     *
     * @return vrai si la date et le lieu de {@code data} doivent etre enregistres
     */
    private static boolean checkBirthDateWrite(TravelerData data) {
        if (canReadIdentity()) {
            return true;
        }
        if (data.birthDate() != null || data.birthPlace() != null) {
            throw new IdentityReadRequiredException();
        }
        return false;
    }

    private static boolean canReadIdentity() {
        return UserContext.current().map(user -> user.has(PermissionCode.IDENTITY_READ)).orElse(false);
    }

    /** Details vides : ni nom, ni date, ni nationalite. */
    private void audit(String action, Traveler traveler) {
        AgencyId agency = TenantContext.requireAgency();
        events.publishEvent(new AuditableEvent(agency, action, AUDIT_ENTITY, traveler.getId().toString(), Map.of()));
    }

    /**
     * 409 possible-duplicate si un voyageur non archive (autre que {@code self}) a la meme cle de nom
     * et la meme date de naissance (seulement si une date est envoyee), ou le meme e-mail, ou le meme
     * telephone. Le nom seul n'est pas un critere (homonymes).
     */
    private void rejectDuplicates(String nameKey, LocalDate birthDate, String email, String phone, UUID self) {
        Map<UUID, Traveler> found = new LinkedHashMap<>();
        Map<UUID, List<Criterion>> criteria = new LinkedHashMap<>();
        if (birthDate != null) {
            collect(travelers.findTop10ByArchivedAtIsNullAndNameKeyAndBirthDateOrderByNameKeyAscIdAsc(nameKey,
                    birthDate), Criterion.NAME_AND_BIRTH_DATE, self, found, criteria);
        }
        if (email != null) {
            collect(travelers.findTop10ByArchivedAtIsNullAndEmailOrderByNameKeyAscIdAsc(email), Criterion.EMAIL,
                    self, found, criteria);
        }
        if (phone != null) {
            collect(travelers.findTop10ByArchivedAtIsNullAndPhoneOrderByNameKeyAscIdAsc(phone), Criterion.PHONE,
                    self, found, criteria);
        }
        if (!found.isEmpty()) {
            List<TravelerCandidate> candidates = found.values().stream()
                    .map(t -> new TravelerCandidate(t.getId(), t.getLastName(), t.getFirstName(),
                            List.copyOf(criteria.get(t.getId()))))
                    .toList();
            throw new PossibleDuplicateException(candidates);
        }
    }

    private static void collect(List<Traveler> matches, Criterion criterion, UUID self, Map<UUID, Traveler> found,
            Map<UUID, List<Criterion>> criteria) {
        for (Traveler match : matches) {
            if (match.getId().equals(self)) {
                continue;
            }
            found.putIfAbsent(match.getId(), match);
            criteria.computeIfAbsent(match.getId(), key -> new ArrayList<>()).add(criterion);
        }
    }
}
