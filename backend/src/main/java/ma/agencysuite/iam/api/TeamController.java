package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.iam.application.TeamDirectory;
import ma.agencysuite.iam.application.TeamDirectory.TeamMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annuaire de l'equipe de l'agence courante, ouvert a tout utilisateur de l'agence (aucune
 * permission) : identifiant, nom et statut des utilisateurs actifs et invites. Ni e-mail, ni roles,
 * ni date de connexion ({@code GET /api/v1/users} reste reserve a {@code user.manage}).
 */
@RestController
@RequestMapping("/api/v1/team")
@Tag(name = "team", description = "Annuaire de l'equipe de l'agence (noms des responsables)")
class TeamController {

    private final TeamDirectory directory;

    TeamController(TeamDirectory directory) {
        this.directory = directory;
    }

    @GetMapping
    @Operation(summary = "Membres actifs et invites de l'agence, par nom",
            description = "Sert a afficher le nom du responsable d'un client ou d'une demande. fullName peut etre "
                    + "nul pour un compte cree sans nom.")
    @ApiResponse(responseCode = "200", description = "Membres de l'equipe")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    List<TeamMemberItem> list() {
        return directory.list().stream().map(TeamMemberItem::from).toList();
    }

    /** Membre de l'equipe. */
    record TeamMemberItem(UUID id, String fullName, String status) {

        static TeamMemberItem from(TeamMember member) {
            return new TeamMemberItem(member.id(), member.fullName(), member.status().name());
        }
    }
}
