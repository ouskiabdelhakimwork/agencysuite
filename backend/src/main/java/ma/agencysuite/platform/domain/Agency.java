package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import ma.agencysuite.shared.persistence.BaseEntity;

/**
 * Agence cliente : le tenant du SaaS. Toutes les donnees metier lui sont rattachees
 * par agency_id ; elle-meme n'en porte pas.
 */
@Entity
@Table(name = "agency")
public class Agency extends BaseEntity {

    /** Lettres minuscules, chiffres et tirets ; 3 a 63 caracteres ; pas de tiret en debut ou fin. */
    private static final Pattern SUBDOMAIN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");

    /** Sous-domaines reserves a la plateforme. */
    private static final Set<String> RESERVED_SUBDOMAINS =
            Set.of("www", "api", "app", "admin", "auth", "login", "platform", "static", "mail", "status", "demo");

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 15)
    private String ice;

    @Column(name = "if_number", length = 20)
    private String ifNumber;

    @Column(length = 30)
    private String rc;

    @Column(length = 30)
    private String patente;

    @Column(length = 500)
    private String address;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(nullable = false, length = 63)
    private String subdomain;

    @Column(name = "default_locale", nullable = false, length = 5)
    private String defaultLocale = "fr";

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "MAD";

    @Column(nullable = false, length = 50)
    private String timezone = "Africa/Casablanca";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgencyKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgencyStatus status;

    @Column(name = "demo_reset_at")
    private Instant demoResetAt;

    protected Agency() {
        // pour JPA
    }

    private Agency(String name, String subdomain, AgencyKind kind) {
        this.name = requireText(name, "name");
        this.subdomain = normalizeSubdomain(subdomain);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.status = AgencyStatus.TRIAL;
    }

    /** Nouvelle agence cliente, en periode d'essai. */
    public static Agency createCustomer(String name, String subdomain) {
        return new Agency(name, subdomain, AgencyKind.CUSTOMER);
    }

    /** Agence de demonstration (donnees fictives uniquement, ADR-34). Active d'emblee. */
    public static Agency createDemo(String name, String subdomain) {
        Agency agency = new Agency(name, subdomain, AgencyKind.DEMO);
        agency.status = AgencyStatus.ACTIVE;
        return agency;
    }

    /** Identite legale imprimee sur les factures (mentions a confirmer, RG-03). */
    public void updateLegalIdentity(String legalName, String ice, String ifNumber, String rc, String patente,
            String address) {
        this.legalName = legalName;
        this.ice = ice;
        this.ifNumber = ifNumber;
        this.rc = rc;
        this.patente = patente;
        this.address = address;
    }

    public void activate() {
        if (status == AgencyStatus.CLOSING || status == AgencyStatus.CLOSED) {
            throw new IllegalStateException("Une agence en cloture ne peut pas etre reactivee");
        }
        status = AgencyStatus.ACTIVE;
    }

    public void suspend() {
        if (status == AgencyStatus.CLOSED) {
            throw new IllegalStateException("Une agence close ne peut pas etre suspendue");
        }
        status = AgencyStatus.SUSPENDED;
    }

    public boolean isOperational() {
        return status == AgencyStatus.TRIAL || status == AgencyStatus.ACTIVE;
    }

    static String normalizeSubdomain(String subdomain) {
        String value = requireText(subdomain, "subdomain").toLowerCase(Locale.ROOT);
        if (!SUBDOMAIN.matcher(value).matches()) {
            throw new IllegalArgumentException("Sous-domaine invalide : " + subdomain);
        }
        if (RESERVED_SUBDOMAINS.contains(value)) {
            throw new IllegalArgumentException("Sous-domaine reserve : " + subdomain);
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value.strip();
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getIce() {
        return ice;
    }

    public String getIfNumber() {
        return ifNumber;
    }

    public String getRc() {
        return rc;
    }

    public String getPatente() {
        return patente;
    }

    public String getAddress() {
        return address;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getTimezone() {
        return timezone;
    }

    public AgencyKind getKind() {
        return kind;
    }

    public AgencyStatus getStatus() {
        return status;
    }

    public Instant getDemoResetAt() {
        return demoResetAt;
    }
}
