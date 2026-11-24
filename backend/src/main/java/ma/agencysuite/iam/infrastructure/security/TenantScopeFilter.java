package ma.agencysuite.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ouvre le perimetre {@link TenantContext} de la requete a partir du jeton, puis poursuit la
 * chaine dans ce perimetre. Place apres le filtre d'autorisation : seules les requetes deja
 * authentifiees et autorisees (bon realm) arrivent ici avec un jeton.
 *
 * <ul>
 *   <li>Chaine du back-office : jeton du realm "platform" &rarr; perimetre plateforme.</li>
 *   <li>Chaine des agences : jeton du realm "agencysuite" &rarr; perimetre de l'agence lue dans le
 *       claim {@code organization}. Ce claim est la liste des alias des Organizations Keycloak de
 *       l'utilisateur ; l'alias est l'UUID de l'agence. Exactement une Organization est attendue.
 *       L'agence doit exister et etre operationnelle (verifie dans son propre perimetre).</li>
 * </ul>
 *
 * <p>Requete sans jeton du realm attendu (route publique) : pas de perimetre, donc rien de visible.
 * Le jeton et l'e-mail ne sont jamais journalises ; seuls {@code sub} (pseudonyme) et l'UUID de
 * l'agence le sont, en DEBUG.
 */
final class TenantScopeFilter extends OncePerRequestFilter {

    static final String ORGANIZATION_CLAIM = "organization";

    private static final Logger LOG = LoggerFactory.getLogger(TenantScopeFilter.class);
    /** UUID sous sa forme canonique (UUID.fromString accepte aussi des formes abregees). */
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private enum Scope { PLATFORM, AGENCY }

    private final Scope scope;
    private final String issuer;
    private final PlatformApi platform;
    private final SecurityProblems problems;

    private TenantScopeFilter(Scope scope, String issuer, PlatformApi platform, SecurityProblems problems) {
        this.scope = scope;
        this.issuer = issuer;
        this.platform = platform;
        this.problems = problems;
    }

    static TenantScopeFilter platform(String platformIssuer, SecurityProblems problems) {
        return new TenantScopeFilter(Scope.PLATFORM, platformIssuer, null, problems);
    }

    static TenantScopeFilter agency(String agencyIssuer, PlatformApi platform, SecurityProblems problems) {
        return new TenantScopeFilter(Scope.AGENCY, agencyIssuer, platform, problems);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !issuer.equals(token.getToken().getClaimAsString(JwtClaimNames.ISS))) {
            chain.doFilter(request, response);
            return;
        }
        Jwt jwt = token.getToken();
        if (scope == Scope.PLATFORM) {
            LOG.debug("Perimetre plateforme pour sub={}", jwt.getSubject());
            ScopedChain.runAsPlatform(chain, request, response);
            return;
        }

        Object claim = jwt.getClaims().get(ORGANIZATION_CLAIM);
        if (claim == null || claim instanceof Collection<?> none && none.isEmpty()) {
            LOG.debug("Aucune Organization dans le jeton de sub={}", jwt.getSubject());
            problems.write(request, response, HttpStatus.FORBIDDEN, "agency-scope-required");
            return;
        }
        AgencyId agencyId = singleAgency(claim);
        if (agencyId == null) {
            LOG.debug("Claim organization refuse (plusieurs Organizations ou alias invalide) pour sub={}",
                    jwt.getSubject());
            problems.write(request, response, HttpStatus.FORBIDDEN, "access-denied");
            return;
        }
        ScopedChain.runAsAgency(agencyId, () -> {
            if (!platform.isOperational(agencyId)) {
                // Meme reponse pour une agence inconnue, suspendue ou fermee : on ne revele pas si elle existe.
                LOG.debug("Agence {} indisponible pour sub={}", agencyId, jwt.getSubject());
                problems.write(request, response, HttpStatus.FORBIDDEN, "agency-unavailable");
                return;
            }
            LOG.debug("Perimetre agence {} pour sub={}", agencyId, jwt.getSubject());
            chain.doFilter(request, response);
        });
    }

    /** Agence unique du claim (liste d'un seul alias au format UUID), sinon {@code null}. */
    private static AgencyId singleAgency(Object claim) {
        if (!(claim instanceof Collection<?> aliases) || aliases.size() != 1
                || !(aliases.iterator().next() instanceof String alias)
                || !CANONICAL_UUID.matcher(alias).matches()) {
            return null;
        }
        return new AgencyId(UUID.fromString(alias));
    }
}
