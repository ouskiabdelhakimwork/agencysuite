package ma.agencysuite.crm.application;

import java.util.List;
import java.util.UUID;

/**
 * Un client non archive de l'agence a deja le meme e-mail, telephone ou ICE : 409
 * possible-duplicate avec les candidats. Le client renvoie la requete avec
 * {@code confirmDuplicate: true} pour enregistrer quand meme.
 */
public class PossibleDuplicateException extends CrmException {

    /** Critere commun avec un client existant. */
    public enum Criterion {
        EMAIL,
        PHONE,
        ICE
    }

    /** Client existant semblable : identifiant, nom et criteres communs. */
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
