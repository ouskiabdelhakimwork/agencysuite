package ma.agencysuite.crm.domain;

/** Champs modifiables d'un contact, tels que saisis (deja valides par l'API). */
public record ContactData(String fullName, String jobTitle, String email, String phone, boolean primary) {
}
