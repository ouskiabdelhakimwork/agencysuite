package ma.agencysuite.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.platform.application.AgencyScopeRequiredException;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modules actifs de l'agence courante (servira au garde de routes Angular, lot 5).
 * L'agence vient du perimetre de la requete, jamais d'un parametre envoye par le client.
 */
@RestController
@Tag(name = "me", description = "Informations sur l'agence de l'utilisateur connecte")
class MeModulesController {

    private final PlatformApi platform;

    MeModulesController(PlatformApi platform) {
        this.platform = platform;
    }

    @GetMapping("/api/v1/me/modules")
    @Operation(summary = "Modules actives pour l'agence courante")
    @ApiResponse(responseCode = "200", description = "Modules actifs, dans l'ordre du catalogue")
    @ApiResponse(responseCode = "403", description = "Aucune agence courante")
    MyModules myModules() {
        AgencyId agency = TenantContext.current()
                .filter(TenantScope.Agency.class::isInstance)
                .map(scope -> ((TenantScope.Agency) scope).agencyId())
                .orElseThrow(AgencyScopeRequiredException::new);
        return new MyModules(List.copyOf(platform.enabledModules(agency)));
    }

    /** Reponse de GET /api/v1/me/modules. */
    record MyModules(List<ModuleCode> modules) {
    }
}
