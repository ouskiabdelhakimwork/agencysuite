package ma.agencysuite.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Textes traduisibles saisis par l'agence : cles fr, ar, en ; nettoyage ; au moins une langue ;
 * longueurs ; forme JSON. L'ecriture et la relecture en JSONB sont verifiees de bout en bout dans
 * {@code ProductApiTest} (colonne product.name).
 */
class LocalizedTextTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void keepsTheSupportedLanguagesInTheOrderFrArEn() {
        LocalizedText text = LocalizedText.of(Map.of("en", "Double room", "fr", "Chambre double", "ar", "غرفة مزدوجة"));

        assertThat(LocalizedText.LANGUAGES).containsExactly("fr", "ar", "en");
        assertThat(text.values().keySet()).containsExactly("fr", "ar", "en");
        assertThat(text.get("ar")).contains("غرفة مزدوجة");
        assertThat(text.isValid(true, 200)).isTrue();
    }

    @Test
    void trimsValuesAndIgnoresAnEmptyLanguage() {
        Map<String, String> raw = new HashMap<>();
        raw.put("fr", "  Chambre double  ");
        raw.put("ar", "   ");
        raw.put("en", null);

        LocalizedText text = LocalizedText.of(raw);

        assertThat(text.values()).containsExactly(Map.entry("fr", "Chambre double"));
        assertThat(text.get("ar")).isEmpty();
    }

    @Test
    void anUnknownLanguageIsKeptSoThatValidationRefusesIt() {
        LocalizedText text = LocalizedText.of(Map.of("fr", "Chambre", "es", "Habitacion"));

        assertThat(text.hasOnlySupportedLanguages()).isFalse();
        assertThat(text.isValid(false, 200)).isFalse();
        assertThatThrownBy(() -> text.requireValid(false, 200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRequiredTextNeedsAtLeastOneLanguage() {
        assertThat(LocalizedText.of(Map.of("fr", "  ")).isEmpty()).isTrue();
        assertThat(LocalizedText.of(null).isValid(true, 200)).isFalse();
        assertThat(LocalizedText.empty().isValid(true, 200)).isFalse();
        assertThat(LocalizedText.empty().isValid(false, 200)).isTrue();
        assertThat(LocalizedText.of(Map.of("ar", "غرفة")).isValid(true, 200)).isTrue();
    }

    @Test
    void eachLanguageIsLimitedToTheMaximumLengthOfTheField() {
        assertThat(LocalizedText.of(Map.of("fr", "a".repeat(200))).isValid(true, 200)).isTrue();
        assertThat(LocalizedText.of(Map.of("fr", "a".repeat(10), "en", "b".repeat(201))).isValid(true, 200)).isFalse();
        // Mesure apres nettoyage des espaces
        assertThat(LocalizedText.of(Map.of("fr", " " + "a".repeat(200) + " ")).isValid(true, 200)).isTrue();
    }

    @Test
    void readsAndWritesAPlainJsonObject() {
        LocalizedText read = JSON.readValue("{\"fr\": \" Demi-pension \", \"ar\": \"نصف إقامة\"}", LocalizedText.class);

        assertThat(read.values()).containsExactly(Map.entry("fr", "Demi-pension"), Map.entry("ar", "نصف إقامة"));
        assertThat(JSON.writeValueAsString(read)).isEqualTo("{\"fr\":\"Demi-pension\",\"ar\":\"نصف إقامة\"}");
        assertThat(JSON.readValue(JSON.writeValueAsString(read), LocalizedText.class)).isEqualTo(read);
    }

    @Test
    void neverPrintsTheTextItself() {
        assertThat(LocalizedText.of(Map.of("fr", "Riad confidentiel")).toString())
                .isEqualTo("LocalizedText[fr]")
                .doesNotContain("Riad");
    }

    @Test
    void theValidatorAppliesTheSameRules() {
        ValidLocalizedText.Validator required = validator(true, 5);
        ValidLocalizedText.Validator optional = validator(false, 5);

        assertThat(required.isValid(null, null)).isFalse();
        assertThat(optional.isValid(null, null)).isTrue();
        assertThat(required.isValid(LocalizedText.of(Map.of("fr", "court")), null)).isTrue();
        assertThat(required.isValid(LocalizedText.of(Map.of("fr", "trop long")), null)).isFalse();
        assertThat(optional.isValid(LocalizedText.of(Map.of("de", "kurz")), null)).isFalse();
    }

    private static ValidLocalizedText.Validator validator(boolean required, int max) {
        ValidLocalizedText.Validator validator = new ValidLocalizedText.Validator();
        validator.initialize(new ValidLocalizedText() {
            @Override
            public boolean required() {
                return required;
            }

            @Override
            public int max() {
                return max;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @SuppressWarnings("unchecked")
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ValidLocalizedText.class;
            }
        });
        return validator;
    }
}
