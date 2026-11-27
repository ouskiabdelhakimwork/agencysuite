package ma.agencysuite.crm.api;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.crm.application.ContactNotFoundException;
import ma.agencysuite.crm.application.ContactsCompanyOnlyException;
import ma.agencysuite.crm.application.CustomerHasContactsException;
import ma.agencysuite.crm.application.CustomerNotFoundException;
import ma.agencysuite.crm.application.InvalidOwnerException;
import ma.agencysuite.crm.application.PossibleDuplicateException;
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

    @ExceptionHandler({CustomerNotFoundException.class, ContactNotFoundException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, "not-found", locale);
    }

    /** Les candidats (identifiant, nom, criteres communs) sont de la meme agence que l'appelant. */
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

    @ExceptionHandler(InvalidOwnerException.class)
    ResponseEntity<ProblemDetail> invalidOwner(InvalidOwnerException e, Locale locale) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-owner", locale);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, Locale requested) {
        Locale locale = SupportedLocale.from(requested).locale();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(messages.getMessage("problem." + code + ".title", null, locale));
        problem.setDetail(messages.getMessage("problem." + code + ".detail", null, locale));
        return ResponseEntity.status(status).body(problem);
    }
}
