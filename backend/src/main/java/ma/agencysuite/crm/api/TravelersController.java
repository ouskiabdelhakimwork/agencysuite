package ma.agencysuite.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import ma.agencysuite.crm.api.TravelerRequests.CreateTravelerRequest;
import ma.agencysuite.crm.api.TravelerRequests.UpdateTravelerRequest;
import ma.agencysuite.crm.api.TravelerResponses.TravelerPage;
import ma.agencysuite.crm.api.TravelerResponses.TravelerResponse;
import ma.agencysuite.crm.application.TravelerRegistry;
import ma.agencysuite.crm.application.TravelerSort;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Voyageurs de l'agence courante et leur rattachement aux clients (etape 5, lot 2). Lecture : tout
 * utilisateur de l'agence ; ecriture : permission {@code crm.write} ; date de naissance : permission
 * {@code identity.read} en plus (regle appliquee par {@link TravelerRegistry}). L'agence vient du
 * jeton ; un identifiant d'une autre agence donne 404 (jamais 403).
 */
@RestController
@RequestMapping("/api/v1/travelers")
@Tag(name = "travelers", description = "Voyageurs et rattachement aux clients. La date et le lieu de "
        + "naissance ne sont lus et modifies qu'avec la permission identity.read.")
class TravelersController {

    private static final String BIRTH_DATE_READ = " Sans la permission identity.read, birthDate vaut null et "
            + "birthDateHidden vaut true si une date existe ; avec elle, la date et birthDateHidden: false. Le lieu de "
            + "naissance suit la meme regle (birthPlace, birthPlaceHidden).";

    private final TravelerRegistry registry;

    TravelersController(TravelerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "Rechercher les voyageurs", description = "q cherche dans le nom et le prenom latins "
            + "(sans tenir compte de la casse ni des accents), les noms arabes, l'e-mail et le telephone (aussi sous "
            + "sa forme E.164). La liste ne contient jamais la date de naissance, qui n'est pas un critere.")
    @ApiResponse(responseCode = "200", description = "Page de voyageurs")
    @ApiResponse(responseCode = "400", description = "Parametre invalide (taille de 1 a 100, tri inconnu)")
    TravelerPage list(
            @Parameter(description = "Texte recherche") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Seulement les voyageurs rattaches a ce client")
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Vrai : seulement les voyageurs archives")
            @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "Numero de page, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 100)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(TravelerRegistry.MAX_PAGE_SIZE) int size,
            @Parameter(description = "name (defaut), -createdAt ou -updatedAt")
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|-createdAt|-updatedAt") String sort) {
        return TravelerPage.from(registry.search(q, customerId, archived, sortOf(sort), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'un voyageur (archive ou non), avec ses clients",
            description = "Fiche complete et clients rattaches." + BIRTH_DATE_READ)
    @ApiResponse(responseCode = "200", description = "Fiche du voyageur")
    @ApiResponse(responseCode = "404", description = "Voyageur inconnu dans l'agence")
    TravelerResponse get(@PathVariable UUID id) {
        return TravelerResponse.from(registry.get(id));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Creer un voyageur", description = "Date et lieu de naissance : avec la permission "
            + "identity.read seulement ; sans elle, une date ou un lieu non vide donne 403 permission-denied. "
            + "customerIds rattache le voyageur a des clients non archives de l'agence. 409 possible-duplicate si "
            + "un voyageur non archive a le meme nom et la meme date de naissance, le meme e-mail ou le meme telephone ; renvoyer avec "
            + "confirmDuplicate: true pour creer quand meme." + BIRTH_DATE_READ)
    @ApiResponse(responseCode = "201", description = "Voyageur cree ; en-tete Location")
    @ApiResponse(responseCode = "400", description = "Requete invalide (date future, nationalite inconnue...)")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente, ou date ou lieu de "
            + "naissance envoye sans identity.read")
    @ApiResponse(responseCode = "404", description = "Client de customerIds inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Doublon probable (candidats dans la reponse), ou client archive")
    ResponseEntity<TravelerResponse> create(@Valid @RequestBody CreateTravelerRequest request) {
        TravelerResponse created = TravelerResponse.from(
                registry.create(request.toData(), request.customerIdSet(), request.confirmDuplicate()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Modifier un voyageur (remplace les champs modifiables, pas les liens)",
            description = "Date et lieu de naissance : avec la permission identity.read, les valeurs "
                    + "envoyees remplacent les anciennes (nulles : effacees) ; sans elle, une date ou un lieu non vide "
                    + "donne 403 permission-denied, et des valeurs absentes ne modifient pas celles enregistrees."
                    + BIRTH_DATE_READ)
    @ApiResponse(responseCode = "200", description = "Voyageur modifie")
    @ApiResponse(responseCode = "400", description = "Requete invalide ou version absente")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente, ou date ou lieu de "
            + "naissance envoye sans identity.read")
    @ApiResponse(responseCode = "404", description = "Voyageur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Version perimee ou doublon probable")
    TravelerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTravelerRequest request) {
        return TravelerResponse.from(
                registry.update(id, request.version(), request.toData(), request.confirmDuplicate()));
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Archiver un voyageur (idempotent ; il reste lisible par son identifiant, ses liens restent)")
    @ApiResponse(responseCode = "200", description = "Voyageur archive")
    @ApiResponse(responseCode = "404", description = "Voyageur inconnu dans l'agence")
    TravelerResponse archive(@PathVariable UUID id) {
        return TravelerResponse.from(registry.archive(id));
    }

    @PostMapping("/{id}/restore")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Restaurer un voyageur archive (idempotent)")
    @ApiResponse(responseCode = "200", description = "Voyageur restaure")
    @ApiResponse(responseCode = "404", description = "Voyageur inconnu dans l'agence")
    TravelerResponse restore(@PathVariable UUID id) {
        return TravelerResponse.from(registry.restore(id));
    }

    @PutMapping("/{id}/customers/{customerId}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Rattacher le voyageur a un client (idempotent)")
    @ApiResponse(responseCode = "200", description = "Fiche du voyageur, avec ses clients")
    @ApiResponse(responseCode = "404", description = "Voyageur ou client inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Client archive")
    TravelerResponse link(@PathVariable UUID id, @PathVariable UUID customerId) {
        return TravelerResponse.from(registry.link(id, customerId));
    }

    @DeleteMapping("/{id}/customers/{customerId}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Detacher le voyageur d'un client (suppression reelle du lien ; idempotent)")
    @ApiResponse(responseCode = "204", description = "Lien supprime, ou absent")
    @ApiResponse(responseCode = "404", description = "Voyageur inconnu dans l'agence")
    ResponseEntity<Void> unlink(@PathVariable UUID id, @PathVariable UUID customerId) {
        registry.unlink(id, customerId);
        return ResponseEntity.noContent().build();
    }

    private static TravelerSort sortOf(String sort) {
        return switch (sort) {
            case "-createdAt" -> TravelerSort.CREATED_AT_DESC;
            case "-updatedAt" -> TravelerSort.UPDATED_AT_DESC;
            default -> TravelerSort.NAME;
        };
    }
}
