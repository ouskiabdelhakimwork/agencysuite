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
 * <p>Pour les requetes HTTP, le perimetre est ouvert par le filtre de securite a partir du
 * jeton Keycloak (module iam) : realm "platform" pour le back-office, realm "agencysuite" pour
 * l'agence de l'utilisateur.
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

    /**
     * Echoue si l'on n'est pas dans le perimetre plateforme (back-office editeur). Erreur de
     * programmation (appel depuis le mauvais point d'entree), pas une erreur de l'utilisateur.
     */
    public static void requirePlatform() {
        if (!(current().orElse(null) instanceof TenantScope.Platform)) {
            throw new IllegalStateException("Perimetre plateforme requis");
        }
    }
}
