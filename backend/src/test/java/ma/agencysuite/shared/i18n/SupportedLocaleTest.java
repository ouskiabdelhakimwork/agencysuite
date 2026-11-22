package ma.agencysuite.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SupportedLocaleTest {

    @Test
    void mapsRegionalVariantsToSupportedLanguage() {
        assertThat(SupportedLocale.from(Locale.forLanguageTag("ar-MA"))).isEqualTo(SupportedLocale.AR);
        assertThat(SupportedLocale.from(Locale.forLanguageTag("fr-MA"))).isEqualTo(SupportedLocale.FR);
        assertThat(SupportedLocale.from(Locale.forLanguageTag("en-GB"))).isEqualTo(SupportedLocale.EN);
    }

    @Test
    void arabicIsRightToLeft() {
        assertThat(SupportedLocale.AR.direction()).isEqualTo(SupportedLocale.TextDirection.RTL);
        assertThat(SupportedLocale.AR.direction().code()).isEqualTo("rtl");
        assertThat(SupportedLocale.FR.direction().code()).isEqualTo("ltr");
    }

    @Test
    void fallsBackToFrench() {
        assertThat(SupportedLocale.from(Locale.GERMAN)).isEqualTo(SupportedLocale.FR);
        assertThat(SupportedLocale.from(null)).isEqualTo(SupportedLocale.FR);
    }
}
