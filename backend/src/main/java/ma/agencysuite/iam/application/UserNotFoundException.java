package ma.agencysuite.iam.application;

import java.util.UUID;

/**
 * Utilisateur inconnu dans l'agence courante : 404 not-found. Un utilisateur d'une autre agence
 * donne la meme reponse (il est de toute facon invisible : filtre @TenantId + RLS).
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("Utilisateur introuvable : " + userId);
    }
}
