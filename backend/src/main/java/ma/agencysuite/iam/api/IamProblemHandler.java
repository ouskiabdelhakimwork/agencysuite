package ma.agencysuite.iam.api;

import java.net.URI;
import java.util.Locale;
import ma.agencysuite.iam.application.AgencyClosedException;
import ma.agencysuite.iam.application.CannotDisableSelfException;
import ma.agencysuite.iam.application.EmailAlreadyRegisteredException;
import ma.agencysuite.iam.application.IdentityAdminUnavailableException;
import ma.agencysuite.iam.application.LastManagerException;
import ma.agencysuite.iam.application.PermissionDeniedException;
import ma.agencysuite.iam.application.UnknownAgencyException;
import ma.agencysuite.iam.application.UserNotFoundException;
import ma.agencysuite.iam.application.UserNotInvitedException;
import ma.agencysuite.iam.application.UserQuotaExceededException;
import ma.agencysuite.iam.application.UserRequiredException;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs du module iam au format RFC 9457 (application/problem+json), identique a celui de
 * {@code PlatformProblemHandler} : type urn:agencysuite:problem:&lt;code&gt;, titre et detail traduits.
 * Seules les exceptions metier dediees sont traitees ici.
 */
@RestControllerAdvice
class IamProblemHandler {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;

    IamProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(PermissionDeniedException.class)
    ResponseEntity<ProblemDetail> permissionDenied(PermissionDeniedException e, Locale locale) {
        String code = e.getPermission().code();
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.FORBIDDEN, "permission-denied", locale, code);
        response.getBody().setProperty("permission", code);
        return response;
    }

    @ExceptionHandler(UserRequiredException.class)
    ResponseEntity<ProblemDetail> userRequired(UserRequiredException e, Locale locale) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", locale);
    }

    @ExceptionHandler({UserNotFoundException.class, UnknownAgencyException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, "not-found", locale);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> emailAlreadyRegistered(EmailAlreadyRegisteredException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "email-already-registered", locale);
    }

    @ExceptionHandler(UserQuotaExceededException.class)
    ResponseEntity<ProblemDetail> userQuotaExceeded(UserQuotaExceededException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "user-quota-exceeded", locale);
    }

    @ExceptionHandler(LastManagerException.class)
    ResponseEntity<ProblemDetail> lastManager(LastManagerException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "last-manager", locale);
    }

    @ExceptionHandler(CannotDisableSelfException.class)
    ResponseEntity<ProblemDetail> cannotDisableSelf(CannotDisableSelfException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "cannot-disable-self", locale);
    }

    @ExceptionHandler(UserNotInvitedException.class)
    ResponseEntity<ProblemDetail> userNotInvited(UserNotInvitedException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "user-not-invited", locale);
    }

    @ExceptionHandler(AgencyClosedException.class)
    ResponseEntity<ProblemDetail> agencyClosed(AgencyClosedException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, "agency-closed", locale);
    }

    /** Le message (operation et statut HTTP) reste dans le serveur : jamais renvoye au client. */
    @ExceptionHandler(IdentityAdminUnavailableException.class)
    ResponseEntity<ProblemDetail> identityAdminUnavailable(IdentityAdminUnavailableException e, Locale locale) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "identity-admin-unavailable", locale);
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
