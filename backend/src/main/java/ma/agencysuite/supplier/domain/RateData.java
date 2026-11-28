package ma.agencysuite.supplier.domain;

import java.time.LocalDate;
import ma.agencysuite.shared.money.Money;

/**
 * Champs modifiables d'un tarif. {@code purchase} est deja un montant exact, strictement positif,
 * dans la devise retenue par le cas d'usage (celle envoyee, sinon celle du fournisseur).
 */
public record RateData(RateBasis basis, LocalDate validFrom, LocalDate validTo, Money purchase, String label) {
}
