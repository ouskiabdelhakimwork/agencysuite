package ma.agencysuite.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generation d'identifiants UUID version 7 (RFC 9562) : les 48 premiers bits portent
 * l'horodatage en millisecondes, le reste est aleatoire. Les identifiants sont donc
 * tries par milliseconde de creation, ce qui garde les index de base de donnees compacts.
 * Dans une meme milliseconde, leur ordre est aleatoire (pas de compteur monotone) : ne pas
 * s'en servir comme ordre d'ecriture.
 *
 * <p>Genere cote application (et non par la base) pour que l'identifiant existe des la
 * creation de l'objet, avant toute sauvegarde (utile pour les evenements entre modules).
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        return v7(System.currentTimeMillis());
    }

    static UUID v7(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long mostSignificant = (epochMillis << 16) | 0x7000L | randA;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
