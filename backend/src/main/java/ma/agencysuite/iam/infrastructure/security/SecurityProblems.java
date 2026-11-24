package ma.agencysuite.iam.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Erreurs de la couche de securite au format RFC 9457 (application/problem+json), identique a
 * celui des controleurs ({@code PlatformProblemHandler}) : {@code type} = urn:agencysuite:problem:&lt;code&gt;,
 * {@code title} et {@code detail} traduits (cles problem.&lt;code&gt;.*), langue selon Accept-Language.
 *
 * <p>Ces erreurs sont produites avant Spring MVC (dans les filtres) : elles ne passent donc pas
 * par {@code @RestControllerAdvice}. Aucune raison precise du rejet n'est renvoyee (ni dans le
 * corps, ni dans l'en-tete {@code WWW-Authenticate}).
 */
final class SecurityProblems {

    static final String TYPE_PREFIX = "urn:agencysuite:problem:";

    private final MessageSource messages;
    private final LocaleResolver localeResolver;
    private final JsonMapper json;

    SecurityProblems(MessageSource messages, LocaleResolver localeResolver, JsonMapper json) {
        this.messages = messages;
        this.localeResolver = localeResolver;
        this.json = json;
    }

    /** 401 : jeton absent, invalide, expire ou d'un autre realm. Point d'entree d'authentification. */
    void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    /** 403 : jeton valide mais non autorise ici. Gestionnaire de refus d'acces. */
    void accessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "access-denied");
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Locale locale = SupportedLocale.from(localeResolver.resolveLocale(request)).locale();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_PREFIX + code);
        body.put("title", messages.getMessage("problem." + code + ".title", null, locale));
        body.put("status", status.value());
        body.put("detail", messages.getMessage("problem." + code + ".detail", null, locale));
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(json.writeValueAsBytes(body));
    }
}
