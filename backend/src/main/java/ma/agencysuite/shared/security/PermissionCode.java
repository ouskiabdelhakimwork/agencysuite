package ma.agencysuite.shared.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permissions de l'application (droits geres dans l'application, ADR-08).
 *
 * <p>Alignees exactement sur la table {@code permission} (migration V4, verifie par
 * ReferenceDataTest) : chaque etape ajoute les siennes ici et par migration, avec leur
 * repartition dans les roles systeme.
 */
public enum PermissionCode {
    /** Gestion des utilisateurs et de leurs roles. */
    USER_MANAGE("user.manage"),
    /** Consultation du journal d'audit. */
    AUDIT_READ("audit.read"),
    /** Traitement des demandes de droits des personnes. */
    PRIVACY_MANAGE("privacy.manage"),
    /** Consultation des pieces d'identite. */
    IDENTITY_READ("identity.read"),
    /** Billetterie (module TICKETING). */
    TICKETING_WRITE("ticketing.write"),
    /** Emission d'une facture. */
    BILLING_ISSUE("billing.issue"),
    /** Creation et modification des clients et de leurs contacts (etape 5). La lecture est ouverte a tous. */
    CRM_WRITE("crm.write"),
    /** Creation et modification des fournisseurs (etape 5, lot 3). */
    SUPPLIER_WRITE("supplier.write");

    private static final Map<String, PermissionCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PermissionCode::code, Function.identity()));

    private final String code;

    PermissionCode(String code) {
        this.code = code;
    }

    /** Code stocke en base et renvoye par l'API (ex. {@code audit.read}). */
    public String code() {
        return code;
    }

    /** Permission portant ce code, vide si le code est inconnu. */
    public static Optional<PermissionCode> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
