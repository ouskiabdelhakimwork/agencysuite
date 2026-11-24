package ma.agencysuite.iam.application;

import ma.agencysuite.shared.security.PermissionCode;

/** L'utilisateur courant n'a pas la permission requise (403 permission-denied). */
public class PermissionDeniedException extends RuntimeException {

    private final PermissionCode permission;

    public PermissionDeniedException(PermissionCode permission) {
        super("Permission requise : " + permission.code());
        this.permission = permission;
    }

    public PermissionCode getPermission() {
        return permission;
    }
}
