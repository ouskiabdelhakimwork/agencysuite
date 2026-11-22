package ma.agencysuite.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import ma.agencysuite.shared.i18n.SupportedLocale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informations publiques sur la plateforme : version, langues prises en charge,
 * langue et sens d'ecriture retenus pour la requete. Sert de verification de
 * bout en bout du socle (i18n, OpenAPI, format d'erreur).
 */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "platform", description = "Informations sur la plateforme")
class PlatformInfoController {

    private final MessageSource messages;
    private final ObjectProvider<BuildProperties> buildProperties;

    PlatformInfoController(MessageSource messages, ObjectProvider<BuildProperties> buildProperties) {
        this.messages = messages;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/info")
    @Operation(summary = "Version, langues prises en charge et langue retenue pour la requete")
    PlatformInfo info(Locale requestLocale) {
        SupportedLocale locale = SupportedLocale.from(requestLocale);
        BuildProperties build = buildProperties.getIfAvailable();
        return new PlatformInfo(
                "AgencySuite",
                build != null ? build.getVersion() : "dev",
                java.util.Arrays.stream(SupportedLocale.values()).map(SupportedLocale::code).toList(),
                locale.code(),
                locale.direction().code(),
                messages.getMessage("platform.welcome", null, locale.locale()));
    }

    /** Reponse de GET /api/v1/platform/info. */
    public record PlatformInfo(
            String name,
            String version,
            List<String> supportedLocales,
            String locale,
            String direction,
            String welcome) {
    }
}
