package ma.agencysuite.crm.domain;

import java.util.Set;

/**
 * Statut d'une demande (01-cahier-des-charges.md, section 5.2) et transitions autorisees :
 *
 * <pre>
 * NEW        -&gt; QUOTING, LOST
 * QUOTING    -&gt; QUOTE_SENT, LOST
 * QUOTE_SENT -&gt; QUOTING (autre proposition demandee), WON, LOST
 * LOST       -&gt; NEW (reouverture)
 * WON        -&gt; aucune (etat final ; conversion en dossier a l'etape 6)
 * </pre>
 */
public enum RequestStatus {
    NEW,
    QUOTING,
    QUOTE_SENT,
    WON,
    LOST;

    /** Vrai si la demande peut passer de ce statut a {@code target} (jamais vrai pour le meme statut). */
    public boolean canMoveTo(RequestStatus target) {
        return targets().contains(target);
    }

    private Set<RequestStatus> targets() {
        return switch (this) {
            case NEW -> Set.of(QUOTING, LOST);
            case QUOTING -> Set.of(QUOTE_SENT, LOST);
            // TODO(etape-6): QUOTE_SENT sera mis automatiquement a l'envoi d'un devis ; manuel a ce lot.
            case QUOTE_SENT -> Set.of(QUOTING, WON, LOST);
            case LOST -> Set.of(NEW);
            case WON -> Set.of();
        };
    }
}
