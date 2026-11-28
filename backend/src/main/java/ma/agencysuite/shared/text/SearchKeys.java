package ma.agencysuite.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cles de recherche ({@code name_key}, {@code search_key}) : texte en minuscules, sans accents
 * (forme NFD puis retrait des marques diacritiques), espaces reduits. {@code "Él Fassi  Zineb"}
 * &rarr; {@code "el fassi zineb"}. Servent a la recherche et aux doublons, jamais renvoyees par l'API.
 *
 * <p>Les apostrophes typographiques deviennent l'apostrophe droite, avant tout le reste
 * ({@code Aït M’Barek} &rarr; {@code ait m'barek}) ; apostrophes et traits d'union sont gardes par
 * {@link #normalize(String)}. Pas de translitteration latin / arabe (etape 13b, ADR-22).
 *
 * <p>Deplace du module crm ({@code NameKeys}, etape 5 lot 2) vers le noyau partage au lot 3, sans
 * changement de comportement, pour servir aussi au module supplier.
 */
public final class SearchKeys {

    /** Apostrophes typographiques (’ U+2019, ‘ U+2018, ʼ U+02BC), ramenees a l'apostrophe droite. */
    private static final Pattern TYPOGRAPHIC_APOSTROPHES = Pattern.compile("[’‘ʼ]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    /** Blancs ASCII et Unicode (dont l'espace insecable, frequent dans les copier-coller). */
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
    /** Trait d'union et tirets Unicode (‐ U+2010, ‑ U+2011, – U+2013, — U+2014). */
    private static final Pattern DASHES = Pattern.compile("[-‐‑–—]+");
    private static final char LIKE_ESCAPE = '\\';

    private SearchKeys() {
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

    /**
     * {@link #normalize(String)}, puis traits d'union et tirets remplaces par une espace : cle de
     * recherche par mots, ou {@code "demi pension"} trouve {@code "Demi-pension"} (noms de
     * prestations, etape 5 lot 3). Le meme calcul doit etre applique au texte recherche.
     */
    public static String words(String text) {
        String normalized = normalize(text);
        return SPACES.matcher(DASHES.matcher(normalized).replaceAll(" ")).replaceAll(" ").strip();
    }

    /** Caractere d'echappement a passer a {@code LIKE ... ESCAPE} avec {@link #escapeLike(String)}. */
    public static char likeEscape() {
        return LIKE_ESCAPE;
    }

    /** Valeur prise a la lettre dans un motif LIKE : {@code \}, {@code %} et {@code _} echappes. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Motif LIKE {@code %valeur%}, valeur echappee. */
    public static String containsPattern(String value) {
        return "%" + escapeLike(value) + "%";
    }
}
