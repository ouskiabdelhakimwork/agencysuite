package ma.agencysuite.iam.application;

import java.util.Objects;

/**
 * Organization du fournisseur d'identite rattachee a une agence.
 *
 * @param id identifiant de l'Organization chez le fournisseur
 * @param created vrai si elle vient d'etre creee par cet appel (audit ORGANIZATION_PROVISIONED)
 */
public record ProvisionedOrganization(String id, boolean created) {

    public ProvisionedOrganization {
        Objects.requireNonNull(id, "id");
    }
}
