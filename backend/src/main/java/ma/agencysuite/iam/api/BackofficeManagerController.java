package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import ma.agencysuite.iam.application.ManagerProvisioning;
import ma.agencysuite.iam.application.ManagerProvisioning.ManagerCommand;
import ma.agencysuite.iam.application.UserInvitations.InvitationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office editeur : rattachement du gerant d'une agence (fin de US-101). Reserve aux jetons du
 * realm "platform" (perimetre plateforme ouvert par la chaine de securite du back-office).
 */
@RestController
@Tag(name = "backoffice", description = "Back-office editeur : agences clientes et modules souscrits")
class BackofficeManagerController {

    private final ManagerProvisioning provisioning;

    BackofficeManagerController(ManagerProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @PostMapping("/api/v1/backoffice/agencies/{agencyId}/manager")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rattacher le gerant d'une agence : Organization creee si besoin, invitation MANAGER",
            description = "L'Organization du fournisseur d'identite (alias = UUID de l'agence) est creee au premier "
                    + "appel puis reutilisee. Le gerant recoit un e-mail d'invitation ; invitationSent est faux s'il "
                    + "n'a pas pu partir.")
    @ApiResponse(responseCode = "201", description = "Gerant invite")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "404", description = "Agence inconnue")
    @ApiResponse(responseCode = "409", description = "Agence close, e-mail deja enregistre ou palier atteint")
    @ApiResponse(responseCode = "503", description = "Fournisseur d'identite indisponible")
    UsersController.InvitationResponse provisionManager(@PathVariable UUID agencyId,
            @Valid @RequestBody ManagerRequest request) {
        InvitationResult result = provisioning.provisionManager(agencyId,
                new ManagerCommand(request.email(), request.firstName(), request.lastName()));
        return UsersController.InvitationResponse.from(result);
    }

    record ManagerRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName) {
    }
}
