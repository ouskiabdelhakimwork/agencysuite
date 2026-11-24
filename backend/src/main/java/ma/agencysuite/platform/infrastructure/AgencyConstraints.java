package ma.agencysuite.platform.infrastructure;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reconnaissance des contraintes de la table agency (migration V1) dans les erreurs de la base.
 * Seule la contrainte nommee est reconnue : toute autre violation reste une erreur imprevue.
 */
public final class AgencyConstraints {

    /** Contrainte unique sur agency.subdomain (V1__create_platform_tables.sql). */
    public static final String SUBDOMAIN_UNIQUE = "uk_agency_subdomain";

    private AgencyConstraints() {
    }

    /** Vrai si l'erreur est la violation de {@value #SUBDOMAIN_UNIQUE} (sous-domaine deja pris). */
    public static boolean isSubdomainTaken(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && SUBDOMAIN_UNIQUE.equals(name.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }
}
