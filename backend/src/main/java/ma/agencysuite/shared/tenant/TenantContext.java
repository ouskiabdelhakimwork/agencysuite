package ma.agencysuite.shared.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Agence courante, portee par une {@link ScopedValue} (Java 25).
 *
 * <p>Pourquoi une ScopedValue plutot qu'un ThreadLocal : la valeur n'existe que pendant
 * l'execution du bloc et disparait toute seule a la fin, meme en cas d'exception. Avec un
 * ThreadLocal, un oubli de nettoyage sur un thread reutilise laisserait la requete suivante
 * s'executer avec l'agence precedente : une fuite de donnees entre agences.
 *
 * <p>Sans perimetre ouvert, rien n'est visible (voir TenantIdentifierResolver) : le
 * comportement par defaut est sur. Les taches en arriere-plan (files de messages,
 * planificateur) doivent ouvrir explicitement un perimetre.
 *
 * <p>Le perimetre est ouvert par le filtre d'authentification a partir du jeton (etape 3) ;
 * en attendant, par les tests et le back-office.
 */
public final class TenantContext {

    private static final ScopedValue<TenantScope> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static void runAsAgency(AgencyId agencyId, Runnable work) {
        ScopedValue.where(CURRENT, new TenantScope.Agency(agencyId)).run(work);
    }

    public static <T> T callAsAgency(AgencyId agencyId, Supplier<T> work) {
        return ScopedValue.where(CURRENT, new TenantScope.Agency(agencyId)).call(work::get);
    }

    public static void runAsPlatform(Runnable work) {
        ScopedValue.where(CURRENT, new TenantScope.Platform()).run(work);
    }

    public static <T> T callAsPlatform(Supplier<T> work) {
        return ScopedValue.where(CURRENT, new TenantScope.Platform()).call(work::get);
    }

    public static Optional<TenantScope> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /** Agence courante ; echoue si l'on n'est pas dans le perimetre d'une agence. */
    public static AgencyId requireAgency() {
        return current()
                .filter(TenantScope.Agency.class::isInstance)
                .map(scope -> ((TenantScope.Agency) scope).agencyId())
                .orElseThrow(() -> new IllegalStateException("Aucune agence courante"));
    }
}
