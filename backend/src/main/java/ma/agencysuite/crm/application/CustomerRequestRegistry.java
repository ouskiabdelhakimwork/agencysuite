package ma.agencysuite.crm.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerRequest;
import ma.agencysuite.crm.domain.CustomerRequestData;
import ma.agencysuite.crm.domain.LostReason;
import ma.agencysuite.crm.domain.RequestStatus;
import ma.agencysuite.crm.infrastructure.CrmConstraints;
import ma.agencysuite.crm.infrastructure.CustomerRepository;
import ma.agencysuite.crm.infrastructure.CustomerRequestRepository;
import ma.agencysuite.crm.infrastructure.CustomerRequestSpecifications;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demandes des clients de l'agence courante (US-701) : recherche, fiche, creation, modification et
 * changement de statut controle.
 *
 * <p>Regles :
 * <ul>
 *   <li>client obligatoire, connu dans l'agence (sinon 404) et non archive (sinon 409
 *       customer-archived), a la creation et quand le client change ; une demande existante garde
 *       son client meme s'il est archive ensuite ;</li>
 *   <li>responsable : par defaut l'utilisateur d'agence courant ; sans lui (membre de l'editeur),
 *       il doit etre donne ({@link OwnerRequiredException}) ; un utilisateur d'une autre agence est
 *       refuse par la cle composee ({@link InvalidOwnerException}). Il ne restreint jamais l'acces ;</li>
 *   <li>statut : seulement par {@link #changeStatus}, selon {@link RequestStatus} ; meme statut :
 *       rien ne change, aucun audit ; chaque changement effectif est audite
 *       ({@code REQUEST_STATUS_CHANGED}, codes seulement).</li>
 * </ul>
 *
 * <p>N'ouvre aucun perimetre : agit dans celui de l'appelant, qui doit etre une agence (sinon
 * {@link IllegalStateException}). L'ecriture est reservee a {@code crm.write} par le controleur.
 */
@Service
public class CustomerRequestRegistry {

    public static final int MAX_PAGE_SIZE = 100;

    static final String AUDIT_ENTITY = "REQUEST";
    static final String REQUEST_STATUS_CHANGED = "REQUEST_STATUS_CHANGED";

    /** Demande et son client (archive ou non). */
    public record RequestView(CustomerRequest request, Customer customer) {
    }

    private final CustomerRequestRepository requests;
    private final CustomerRepository customers;
    private final ApplicationEventPublisher events;

    CustomerRequestRegistry(CustomerRequestRepository requests, CustomerRepository customers,
            ApplicationEventPublisher events) {
        this.requests = requests;
        this.customers = customers;
        this.events = events;
    }

    /** Page de demandes, chacune avec son client (lus en une requete). */
    @Transactional(readOnly = true)
    public Page<RequestView> search(RequestCriteria criteria, RequestSort sort, int page, int size) {
        TenantContext.requireAgency();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        Specification<CustomerRequest> matching = CustomerRequestSpecifications.matching(criteria.statuses(),
                criteria.ownerUserId(), criteria.customerId(), criteria.channel(), criteria.createdFrom(),
                criteria.createdTo(), criteria.q());
        Page<CustomerRequest> found = requests.findAll(matching, PageRequest.of(page, size, sort.sort()));
        List<UUID> customerIds = found.stream().map(CustomerRequest::getCustomerId).distinct().toList();
        Map<UUID, Customer> byId = customers.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return found.map(request -> new RequestView(request, byId.get(request.getCustomerId())));
    }

    /** Fiche de la demande. */
    @Transactional(readOnly = true)
    public RequestView get(UUID id) {
        return view(find(id));
    }

    /** Cree la demande au statut NEW. */
    @Transactional
    public RequestView create(CustomerRequestData data) {
        TenantContext.requireAgency();
        Customer customer = linkableCustomer(data.customerId());
        CustomerRequest request = CustomerRequest.create(withOwner(data), Instant.now());
        saveChecked(() -> requests.saveAndFlush(request));
        return new RequestView(request, customer);
    }

    /**
     * Remplace le client, le canal, la source, le responsable et le besoin ; jamais le statut.
     * {@code version} doit etre celle de la fiche lue (sinon 409 stale-version). Une demande gagnee
     * ou perdue reste modifiable.
     */
    @Transactional
    public RequestView update(UUID id, long version, CustomerRequestData data) {
        CustomerRequest request = find(id);
        CustomerRegistry.checkVersion(request, id, request.getVersion(), version);
        // Avant la modification : une requete sur une entite modifiee declencherait son ecriture.
        Customer customer = Objects.equals(data.customerId(), request.getCustomerId())
                ? customers.findById(request.getCustomerId()).orElseThrow()
                : linkableCustomer(data.customerId());
        request.update(withOwner(data));
        saveChecked(requests::flush);
        return new RequestView(request, customer);
    }

    /**
     * Change le statut selon {@link RequestStatus} ; meme statut : rien ne change (ni raison, ni
     * commentaire, ni audit). En LOST, {@code lostReason} est obligatoire (controle par l'API).
     *
     * @throws InvalidStatusTransitionException si la transition n'est pas autorisee
     */
    @Transactional
    public RequestView changeStatus(UUID id, long version, RequestStatus target, LostReason lostReason,
            String lostComment) {
        CustomerRequest request = find(id);
        CustomerRegistry.checkVersion(request, id, request.getVersion(), version);
        RequestStatus from = request.getStatus();
        if (target == from) {
            return view(request);
        }
        if (!from.canMoveTo(target)) {
            throw new InvalidStatusTransitionException(from, target);
        }
        request.moveTo(target, lostReason, lostComment, Instant.now());
        requests.flush();
        audit(request, from);
        return view(request);
    }

    private CustomerRequest find(UUID id) {
        TenantContext.requireAgency();
        return requests.findById(id).orElseThrow(() -> new CustomerRequestNotFoundException(id));
    }

    private RequestView view(CustomerRequest request) {
        return new RequestView(request, customers.findById(request.getCustomerId()).orElseThrow());
    }

    /** Client de l'agence courante et non archive. */
    private Customer linkableCustomer(UUID customerId) {
        Customer customer = customers.findById(Objects.requireNonNull(customerId, "customerId"))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (customer.isArchived()) {
            throw new CustomerArchivedException();
        }
        return customer;
    }

    /** Responsable donne, sinon l'utilisateur d'agence courant. */
    private static CustomerRequestData withOwner(CustomerRequestData data) {
        if (data.ownerUserId() != null) {
            return data;
        }
        UUID owner = UserContext.current().map(CurrentUser::userId).orElseThrow(OwnerRequiredException::new);
        return new CustomerRequestData(data.customerId(), owner, data.channel(), data.source(), data.destination(),
                data.departureDate(), data.returnDate(), data.datesNote(), data.adults(), data.children(),
                data.budget(), data.description());
    }

    /** Details : statuts et raison de perte en code ; jamais le commentaire, la destination ni le budget. */
    private void audit(CustomerRequest request, RequestStatus from) {
        AgencyId agency = TenantContext.requireAgency();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.name());
        details.put("to", request.getStatus().name());
        if (request.getStatus() == RequestStatus.LOST) {
            details.put("lostReason", request.getLostReason().name());
        }
        events.publishEvent(new AuditableEvent(agency, REQUEST_STATUS_CHANGED, AUDIT_ENTITY,
                request.getId().toString(), details));
    }

    /** Le responsable doit etre un utilisateur de l'agence : garanti par la cle composee, traduit en 400. */
    private static void saveChecked(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException e) {
            if (CrmConstraints.isUnknownRequestOwner(e)) {
                throw new InvalidOwnerException();
            }
            throw e;
        }
    }
}
