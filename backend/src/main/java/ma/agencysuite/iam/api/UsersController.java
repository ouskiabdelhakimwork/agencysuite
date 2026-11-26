package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.iam.application.UserAdministration;
import ma.agencysuite.iam.application.UserAdministration.UserSummary;
import ma.agencysuite.iam.application.UserInvitations;
import ma.agencysuite.iam.application.UserInvitations.InvitationResult;
import ma.agencysuite.iam.application.UserInvitations.InviteUserCommand;
import ma.agencysuite.iam.domain.SystemRole;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Utilisateurs de l'agence courante (US-201), reserve a la permission {@code user.manage}. L'agence
 * vient du jeton ; un identifiant d'une autre agence donne 404 (jamais 403).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiresPermission(PermissionCode.USER_MANAGE)
@Tag(name = "users", description = "Utilisateurs de l'agence : invitation, roles, desactivation")
class UsersController {

    private final UserInvitations invitations;
    private final UserAdministration administration;

    UsersController(UserInvitations invitations, UserAdministration administration) {
        this.invitations = invitations;
        this.administration = administration;
    }

    @GetMapping
    @Operation(summary = "Utilisateurs de l'agence, par e-mail")
    @ApiResponse(responseCode = "200", description = "Liste des utilisateurs")
    @ApiResponse(responseCode = "403", description = "Permission user.manage absente")
    List<UserItem> list() {
        return administration.list().stream().map(UserItem::from).toList();
    }

    @PostMapping
    @Operation(summary = "Inviter un utilisateur (e-mail d'invitation envoye par le fournisseur d'identite)",
            description = "Aucun mot de passe n'est genere : l'invite verifie son adresse et choisit lui-meme "
                    + "son mot de passe. invitationSent est faux si l'e-mail n'a pas pu partir (a renvoyer).")
    @ApiResponse(responseCode = "201", description = "Utilisateur invite ; en-tete Location")
    @ApiResponse(responseCode = "400", description = "Requete invalide (e-mail, nom, roles)")
    @ApiResponse(responseCode = "409", description = "E-mail deja enregistre, ou palier atteint")
    @ApiResponse(responseCode = "503", description = "Fournisseur d'identite indisponible")
    ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InviteUserRequest request) {
        InvitationResult result = invitations.invite(
                new InviteUserCommand(request.email(), request.firstName(), request.lastName(), request.roles()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(result.userId())
                .toUri();
        return ResponseEntity.created(location).body(InvitationResponse.from(result));
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "Remplacer les roles de l'utilisateur")
    @ApiResponse(responseCode = "200", description = "Roles enregistres")
    @ApiResponse(responseCode = "400", description = "Aucun role, ou role inconnu")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Retirerait le dernier gerant de l'agence")
    UserItem changeRoles(@PathVariable UUID userId, @Valid @RequestBody RolesRequest request) {
        return UserItem.from(administration.changeRoles(userId, request.roles()));
    }

    @PostMapping("/{userId}/invitation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Renvoyer l'e-mail d'invitation d'un utilisateur invite")
    @ApiResponse(responseCode = "202", description = "Invitation renvoyee")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Utilisateur deja actif ou desactive")
    @ApiResponse(responseCode = "503", description = "Fournisseur d'identite indisponible")
    void resendInvitation(@PathVariable UUID userId) {
        invitations.resendInvitation(userId);
    }

    @PostMapping("/{userId}/disable")
    @Operation(summary = "Desactiver l'utilisateur (acces coupe immediatement, sessions fermees)",
            description = "identityProviderSynced est faux si le fournisseur d'identite n'a pas pu etre mis a "
                    + "jour : l'utilisateur reste bloque par l'application ; un nouvel appel resynchronise.")
    @ApiResponse(responseCode = "200", description = "Utilisateur desactive")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Soi-meme, ou dernier gerant de l'agence")
    StatusChangeResponse disable(@PathVariable UUID userId) {
        return new StatusChangeResponse(administration.disable(userId));
    }

    @PostMapping("/{userId}/enable")
    @Operation(summary = "Reactiver l'utilisateur (actif s'il s'est deja connecte, sinon invite)")
    @ApiResponse(responseCode = "200", description = "Utilisateur reactive")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Palier atteint")
    StatusChangeResponse enable(@PathVariable UUID userId) {
        return new StatusChangeResponse(administration.enable(userId));
    }

    /** Invitation d'un utilisateur. Roles parmi les codes connus (400 sinon). */
    record InviteUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotEmpty Set<@NotNull SystemRole> roles) {
    }

    record RolesRequest(@NotEmpty Set<@NotNull SystemRole> roles) {
    }

    record InvitationResponse(UUID id, String status, boolean invitationSent) {

        static InvitationResponse from(InvitationResult result) {
            return new InvitationResponse(result.userId(), result.status().name(), result.invitationSent());
        }
    }

    record StatusChangeResponse(boolean identityProviderSynced) {
    }

    /** Utilisateur de la liste. Roles tries par code. */
    record UserItem(UUID id, String email, String fullName, String status, List<String> roles,
            Instant lastLoginAt) {

        static UserItem from(UserSummary user) {
            return new UserItem(user.id(), user.email(), user.fullName(), user.status().name(), user.roles(),
                    user.lastLoginAt());
        }
    }
}
