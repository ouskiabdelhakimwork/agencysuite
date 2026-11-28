package ma.agencysuite.supplier.api;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.shared.i18n.SupportedLocale;
import ma.agencysuite.supplier.application.PossibleDuplicateException;
import ma.agencysuite.supplier.application.RateOverlapException;
import ma.agencysuite.supplier.application.SupplierException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs du module supplier au format RFC 9457 (meme forme que les autres modules). Seules les
 * exceptions metier dediees sont traitees ici ; le conflit de version et le montant refuse sont
 * traites dans shared.
 */
@RestControllerAdvice
class SupplierProblemHandler {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;

    SupplierProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(SupplierException.NotFound.class)
    ResponseEntity<ProblemDetail> notFound(SupplierException.NotFound e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, "not-found", locale);
    }

    /** Meme forme que pour les clients (module crm) : candidats {@code id}, {@code name}, {@code criteria}. */
    @ExceptionHandler(PossibleDuplicateException.class)
    ResponseEntity<ProblemDetail> possibleDuplicate(PossibleDuplicateException e, Locale locale) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.CONFLICT, "possible-duplicate", locale);
        response.getBody().setProperty("candidates", e.getCandidates());
        return response;
    }

    @ExceptionHandler(SupplierException.SupplierArchived.class)
    ResponseEntity<ProblemDetail> supplierArchived(SupplierException.SupplierArchived e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "supplier-archived", locale);
    }

    @ExceptionHandler(SupplierException.ProductArchived.class)
    ResponseEntity<ProblemDetail> productArchived(SupplierException.ProductArchived e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "product-archived", locale);
    }

    /** Tarif en conflit : identifiant et periode (dates ISO), de la meme agence et de la meme prestation. */
    @ExceptionHandler(RateOverlapException.class)
    ResponseEntity<ProblemDetail> rateOverlap(RateOverlapException e, Locale locale) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.CONFLICT, "rate-overlap", locale);
        ProblemDetail body = response.getBody();
        body.setProperty("conflictingRateId", e.getConflictingRateId());
        body.setProperty("validFrom", e.getValidFrom().toString());
        body.setProperty("validTo", e.getValidTo().toString());
        return response;
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
