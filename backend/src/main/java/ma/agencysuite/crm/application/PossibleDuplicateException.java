package ma.agencysuite.crm.application;

import java.util.List;
import java.util.UUID;

/**
 * Une fiche non archivee de l'agence ressemble a celle qu'on enregistre : 409 possible-duplicate
 * avec les candidats. Le client renvoie la requete avec {@code confirmDuplicate: true} pour
 * enregistrer quand meme. Meme code pour les clients ({@link Candidate}) et les voyageurs
 * ({@link TravelerCandidate}) : seule la forme des candidats change.
 */
public class PossibleDuplicateException extends CrmException {

    /** Critere commun avec une fiche existante. */
    public enum Criterion {
        EMAIL,
        PHONE,
        ICE,
        /** Voyageurs : meme cle de nom et meme date de naissance (le nom seul n'est pas un critere). */
        NAME_AND_BIRTH_DATE
    }

    /** Client existant semblable : identifiant, nom et criteres communs. */
    public record Candidate(UUID id, String name, List<Criterion> criteria) {
    }

    /** Voyageur existant semblable : identifiant, nom, prenom et criteres communs (jamais la date). */
    public record TravelerCandidate(UUID id, String lastName, String firstName, List<Criterion> criteria) {
    }

    private final List<?> candidates;

    public PossibleDuplicateException(List<?> candidates) {
        super("Doublon probable : " + candidates.size() + " candidat(s)");
        this.candidates = List.copyOf(candidates);
    }

    /** {@link Candidate} ou {@link TravelerCandidate}, serialises tels quels dans la reponse. */
    public List<?> getCandidates() {
        return candidates;
    }
}
