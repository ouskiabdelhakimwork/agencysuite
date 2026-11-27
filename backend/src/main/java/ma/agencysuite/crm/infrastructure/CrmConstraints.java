package ma.agencysuite.crm.infrastructure;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reconnaissance des contraintes des tables du module (migration V6) dans les erreurs de la base.
 * Seule une contrainte nommee est reconnue : toute autre violation reste une erreur imprevue.
 */
public final class CrmConstraints {

    /** Le responsable du client doit etre un utilisateur de la meme agence. */
    public static final String OWNER_SAME_AGENCY = "fk_customer_owner_same_agency";

    /** Au plus un contact principal par client (index unique partiel). */
    public static final String ONE_PRIMARY_CONTACT = "uk_contact_one_primary";

    private CrmConstraints() {
    }

    /** Vrai si l'erreur est la violation de {@value #OWNER_SAME_AGENCY} (responsable inconnu dans l'agence). */
    public static boolean isUnknownOwner(DataIntegrityViolationException error) {
        return violates(error, OWNER_SAME_AGENCY);
    }

    /**
     * Vrai si l'erreur est la violation de {@value #ONE_PRIMARY_CONTACT} : une autre transaction a
     * designe un contact principal entre la lecture et l'ecriture.
     */
    public static boolean isPrimaryContactTaken(DataIntegrityViolationException error) {
        return violates(error, ONE_PRIMARY_CONTACT);
    }

    private static boolean violates(DataIntegrityViolationException error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && constraint.equals(name.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }
}
