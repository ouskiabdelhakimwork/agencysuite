package ma.agencysuite.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cles de recherche (ancien NameKeysTest du module crm, deplace sans changement) : minuscules, sans accents, espaces reduits. */
class SearchKeysTest {

    @Test
    void removesAccentsAndLowersCase() {
        assertThat(SearchKeys.of("Él Fassi", "Zineb")).isEqualTo("el fassi zineb");
        assertThat(SearchKeys.of("BENJELLOUN", "Hélène")).isEqualTo("benjelloun helene");
        assertThat(SearchKeys.of("Çelik", "Noël")).isEqualTo("celik noel");
    }

    @Test
    void reducesSpaces() {
        assertThat(SearchKeys.of("  Él Fassi  ", " Zineb ")).isEqualTo("el fassi zineb");
        assertThat(SearchKeys.normalize("Él\tFassi    Zineb")).isEqualTo("el fassi zineb");
    }

    @Test
    void keepsApostrophesAndHyphens() {
        assertThat(SearchKeys.of("Ait M'Barek", "Anne-Marie")).isEqualTo("ait m'barek anne-marie");
    }

    @Test
    void typographicApostrophesBecomeTheStraightOne() {
        String expected = SearchKeys.of("Ait M'Barek", "Salma");
        assertThat(expected).isEqualTo("ait m'barek salma");
        assertThat(SearchKeys.of("Aït M’Barek", "Salma")).isEqualTo(expected);
        assertThat(SearchKeys.of("Aït M‘Barek", "Salma")).isEqualTo(expected);
        assertThat(SearchKeys.of("Aït MʼBarek", "Salma")).isEqualTo(expected);
        assertThat(SearchKeys.normalize("M’BAREK")).isEqualTo("m'barek");
    }

    @Test
    void normalizesSearchText() {
        assertThat(SearchKeys.normalize("  ÉL   FASSI ")).isEqualTo("el fassi");
        assertThat(SearchKeys.normalize(null)).isEmpty();
        assertThat(SearchKeys.normalize("   ")).isEmpty();
    }

    @Test
    void longestNamesFitTheColumn() {
        assertThat(SearchKeys.of("N".repeat(100), "P".repeat(100))).hasSizeLessThanOrEqualTo(210);
    }

    // Ajouts du lot 3 (etape 5) : les tests ci-dessus sont ceux de NameKeysTest, inchanges.

    @Test
    void wordsAlsoTurnsHyphensIntoSpaces() {
        assertThat(SearchKeys.words("Chambre double Demi-Pension")).isEqualTo("chambre double demi pension");
        assertThat(SearchKeys.words("Riad – Médina")).isEqualTo("riad medina");
        assertThat(SearchKeys.words("  غرفة   مزدوجة ")).isEqualTo("غرفة مزدوجة");
        assertThat(SearchKeys.words(null)).isEmpty();
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(SearchKeys.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
        assertThat(SearchKeys.containsPattern("50%")).isEqualTo("%50\\%%");
    }
}
