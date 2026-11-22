package ma.agencysuite.shared.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Langues prises en charge des la V1 (ADR-16) et sens d'ecriture associe.
 * Le francais est la langue par defaut.
 */
public enum SupportedLocale {

    FR(Locale.FRENCH, TextDirection.LTR),
    AR(Locale.forLanguageTag("ar"), TextDirection.RTL),
    EN(Locale.ENGLISH, TextDirection.LTR);

    public static final SupportedLocale DEFAULT = FR;

    private final Locale locale;
    private final TextDirection direction;

    SupportedLocale(Locale locale, TextDirection direction) {
        this.locale = locale;
        this.direction = direction;
    }

    public Locale locale() {
        return locale;
    }

    public String code() {
        return locale.getLanguage();
    }

    public TextDirection direction() {
        return direction;
    }

    /** Langue prise en charge correspondant a la locale demandee (ex. ar-MA -> AR), sinon la langue par defaut. */
    public static SupportedLocale from(Locale requested) {
        if (requested == null) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(s -> s.locale.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(DEFAULT);
    }

    public static List<Locale> locales() {
        return Arrays.stream(values()).map(SupportedLocale::locale).toList();
    }

    public enum TextDirection {
        LTR, RTL;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
