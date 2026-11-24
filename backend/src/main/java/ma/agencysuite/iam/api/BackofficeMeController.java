package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import ma.agencysuite.iam.application.PlatformUserProfile;
import ma.agencysuite.iam.application.UserProfiles;
import ma.agencysuite.iam.application.UserRequiredException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Membre de l'editeur connecte au back-office (jeton du realm "platform"). */
@RestController
@Tag(name = "backoffice-me", description = "Membre de l'editeur connecte au back-office")
class BackofficeMeController {

    private final UserProfiles profiles;

    BackofficeMeController(UserProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/backoffice/me")
    @Operation(summary = "Membre de l'editeur connecte : identite et role")
    @ApiResponse(responseCode = "200", description = "Profil du membre de l'editeur")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Jeton d'un autre realm, membre inconnu ou desactive")
    BackofficeMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        PlatformUserProfile profile = profiles.platformUser(jwt.getSubject())
                .orElseThrow(UserRequiredException::new);
        return new BackofficeMeResponse(profile.id(), profile.email(), profile.fullName(), profile.role());
    }

    /** Reponse de GET /api/v1/backoffice/me. */
    record BackofficeMeResponse(UUID id, String email, String fullName, String role) {
    }
}
