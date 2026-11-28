package ma.agencysuite.supplier.application;

import java.util.List;
import java.util.UUID;

/**
 * Un fournisseur non archive de l'agence ressemble a celui qu'on enregistre : 409
 * possible-duplicate avec les candidats, exactement la meme forme que pour les clients du module
 * crm (meme code ; exception propre au module, supplier ne dependant pas de crm). Le client renvoie
 * la requete avec {@code confirmDuplicate: true} pour enregistrer quand meme.
 */
public class PossibleDuplicateException extends SupplierException {

    /** Critere commun avec un fournisseur existant. Le nom seul n'en est pas un. */
    public enum Criterion {
        ICE,
        EMAIL,
        PHONE
    }

    /** Fournisseur existant semblable : identifiant, nom et criteres communs. */
    public record Candidate(UUID id, String name, List<Criterion> criteria) {
    }

    private final List<Candidate> candidates;

    public PossibleDuplicateException(List<Candidate> candidates) {
        super("Doublon probable : " + candidates.size() + " candidat(s)");
        this.candidates = List.copyOf(candidates);
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }
}
