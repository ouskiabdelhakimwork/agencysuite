package ma.agencysuite.platform.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import ma.agencysuite.shared.tenant.TenantContext;

/**
 * Poursuit la chaine de filtres dans un perimetre {@link TenantContext}. TenantContext n'accepte
 * que des blocs sans exception controlee : IOException et ServletException sont transportees
 * puis relancees telles quelles.
 */
final class ScopedFilterChain {

    private ScopedFilterChain() {
    }

    static void runAsPlatform(FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        try {
            TenantContext.runAsPlatform(() -> doFilter(chain, request, response));
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    private static void doFilter(FilterChain chain, ServletRequest request, ServletResponse response) {
        try {
            chain.doFilter(request, response);
        } catch (IOException e) {
            throw new CheckedFilterException(e);
        } catch (ServletException e) {
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
