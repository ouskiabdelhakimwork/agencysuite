package ma.agencysuite.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Auteur d'une action : son type et son identifiant technique (ligne user_account ou
 * platform_user). Jamais de nom ni d'e-mail : l'identite passe par l'identifiant.
 *
 * @param type origine de l'action
 * @param id identifiant de l'utilisateur ; {@code null} pour {@link ActorType#SYSTEM} uniquement
 */
public record Actor(ActorType type, UUID id) {

    /** Acteur des traitements sans utilisateur. */
    public static final Actor SYSTEM = new Actor(ActorType.SYSTEM, null);

    public Actor {
        Objects.requireNonNull(type, "type");
        if ((type == ActorType.SYSTEM) != (id == null)) {
            throw new IllegalArgumentException("Identifiant requis pour un utilisateur, interdit pour SYSTEM");
        }
    }

    public static Actor agencyUser(UUID userId) {
        return new Actor(ActorType.AGENCY_USER, Objects.requireNonNull(userId, "userId"));
    }

    public static Actor platformUser(UUID platformUserId) {
        return new Actor(ActorType.PLATFORM_USER, Objects.requireNonNull(platformUserId, "platformUserId"));
    }
}
