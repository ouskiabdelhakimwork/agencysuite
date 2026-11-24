package ma.agencysuite.platform.api;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.platform.application.AgencyScopeRequiredException;
import ma.agencysuite.platform.application.BackofficeDisabledException;
import ma.agencysuite.platform.application.InvalidSubdomainException;
import ma.agencysuite.platform.application.ModuleNotEnabledException;
import ma.agencysuite.platform.application.ResourceNotFoundException;
import ma.agencysuite.platform.application.SubdomainAlreadyTakenException;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs du module Plateforme au format RFC 9457 (application/problem+json).
 *
 * <p>{@code title} et {@code detail} sont traduits (cles {@code problem.<code>.title / .detail}).
 * Le message interne des exceptions n'est jamais renvoye au client (ni trace, ni SQL).
 * Seules les exceptions metier dediees sont traitees ici : toute autre exception imprevue
 * reste une 500. Les erreurs de lecture JSON et de Bean Validation restent gerees par le
 * gestionnaire standard de Spring Boot, traduit via les cles {@code problemDetail.*}.
 */
@RestControllerAdvice
class PlatformProblemHandler {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;

    PlatformProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(SubdomainAlreadyTakenException.class)
    ResponseEntity<ProblemDetail> subdomainTaken(SubdomainAlreadyTakenException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "subdomain-taken", locale);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, "not-found", locale);
    }

    @ExceptionHandler(ModuleNotEnabledException.class)
    ResponseEntity<ProblemDetail> moduleNotEnabled(ModuleNotEnabledException e, Locale locale) {
        ResponseEntity<ProblemDetail> response =
                problem(HttpStatus.FORBIDDEN, "module-not-enabled", locale, e.getModule().name());
        response.getBody().setProperty("module", e.getModule().name());
        return response;
    }

    @ExceptionHandler(AgencyScopeRequiredException.class)
    ResponseEntity<ProblemDetail> agencyScopeRequired(AgencyScopeRequiredException e, Locale locale) {
        return problem(HttpStatus.FORBIDDEN, "agency-scope-required", locale);
    }

    @ExceptionHandler(BackofficeDisabledException.class)
    ResponseEntity<ProblemDetail> backofficeDisabled(BackofficeDisabledException e, Locale locale) {
        return problem(HttpStatus.FORBIDDEN, "backoffice-disabled", locale);
    }

    @ExceptionHandler(InvalidSubdomainException.class)
    ResponseEntity<ProblemDetail> invalidSubdomain(InvalidSubdomainException e, Locale locale) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "invalid-subdomain", locale);
        response.getBody().setProperty("reason", e.getReason().name());
        return response;
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, Locale requested, Object... args) {
        Locale locale = SupportedLocale.from(requested).locale();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(messages.getMessage("problem." + code + ".title", null, locale));
        problem.setDetail(messages.getMessage("problem." + code + ".detail", args, locale));
        return ResponseEntity.status(status).body(problem);
    }
}
