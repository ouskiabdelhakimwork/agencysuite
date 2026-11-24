package ma.agencysuite.iam.application;

import java.util.UUID;

/** Membre de l'editeur connecte. */
public record PlatformUserProfile(UUID id, String email, String fullName, String role) {
}
