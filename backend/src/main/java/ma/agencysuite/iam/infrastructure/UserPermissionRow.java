package ma.agencysuite.iam.infrastructure;

import java.util.UUID;

/** Ligne de {@link UserAccountRepository#findWithPermissions} : permission nulle si aucun role. */
public record UserPermissionRow(UUID userId, String permissionCode) {
}
