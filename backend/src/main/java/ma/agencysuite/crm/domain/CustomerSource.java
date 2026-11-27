package ma.agencysuite.crm.domain;

/** Canal par lequel le client est arrive (liste a valider avec l'agence pilote). */
public enum CustomerSource {
    WEBSITE,
    WHATSAPP,
    PHONE,
    EMAIL,
    WALK_IN,
    REFERRAL,
    SOCIAL_MEDIA,
    TRADE_SHOW,
    OTHER
}
