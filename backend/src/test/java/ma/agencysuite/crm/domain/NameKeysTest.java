package ma.agencysuite.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cle de nom des voyageurs : minuscules, sans accents, espaces reduits. */
class NameKeysTest {

    @Test
    void removesAccentsAndLowersCase() {
        assertThat(NameKeys.of("Él Fassi", "Zineb")).isEqualTo("el fassi zineb");
        assertThat(NameKeys.of("BENJELLOUN", "Hélène")).isEqualTo("benjelloun helene");
        assertThat(NameKeys.of("Çelik", "Noël")).isEqualTo("celik noel");
    }

    @Test
    void reducesSpaces() {
        assertThat(NameKeys.of("  Él Fassi  ", " Zineb ")).isEqualTo("el fassi zineb");
        assertThat(NameKeys.normalize("Él\tFassi    Zineb")).isEqualTo("el fassi zineb");
    }

    @Test
    void keepsApostrophesAndHyphens() {
        assertThat(NameKeys.of("Ait M'Barek", "Anne-Marie")).isEqualTo("ait m'barek anne-marie");
    }

    @Test
    void typographicApostrophesBecomeTheStraightOne() {
        String expected = NameKeys.of("Ait M'Barek", "Salma");
        assertThat(expected).isEqualTo("ait m'barek salma");
        assertThat(NameKeys.of("Aït M’Barek", "Salma")).isEqualTo(expected);
        assertThat(NameKeys.of("Aït M‘Barek", "Salma")).isEqualTo(expected);
        assertThat(NameKeys.of("Aït MʼBarek", "Salma")).isEqualTo(expected);
        assertThat(NameKeys.normalize("M’BAREK")).isEqualTo("m'barek");
    }

    @Test
    void normalizesSearchText() {
        assertThat(NameKeys.normalize("  ÉL   FASSI ")).isEqualTo("el fassi");
        assertThat(NameKeys.normalize(null)).isEmpty();
        assertThat(NameKeys.normalize("   ")).isEmpty();
    }

    @Test
    void longestNamesFitTheColumn() {
        assertThat(NameKeys.of("N".repeat(100), "P".repeat(100))).hasSizeLessThanOrEqualTo(210);
    }
}
