package ma.agencysuite.supplier.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La periode du tarif chevauche celle d'un tarif non archive de la meme prestation et de la meme
 * base (dates incluses) : 409 rate-overlap, avec le tarif en conflit.
 */
public class RateOverlapException extends SupplierException {

    private final UUID conflictingRateId;
    private final LocalDate validFrom;
    private final LocalDate validTo;

    public RateOverlapException(UUID conflictingRateId, LocalDate validFrom, LocalDate validTo) {
        super("Chevauchement avec le tarif " + conflictingRateId);
        this.conflictingRateId = Objects.requireNonNull(conflictingRateId, "conflictingRateId");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validTo = Objects.requireNonNull(validTo, "validTo");
    }

    public UUID getConflictingRateId() {
        return conflictingRateId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
