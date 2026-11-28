package ma.agencysuite.crm.application;

import ma.agencysuite.shared.security.PermissionCode;

/**
 * Une date de naissance a ete envoyee par un utilisateur sans la permission {@code identity.read}
 * : 403 permission-denied (meme forme que la garde {@code @RequiresPermission}).
 */
public class IdentityReadRequiredException extends CrmException {

    public IdentityReadRequiredException() {
        super("Permission identity.read requise pour la date de naissance");
    }

    public PermissionCode getPermission() {
        return PermissionCode.IDENTITY_READ;
    }
}
