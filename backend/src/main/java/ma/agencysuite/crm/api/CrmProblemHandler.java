package ma.agencysuite.crm.api;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.crm.application.ContactNotFoundException;
import ma.agencysuite.crm.application.ContactsCompanyOnlyException;
import ma.agencysuite.crm.application.CustomerArchivedException;
import ma.agencysuite.crm.application.CustomerHasContactsException;
import ma.agencysuite.crm.application.CustomerNotFoundException;
import ma.agencysuite.crm.application.CustomerRequestNotFoundException;
import ma.agencysuite.crm.application.IdentityReadRequiredException;
import ma.agencysuite.crm.application.InvalidOwnerException;
import ma.agencysuite.crm.application.InvalidStatusTransitionException;
import ma.agencysuite.crm.application.OwnerRequiredException;
import ma.agencysuite.crm.application.PossibleDuplicateException;
import ma.agencysuite.crm.application.TravelerNotFoundException;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs du module CRM au format RFC 9457 (meme forme que les autres modules). Seules les
 * exceptions metier dediees sont traitees ici ; le conflit de version est traite dans shared.
 */
@RestControllerAdvice
class CrmProblemHandler {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;

    CrmProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler({CustomerNotFoundException.class, ContactNotFoundException.class,
            TravelerNotFoundException.class, CustomerRequestNotFoundException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, "not-found", locale);
    }

    /**
     * Les candidats (clients : identifiant, nom ; voyageurs : identifiant, nom, prenom ; criteres communs)
     * sont de la meme agence que l'appelant. Jamais de date de naissance.
     */
    @ExceptionHandler(PossibleDuplicateException.class)
    ResponseEntity<ProblemDetail> possibleDuplicate(PossibleDuplicateException e, Locale locale) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.CONFLICT, "possible-duplicate", locale);
        response.getBody().setProperty("candidates", e.getCandidates());
        return response;
    }

    @ExceptionHandler(ContactsCompanyOnlyException.class)
    ResponseEntity<ProblemDetail> contactsCompanyOnly(ContactsCompanyOnlyException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "contacts-company-only", locale);
    }

    @ExceptionHandler(CustomerHasContactsException.class)
    ResponseEntity<ProblemDetail> customerHasContacts(CustomerHasContactsException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "customer-has-contacts", locale);
    }

    @ExceptionHandler(CustomerArchivedException.class)
    ResponseEntity<ProblemDetail> customerArchived(CustomerArchivedException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "customer-archived", locale);
    }

    /** Meme forme que la garde @RequiresPermission (module iam) : code permission-denied et permission. */
    @ExceptionHandler(IdentityReadRequiredException.class)
    ResponseEntity<ProblemDetail> identityReadRequired(IdentityReadRequiredException e, Locale locale) {
        String code = e.getPermission().code();
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.FORBIDDEN, "permission-denied", locale, code);
        response.getBody().setProperty("permission", code);
        return response;
    }

    @ExceptionHandler(InvalidOwnerException.class)
    ResponseEntity<ProblemDetail> invalidOwner(InvalidOwnerException e, Locale locale) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-owner", locale);
    }

    /** Statuts de depart et d'arrivee en code (le frontend affiche les libelles), dans le detail et a part. */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<ProblemDetail> invalidStatusTransition(InvalidStatusTransitionException e, Locale locale) {
        String from = e.getFrom().name();
        String to = e.getTo().name();
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.CONFLICT, "invalid-status-transition", locale,
                from, to);
        response.getBody().setProperty("from", from);
        response.getBody().setProperty("to", to);
        return response;
    }

    /** Meme code que la Bean Validation (validation-failed), detail propre au responsable manquant. */
    @ExceptionHandler(OwnerRequiredException.class)
    ResponseEntity<ProblemDetail> ownerRequired(OwnerRequiredException e, Locale locale) {
        return problemWithDetail(HttpStatus.BAD_REQUEST, "validation-failed", "problem.validation-failed.owner.detail",
                locale);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, Locale requested, Object... args) {
        return problemWithDetail(status, code, "problem." + code + ".detail", requested, args);
    }

    private ResponseEntity<ProblemDetail> problemWithDetail(HttpStatus status, String code, String detailKey,
            Locale requested, Object... args) {
        Locale locale = SupportedLocale.from(requested).locale();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(messages.getMessage("problem." + code + ".title", null, locale));
        problem.setDetail(messages.getMessage(detailKey, args, locale));
        return ResponseEntity.status(status).body(problem);
    }
}
