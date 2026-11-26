package ma.agencysuite.shared.security;

import java.util.function.Supplier;

/**
 * Auteur de la requete courante, porte par une {@link ScopedValue} sur le modele de
 * {@link UserContext}. Ouvert par le filtre de securite (module iam) pour les deux chaines :
 * utilisateur d'agence et membre de l'editeur. Sans contexte ouvert (tache planifiee, migration,
 * initialisation), l'auteur est {@link Actor#SYSTEM}.
 */
public final class ActorContext {

    private static final ScopedValue<Actor> CURRENT = ScopedValue.newInstance();

    private ActorContext() {
    }

    public static void runAs(Actor actor, Runnable work) {
        ScopedValue.where(CURRENT, actor).run(work);
    }

    public static <T> T callAs(Actor actor, Supplier<T> work) {
        return ScopedValue.where(CURRENT, actor).call(work::get);
    }

    /** Auteur courant, {@link Actor#SYSTEM} sans contexte ouvert. */
    public static Actor current() {
        return CURRENT.isBound() ? CURRENT.get() : Actor.SYSTEM;
    }
}
