package ma.agencysuite.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import ma.agencysuite.shared.security.Actor;
import ma.agencysuite.shared.security.ActorContext;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;

/**
 * Execute la suite d'un filtre dans un perimetre {@link TenantContext} (et, pour une agence,
 * avec l'utilisateur courant {@link UserContext}), avec l'auteur des actions ({@link ActorContext},
 * journal d'audit). Ces contextes n'acceptent
 * que des blocs sans exception controlee : IOException et ServletException sont transportees
 * puis relancees telles quelles.
 */
final class ScopedChain {

    /** Bloc execute dans le perimetre, pouvant lever les exceptions d'un filtre. */
    @FunctionalInterface
    interface FilterWork {
        void run() throws IOException, ServletException;
    }

    private ScopedChain() {
    }

    static void runAsPlatform(FilterWork work) throws IOException, ServletException {
        try {
            TenantContext.runAsPlatform(() -> unchecked(work));
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    /**
     * Execute la suite de la chaine avec cet utilisateur d'agence courant ({@link UserContext}), auteur
     * des actions ({@link ActorContext}).
     */
    static void runAsUser(CurrentUser user, FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        try {
            ActorContext.runAs(Actor.agencyUser(user.userId()), () -> UserContext.runAs(user,
                    () -> unchecked(() -> chain.doFilter(request, response))));
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    /** Execute la suite de la chaine avec ce membre de l'editeur comme auteur des actions. */
    static void runAsPlatformUser(UUID platformUserId, FilterChain chain, ServletRequest request,
            ServletResponse response) throws IOException, ServletException {
        try {
            ActorContext.runAs(Actor.platformUser(platformUserId),
                    () -> unchecked(() -> chain.doFilter(request, response)));
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    static void runAsAgency(AgencyId agencyId, FilterWork work) throws IOException, ServletException {
        try {
            TenantContext.runAsAgency(agencyId, () -> unchecked(work));
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    private static void unchecked(FilterWork work) {
        try {
            work.run();
        } catch (IOException | ServletException e) {
            throw new CheckedFilterException(e);
        }
    }

    private static final class CheckedFilterException extends RuntimeException {

        private CheckedFilterException(Exception cause) {
            super(cause);
        }

        void rethrow() throws IOException, ServletException {
            switch (getCause()) {
                case IOException io -> throw io;
                case ServletException servlet -> throw servlet;
                default -> throw new IllegalStateException(getCause());
            }
        }
    }
}
