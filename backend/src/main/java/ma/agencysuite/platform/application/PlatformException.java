package ma.agencysuite.platform.application;

/**
 * Erreur metier du module Plateforme, traduite en reponse problem+json.
 * Le message (en francais, pour les journaux) n'est jamais renvoye tel quel au client :
 * celui-ci recoit le texte traduit des cles {@code problem.<code>.title / .detail}.
 */
public abstract class PlatformException extends RuntimeException {

    protected PlatformException(String message) {
        super(message);
    }
}
