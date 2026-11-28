package ma.agencysuite.shared.i18n;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Texte saisi par l'agence dans une ou plusieurs langues ({@code fr}, {@code ar}, {@code en}),
 * stocke en JSONB {@code {"fr": "...", "ar": "...", "en": "..."}} et renvoye tel quel par l'API.
 *
 * <p>Nettoyage a la construction : valeurs sans espaces autour ; une valeur vide ou nulle vaut une
 * langue absente. Les cles inconnues sont gardees pour que la validation ({@link ValidLocalizedText})
 * les refuse au lieu de les ignorer en silence ; une entite n'enregistre qu'un texte valide
 * ({@link #requireValid(boolean, int)}).
 *
 * <p>Le choix de la langue affichee (et le repli sur une autre langue) est fait par le frontend,
 * jamais par le serveur. Immuable.
 */
@Schema(name = "LocalizedText", type = "object", example = "{\"fr\": \"Chambre double\", \"ar\": \"غرفة مزدوجة\"}",
        description = "Texte par langue : cles fr, ar, en uniquement ; une langue vide est absente.")
public final class LocalizedText {

    /** Langues acceptees, dans l'ordre de stockage et de sortie. */
    public static final List<String> LANGUAGES = Arrays.stream(SupportedLocale.values())
            .map(SupportedLocale::code).toList();

    private static final LocalizedText EMPTY = new LocalizedText(Map.of());

    private final Map<String, String> values;

    private LocalizedText(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Texte a partir d'une carte langue &rarr; valeur (corps JSON, colonne JSONB) ; {@code null}
     * donne un texte vide. Langues connues d'abord, dans l'ordre fr, ar, en.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LocalizedText of(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            put(cleaned, language, raw.get(language));
        }
        raw.forEach((language, value) -> {
            if (language != null && !LANGUAGES.contains(language)) {
                put(cleaned, language, value);
            }
        });
        return cleaned.isEmpty() ? EMPTY : new LocalizedText(Collections.unmodifiableMap(cleaned));
    }

    /** Texte vide (aucune langue). */
    public static LocalizedText empty() {
        return EMPTY;
    }

    private static void put(Map<String, String> target, String language, String value) {
        if (value != null) {
            String trimmed = value.strip();
            if (!trimmed.isEmpty()) {
                target.put(language, trimmed);
            }
        }
    }

    /** Carte langue &rarr; valeur, non modifiable (forme JSON et JSONB). */
    @JsonValue
    public Map<String, String> values() {
        return values;
    }

    /** Valeur dans cette langue, vide si elle est absente. */
    public Optional<String> get(String language) {
        return Optional.ofNullable(values.get(language));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Vrai si toutes les cles sont des langues acceptees ({@link #LANGUAGES}). */
    public boolean hasOnlySupportedLanguages() {
        return LANGUAGES.containsAll(values.keySet());
    }

    /** Longueur de la plus longue valeur (0 si vide). */
    public int longestValue() {
        return values.values().stream().mapToInt(String::length).max().orElse(0);
    }

    /** Vrai si le texte respecte les regles : langues acceptees, au moins une si obligatoire, longueur. */
    public boolean isValid(boolean required, int maxLength) {
        return hasOnlySupportedLanguages() && !(required && isEmpty()) && longestValue() <= maxLength;
    }

    /**
     * Ce texte s'il respecte les regles ; sinon {@link IllegalArgumentException} (erreur de
     * programmation : l'API doit l'avoir refuse avec {@link ValidLocalizedText}).
     */
    public LocalizedText requireValid(boolean required, int maxLength) {
        if (!isValid(required, maxLength)) {
            throw new IllegalArgumentException("Texte traduisible invalide");
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LocalizedText text && values.equals(text.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** Langues presentes seulement : jamais le texte (il peut venir d'une saisie libre). */
    @Override
    public String toString() {
        return "LocalizedText" + values.keySet();
    }
}
