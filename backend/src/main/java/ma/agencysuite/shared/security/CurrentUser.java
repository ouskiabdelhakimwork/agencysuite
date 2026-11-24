package ma.agencysuite.shared.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur de l'agence a l'origine de la requete courante, et ses permissions (union des
 * permissions de ses roles). Volontairement sans e-mail ni nom : cette valeur circule partout
 * et ne doit pas exposer de donnee personnelle.
 *
 * @param userId identifiant de la ligne user_account (pas le sub Keycloak)
 * @param permissions permissions effectives
 */
public record CurrentUser(UUID userId, Set<PermissionCode> permissions) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId");
        permissions = permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public boolean has(PermissionCode permission) {
        return permissions.contains(permission);
    }
}
