package ma.agencysuite.iam.application;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.shared.tenant.AgencyId;

/** Utilisateur d'agence connecte : identite, agence, roles et permissions (codes tries). */
public record AgencyUserProfile(
        UUID id,
        String email,
        String fullName,
        AgencyId agencyId,
        String agencyName,
        List<String> roles,
        List<String> permissions) {
}
