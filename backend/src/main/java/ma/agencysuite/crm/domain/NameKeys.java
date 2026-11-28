package ma.agencysuite.crm.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cle de nom des voyageurs ({@code name_key}) : texte en minuscules, sans accents (forme NFD puis
 * retrait des marques diacritiques), espaces reduits. {@code "Él Fassi  Zineb"} &rarr;
 * {@code "el fassi zineb"}. Sert a la recherche et aux doublons, jamais renvoyee par l'API.
 *
 * <p>Les apostrophes typographiques deviennent l'apostrophe droite, avant tout le reste
 * ({@code Aït M’Barek} &rarr; {@code ait m'barek}) ; apostrophes et traits d'union sont gardes. Pas de translitteration latin / arabe (etape 13b, ADR-22).
 */
public final class NameKeys {

    /** Apostrophes typographiques (’ U+2019, ‘ U+2018, ʼ U+02BC), ramenees a l'apostrophe droite. */
    private static final Pattern TYPOGRAPHIC_APOSTROPHES = Pattern.compile("[’‘ʼ]");
    private static final Pattern MARKS =Pattern.compile("\\p{M}+");
    /** Blancs ASCII et Unicode (dont l'espace insecable, frequent dans les copier-coller). */
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    private NameKeys() {
    }

    /** Cle de "nom prenom". */
    public static String of(String lastName, String firstName) {
        return normalize(lastName + " " + firstName);
    }

    /** Meme normalisation pour un texte quelconque (texte recherche) ; chaine vide si absent. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String apostrophes = TYPOGRAPHIC_APOSTROPHES.matcher(text).replaceAll("'");
        String decomposed = Normalizer.normalize(apostrophes, Normalizer.Form.NFD);
        String withoutMarks = MARKS.matcher(decomposed).replaceAll("");
        return SPACES.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
