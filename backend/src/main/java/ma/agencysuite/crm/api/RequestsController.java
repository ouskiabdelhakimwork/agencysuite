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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.crm.api.RequestRequests.CreateRequestRequest;
import ma.agencysuite.crm.api.RequestRequests.StatusChangeRequest;
import ma.agencysuite.crm.api.RequestRequests.UpdateRequestRequest;
import ma.agencysuite.crm.api.RequestResponses.RequestPage;
import ma.agencysuite.crm.api.RequestResponses.RequestResponse;
import ma.agencysuite.crm.application.CustomerRequestRegistry;
import ma.agencysuite.crm.application.RequestSort;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.crm.domain.RequestStatus;
import ma.agencysuite.crm.application.RequestCriteria;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
 * Demandes des clients de l'agence courante (US-701). Lecture : tout utilisateur de l'agence ;
 * ecriture : permission {@code crm.write}. L'agence vient du jeton ; un identifiant d'une autre
 * agence donne 404 (jamais 403). Le responsable sert au filtrage, jamais a restreindre l'acces.
 * Pas de suppression : une demande abandonnee passe en LOST (PROJECT_CANCELLED).
 */
@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "requests", description = "Demandes des clients : canal, source, besoin et statut")
class RequestsController {

    private final CustomerRequestRegistry registry;

    RequestsController(CustomerRequestRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "Rechercher les demandes", description = "q cherche dans la destination (sans tenir "
            + "compte des accents ni de la casse) et dans le nom du client. createdFrom et createdTo sont inclus, "
            + "en journees UTC.")
    @ApiResponse(responseCode = "200", description = "Page de demandes")
    @ApiResponse(responseCode = "400", description = "Parametre invalide (taille de 1 a 100, tri ou statut inconnu)")
    RequestPage list(
            @Parameter(description = "Statut ; repetable (?status=NEW&status=QUOTING)")
            @RequestParam(required = false) Set<RequestStatus> status,
            @Parameter(description = "Responsable (\"Mes demandes\")") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Client") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Canal") @RequestParam(required = false) RequestChannel channel,
            @Parameter(description = "Creees a partir de ce jour (inclus, UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @Parameter(description = "Creees jusqu'a ce jour (inclus, UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(description = "Texte recherche") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Numero de page, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 100)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(CustomerRequestRegistry.MAX_PAGE_SIZE) int size,
            @Parameter(description = "-createdAt (defaut), -statusChangedAt ou departureDate")
            @RequestParam(defaultValue = "-createdAt")
            @Pattern(regexp = "-createdAt|-statusChangedAt|departureDate") String sort) {
        RequestCriteria criteria = new RequestCriteria(status, ownerUserId, customerId, channel, createdFrom,
                createdTo, q);
        return RequestPage.from(registry.search(criteria, sortOf(sort), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'une demande")
    @ApiResponse(responseCode = "200", description = "Fiche de la demande")
    @ApiResponse(responseCode = "404", description = "Demande inconnue dans l'agence")
    RequestResponse get(@PathVariable UUID id) {
        return RequestResponse.from(registry.get(id));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Enregistrer une demande (statut NEW)", description = "Le client doit exister dans "
            + "l'agence et ne pas etre archive. Sans ownerUserId, le responsable est l'utilisateur courant.")
    @ApiResponse(responseCode = "201", description = "Demande creee ; en-tete Location")
    @ApiResponse(responseCode = "400", description = "Requete invalide (dates, personnes, budget), ou responsable "
            + "inconnu dans l'agence")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Client archive")
    ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestRequest request) {
        RequestResponse created = RequestResponse.from(registry.create(request.toData()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Modifier une demande (remplace client, canal, source, responsable et besoin)",
            description = "Ne change jamais le statut (POST /api/v1/requests/{id}/status). Une demande gagnee ou "
                    + "perdue reste modifiable. Un nouveau client doit exister et ne pas etre archive.")
    @ApiResponse(responseCode = "200", description = "Demande modifiee")
    @ApiResponse(responseCode = "400", description = "Requete invalide, ou responsable inconnu dans l'agence")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente")
    @ApiResponse(responseCode = "404", description = "Demande ou client inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Version perimee, ou nouveau client archive")
    RequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequestRequest request) {
        return RequestResponse.from(registry.update(id, request.version(), request.toData()));
    }

    @PostMapping("/{id}/status")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Changer le statut d'une demande", description = """
            Transitions autorisees :

            | Depuis | Vers |
            |---|---|
            | NEW | QUOTING, LOST |
            | QUOTING | QUOTE_SENT, LOST |
            | QUOTE_SENT | QUOTING (autre proposition), WON, LOST |
            | LOST | NEW (reouverture : raison et commentaire effaces) |
            | WON | aucune (etat final) |

            Passer au statut courant : 200 sans changement. LOST exige lostReason (lostComment facultatif, \
            500 caracteres) ; pour un autre statut, raison et commentaire sont ignores. Chaque changement \
            effectif est trace dans le journal d'audit (statuts et raison en code).""")
    @ApiResponse(responseCode = "200", description = "Fiche de la demande")
    @ApiResponse(responseCode = "400", description = "LOST sans raison, ou requete invalide")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente")
    @ApiResponse(responseCode = "404", description = "Demande inconnue dans l'agence")
    @ApiResponse(responseCode = "409", description = "Transition interdite (from, to), ou version perimee")
    RequestResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        return RequestResponse.from(registry.changeStatus(id, request.version(), request.status(), request.reason(),
                request.comment()));
    }

    private static RequestSort sortOf(String sort) {
        return switch (sort) {
            case "-statusChangedAt" -> RequestSort.STATUS_CHANGED_AT_DESC;
            case "departureDate" -> RequestSort.DEPARTURE_DATE;
            default -> RequestSort.CREATED_AT_DESC;
        };
    }
}
