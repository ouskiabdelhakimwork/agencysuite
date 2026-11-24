package ma.agencysuite.platform.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ma.agencysuite.platform.application.BackofficeDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/**
 * Acces provisoire au back-office editeur (/api/v1/backoffice/**), tant que l'authentification
 * n'est pas branchee.
 *
 * <ul>
 *   <li>{@code agencysuite.backoffice.unsecured-access=false} (defaut) : 403 problem+json
 *       {@code backoffice-disabled}, la requete n'atteint jamais les controleurs ;</li>
 *   <li>{@code true} : la requete s'execute dans le perimetre plateforme, sans authentification.</li>
 * </ul>
 *
 * <p>L'erreur est confiee au {@link HandlerExceptionResolver} de Spring MVC pour produire le
 * meme format (et les memes traductions) que les erreurs des controleurs.
 */
// TODO(etape-3) : remplacer par la verification du jeton du realm Keycloak "platform".
class BackofficeAccessFilter extends OncePerRequestFilter {

    static final String BACKOFFICE_PATH = "/api/v1/backoffice";

    private static final Logger LOG = LoggerFactory.getLogger(BackofficeAccessFilter.class);
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final boolean unsecuredAccess;
    private final ObjectProvider<HandlerExceptionResolver> exceptionResolver;

    BackofficeAccessFilter(BackofficeProperties properties, ObjectProvider<HandlerExceptionResolver> exceptionResolver) {
        this.unsecuredAccess = properties.unsecuredAccess();
        this.exceptionResolver = exceptionResolver;
        if (unsecuredAccess) {
            LOG.warn("Back-office sans authentification (agencysuite.backoffice.unsecured-access=true) : "
                    + "usage local uniquement, jamais en recette ni en production.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = PATHS.getPathWithinApplication(request);
        return !(path.equals(BACKOFFICE_PATH) || path.startsWith(BACKOFFICE_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!unsecuredAccess) {
            exceptionResolver.getObject().resolveException(request, response, null, new BackofficeDisabledException());
            return;
        }
        ScopedFilterChain.runAsPlatform(chain, request, response);
    }
}
