package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.iam.application.AgencyUserProfile;
import ma.agencysuite.iam.application.LoginEvents;
import ma.agencysuite.iam.application.UserProfiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Utilisateur d'agence connecte. L'utilisateur et l'agence viennent du jeton (perimetre ouvert
 * par la chaine de securite), jamais d'un parametre envoye par le client.
 */
@RestController
@Tag(name = "me", description = "Informations sur l'utilisateur connecte et son agence")
class MeController {

    private final UserProfiles profiles;
    private final LoginEvents logins;

    MeController(UserProfiles profiles, LoginEvents logins) {
        this.profiles = profiles;
        this.logins = logins;
    }

    @GetMapping("/api/v1/me")
    @Operation(summary = "Utilisateur connecte : identite, agence, roles et permissions")
    @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur connecte")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Utilisateur inconnu ou desactive, ou agence indisponible")
    MeResponse me() {
        AgencyUserProfile profile = profiles.currentAgencyUser();
        return new MeResponse(profile.id(), profile.email(), profile.fullName(),
                new MeResponse.Agency(profile.agencyId().value(), profile.agencyName()),
                profile.roles(), profile.permissions());
    }

    @PostMapping("/api/v1/me/login-events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Signale la connexion de l'utilisateur (apres le retour du fournisseur d'identite)",
            description = "Met a jour la date de derniere connexion et trace USER_LOGGED_IN dans le journal "
                    + "d'audit. Une seconde connexion a moins de 60 secondes est ignoree (204 sans entree).")
    @ApiResponse(responseCode = "204", description = "Connexion prise en compte (ou ignoree car trop rapprochee)")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Utilisateur inconnu ou desactive, ou agence indisponible")
    void loginEvent() {
        logins.recordLogin();
    }

    /** Reponse de GET /api/v1/me. Roles et permissions tries par code. */
    record MeResponse(UUID id, String email, String fullName, Agency agency, List<String> roles,
            List<String> permissions) {

        record Agency(UUID id, String name) {
        }
    }
}
