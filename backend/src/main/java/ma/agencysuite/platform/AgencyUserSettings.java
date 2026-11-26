package ma.agencysuite.platform;

import java.util.Objects;

/**
 * Parametres d'une agence utiles a la gestion de ses utilisateurs (module iam), lus par
 * {@link PlatformApi#userSettings}.
 *
 * @param name nom de l'agence
 * @param subdomain sous-domaine (unique sur la plateforme)
 * @param defaultLocale langue par defaut de l'agence ({@code fr}, {@code ar} ou {@code en})
 * @param closed vrai si l'agence est close (plus aucun utilisateur ne peut y etre ajoute)
 * @param subscribed vrai si l'agence a un abonnement actif
 * @param maxUsers nombre maximal d'utilisateurs du palier actif ; {@code null} = illimite (ou pas
 *        d'abonnement, voir {@link #allowsAnotherUser})
 */
public record AgencyUserSettings(String name, String subdomain, String defaultLocale, boolean closed,
        boolean subscribed, Integer maxUsers) {

    public AgencyUserSettings {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(subdomain, "subdomain");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
    }

    /**
     * Vrai si un utilisateur de plus est permis alors que l'agence en compte deja {@code counted}
     * (utilisateurs non desactives). Ferme par defaut : sans abonnement actif, aucun.
     */
    public boolean allowsAnotherUser(long counted) {
        return subscribed && (maxUsers == null || counted < maxUsers);
    }
}
