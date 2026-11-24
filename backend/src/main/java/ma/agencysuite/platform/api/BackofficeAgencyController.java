package ma.agencysuite.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;
import ma.agencysuite.platform.application.AgencyAdministration;
import ma.agencysuite.platform.application.ModuleActivation;
import ma.agencysuite.platform.application.ModuleNotFoundException;
import ma.agencysuite.shared.module.ModuleCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Back-office editeur : creation des agences clientes (US-101) et activation des modules (US-102).
 *
 * <p>Reserve aux jetons du realm Keycloak "platform" ; le perimetre plateforme est ouvert par la
 * chaine de securite du back-office (module iam).
 */
@RestController
@RequestMapping("/api/v1/backoffice/agencies")
@Tag(name = "backoffice", description = "Back-office editeur : agences clientes et modules souscrits")
class BackofficeAgencyController {

    private final AgencyAdministration administration;
    private final ModuleActivation moduleActivation;

    BackofficeAgencyController(AgencyAdministration administration, ModuleActivation moduleActivation) {
        this.administration = administration;
        this.moduleActivation = moduleActivation;
    }

    @PostMapping
    @Operation(summary = "Creer une agence cliente avec son palier et ses modules (US-101)")
    @ApiResponse(responseCode = "201", description = "Agence creee ; en-tete Location vers la ressource")
    @ApiResponse(responseCode = "400", description = "Requete invalide (champ manquant, sous-domaine invalide ou reserve)")
    @ApiResponse(responseCode = "404", description = "Palier ou module inconnu")
    @ApiResponse(responseCode = "409", description = "Sous-domaine deja pris")
    ResponseEntity<AgencyResponse> create(@Valid @RequestBody CreateAgencyRequest request) {
        AgencyResponse created = AgencyResponse.from(administration.createAgency(request.toCommand()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{agencyId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{agencyId}")
    @Operation(summary = "Consulter une agence : abonnement actif et modules actives")
    @ApiResponse(responseCode = "200", description = "Agence trouvee")
    @ApiResponse(responseCode = "404", description = "Agence inconnue")
    AgencyResponse get(@PathVariable UUID agencyId) {
        return AgencyResponse.from(administration.getAgency(agencyId));
    }

    @PutMapping("/{agencyId}/modules/{moduleCode}")
    @Operation(summary = "Activer un module pour l'agence (idempotent ; reactive sans perte de donnees) (US-102)")
    @ApiResponse(responseCode = "204", description = "Module actif")
    @ApiResponse(responseCode = "404", description = "Agence ou module inconnu")
    ResponseEntity<Void> enableModule(@PathVariable UUID agencyId, @PathVariable String moduleCode) {
        moduleActivation.enable(agencyId, parseModule(moduleCode));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{agencyId}/modules/{moduleCode}")
    @Operation(summary = "Desactiver un module pour l'agence (idempotent ; les donnees sont conservees) (US-102)")
    @ApiResponse(responseCode = "204", description = "Module inactif")
    @ApiResponse(responseCode = "404", description = "Agence ou module inconnu")
    ResponseEntity<Void> disableModule(@PathVariable UUID agencyId, @PathVariable String moduleCode) {
        moduleActivation.disable(agencyId, parseModule(moduleCode));
        return ResponseEntity.noContent().build();
    }

    /** Code inconnu : 404 (ressource inexistante) plutot qu'une erreur de conversion 400. */
    private static ModuleCode parseModule(String code) {
        return Arrays.stream(ModuleCode.values())
                .filter(module -> module.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new ModuleNotFoundException(code));
    }
}
