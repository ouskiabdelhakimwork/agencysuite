package ma.agencysuite.shared.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Utilisateur courant, porte par une {@link ScopedValue}, sur le modele de
 * {@link ma.agencysuite.shared.tenant.TenantContext} : la valeur n'existe que pendant
 * l'execution du bloc, sans risque de fuite d'une requete a l'autre.
 *
 * <p>Ouvert par le filtre de securite (module iam) pour les requetes d'agence, une fois
 * l'utilisateur retrouve ACTIVE dans l'agence du jeton. Sans utilisateur, aucune permission.
 */
public final class UserContext {

    private static final ScopedValue<CurrentUser> CURRENT = ScopedValue.newInstance();

    private UserContext() {
    }

    public static void runAs(CurrentUser user, Runnable work) {
        ScopedValue.where(CURRENT, user).run(work);
    }

    public static <T> T callAs(CurrentUser user, Supplier<T> work) {
        return ScopedValue.where(CURRENT, user).call(work::get);
    }

    public static Optional<CurrentUser> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /** Utilisateur courant ; echoue s'il n'y en a pas (erreur de programmation). */
    public static CurrentUser require() {
        return current().orElseThrow(() -> new IllegalStateException("Aucun utilisateur courant"));
    }
}
