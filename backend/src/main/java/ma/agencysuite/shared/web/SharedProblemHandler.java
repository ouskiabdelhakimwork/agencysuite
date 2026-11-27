package ma.agencysuite.shared.web;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs communes a tous les modules, au format RFC 9457 (meme forme que les gestionnaires des
 * modules). Une seule exception precise ici : le conflit de verrouillage optimiste.
 */
@RestControllerAdvice
class SharedProblemHandler {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;

    SharedProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    /**
     * La fiche a ete modifiee entre sa lecture et l'enregistrement : version envoyee perimee, ou
     * modification concurrente detectee par Hibernate ({@code @Version}). 409 {@code stale-version}.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> staleVersion(ObjectOptimisticLockingFailureException e, Locale requested) {
        Locale locale = SupportedLocale.from(requested).locale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(TYPE_PREFIX + "stale-version"));
        problem.setTitle(messages.getMessage("problem.stale-version.title", null, locale));
        problem.setDetail(messages.getMessage("problem.stale-version.detail", null, locale));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
