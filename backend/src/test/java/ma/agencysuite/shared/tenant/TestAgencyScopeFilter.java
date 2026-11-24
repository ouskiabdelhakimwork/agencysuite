package ma.agencysuite.shared.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TESTS UNIQUEMENT (src/test, jamais en production) : ouvre le perimetre de l'agence indiquee
 * par l'en-tete {@code X-Test-Agency}, en attendant le filtre d'authentification de l'etape 3.
 * Sans en-tete, la requete s'execute sans perimetre.
 */
@Component
public class TestAgencyScopeFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Test-Agency";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AgencyId agency = AgencyId.of(request.getHeader(HEADER));
        Exception[] failure = new Exception[1];
        TenantContext.runAsAgency(agency, () -> {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException e) {
                failure[0] = e;
            }
        });
        if (failure[0] instanceof IOException io) {
            throw io;
        }
        if (failure[0] instanceof ServletException servlet) {
            throw servlet;
        }
    }
}
