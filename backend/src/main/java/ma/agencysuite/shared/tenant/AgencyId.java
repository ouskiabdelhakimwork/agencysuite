package ma.agencysuite.shared.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'une agence (tenant). Present sur chaque entite metier (ADR-06).
 *
 * <p>Regle : il n'est jamais lu depuis une donnee envoyee par le client ; il vient
 * du jeton de l'utilisateur connecte ou d'un lien securise (06-conventions-code.md).
 */
public record AgencyId(UUID value) {

    public AgencyId {
        Objects.requireNonNull(value, "value");
    }

    public static AgencyId of(String value) {
        return new AgencyId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
