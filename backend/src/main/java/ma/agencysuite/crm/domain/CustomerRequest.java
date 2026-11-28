package ma.agencysuite.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.money.Money;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;
import ma.agencysuite.shared.text.SearchKeys;

/**
 * Demande d'un client de l'agence (US-701) : canal, source, responsable, besoin exprime et statut.
 * Table {@code customer_request} (et non {@code request}, trop generique).
 *
 * <p>Regles portees par l'entite : au moins une personne (adultes + enfants) ; date de retour
 * posterieure ou egale a la date de depart si les deux sont presentes ; budget strictement
 * positif ; une raison de perte en {@link RequestStatus#LOST} et seulement dans ce statut (aussi
 * verifie en base). Le statut ne change que par {@link #moveTo}, selon les transitions de
 * {@link RequestStatus}. Les dates passees sont acceptees (saisie d'historique). Pas de
 * suppression : une demande abandonnee passe en LOST.
 */
@Entity
@Table(name = "customer_request")
public class CustomerRequest extends AuthoredAgencyEntity {

    public static final int DEFAULT_ADULTS = 1;
    public static final int DEFAULT_CHILDREN = 0;
    public static final int MAX_PEOPLE = 999;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CustomerSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lost_reason", length = 30)
    private LostReason lostReason;

    @Column(name = "lost_comment", length = 500)
    private String lostComment;

    @Column(length = 200)
    private String destination;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "dates_note", length = 200)
    private String datesNote;

    @Column(nullable = false)
    private short adults;

    @Column(nullable = false)
    private short children;

    @Column(name = "budget_amount", precision = 19, scale = 4)
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(length = 4000)
    private String description;

    /** SearchKeys.words de la destination ; jamais renvoyee par l'API. */
    @Column(name = "search_key", nullable = false, length = 500)
    private String searchKey;

    protected CustomerRequest() {
        // pour JPA
    }

    /** Nouvelle demande, au statut {@link RequestStatus#NEW}. */
    public static CustomerRequest create(CustomerRequestData data, Instant now) {
        CustomerRequest request = new CustomerRequest();
        request.apply(data);
        request.status = RequestStatus.NEW;
        request.statusChangedAt = Objects.requireNonNull(now, "now");
        return request;
    }

    /** Remplace le client, le canal, la source, le responsable et le besoin ; jamais le statut. */
    public void update(CustomerRequestData data) {
        apply(data);
    }

    private void apply(CustomerRequestData data) {
        int adultCount = data.adults() == null ? DEFAULT_ADULTS : data.adults();
        int childCount = data.children() == null ? DEFAULT_CHILDREN : data.children();
        if (adultCount < 0 || adultCount > MAX_PEOPLE || childCount < 0 || childCount > MAX_PEOPLE
                || adultCount + childCount < 1) {
            throw new IllegalArgumentException("Nombre de personnes invalide");
        }
        if (data.departureDate() != null && data.returnDate() != null
                && data.returnDate().isBefore(data.departureDate())) {
            throw new IllegalArgumentException("Date de retour avant la date de depart");
        }
        this.customerId = Objects.requireNonNull(data.customerId(), "customerId");
        this.ownerUserId = Objects.requireNonNull(data.ownerUserId(), "ownerUserId");
        this.channel = Objects.requireNonNull(data.channel(), "channel");
        this.source = data.source();
        this.destination = Texts.trimToNull(data.destination());
        this.departureDate = data.departureDate();
        this.returnDate = data.returnDate();
        this.datesNote = Texts.trimToNull(data.datesNote());
        this.adults = (short) adultCount;
        this.children = (short) childCount;
        Money budget = data.budget() == null ? null : data.budget().requirePositive();
        this.budgetAmount = budget == null ? null : budget.amount();
        this.budgetCurrency = budget == null ? null : budget.currency().getCurrencyCode();
        this.description = Texts.trimToNull(data.description());
        this.searchKey = SearchKeys.words(this.destination);
    }

    /**
     * Passe au statut {@code target}. Meme statut : rien ne change (idempotent, raison et
     * commentaire gardes). En {@link RequestStatus#LOST}, {@code lostReason} est obligatoire et
     * {@code lostComment} facultatif ; quitter LOST (reouverture) efface la raison et le commentaire.
     *
     * @return vrai si le statut a change
     * @throws IllegalStateException si la transition n'est pas autorisee (a verifier avant avec
     *         {@link RequestStatus#canMoveTo})
     */
    public boolean moveTo(RequestStatus target, LostReason reason, String comment, Instant now) {
        Objects.requireNonNull(target, "target");
        if (target == status) {
            return false;
        }
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Transition refusee : " + status + " -> " + target);
        }
        if (target == RequestStatus.LOST) {
            this.lostReason = Objects.requireNonNull(reason, "lostReason");
            this.lostComment = Texts.trimToNull(comment);
        } else {
            this.lostReason = null;
            this.lostComment = null;
        }
        this.status = target;
        this.statusChangedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public RequestChannel getChannel() {
        return channel;
    }

    public CustomerSource getSource() {
        return source;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public LostReason getLostReason() {
        return lostReason;
    }

    public String getLostComment() {
        return lostComment;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public String getDatesNote() {
        return datesNote;
    }

    public int getAdults() {
        return adults;
    }

    public int getChildren() {
        return children;
    }

    /** Budget indicatif exact, dans sa devise ; {@code null} s'il n'est pas renseigne. */
    public Money getBudget() {
        return budgetAmount == null ? null : Money.of(budgetAmount, Currency.getInstance(budgetCurrency));
    }

    public String getDescription() {
        return description;
    }
}
