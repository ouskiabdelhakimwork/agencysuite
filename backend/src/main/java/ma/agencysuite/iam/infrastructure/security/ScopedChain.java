package ma.agencysuite.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;

/**
 * Execute la suite d'un filtre dans un perimetre {@link TenantContext}. TenantContext n'accepte
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

    static void runAsPlatform(FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        try {
            TenantContext.runAsPlatform(() -> unchecked(() -> chain.doFilter(request, response)));
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
