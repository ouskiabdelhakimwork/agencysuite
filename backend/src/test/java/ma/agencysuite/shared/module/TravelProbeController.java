package ma.agencysuite.shared.module;

import java.util.List;
import ma.agencysuite.shared.tenant.TenantProbe;
import ma.agencysuite.shared.tenant.TenantProbeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * TESTS UNIQUEMENT : controleur d'un faux module TRAVEL, pour verifier la garde @RequiresModule.
 * Stocke des sondes (TenantProbe) appartenant a l'agence courante.
 */
@RestController
@RequestMapping("/api/v1/test/travel-probes")
@RequiresModule(ModuleCode.TRAVEL)
class TravelProbeController {

    private final TenantProbeRepository probes;

    TravelProbeController(TenantProbeRepository probes) {
        this.probes = probes;
    }

    @GetMapping
    List<String> list() {
        List<TenantProbe> all = probes.findAll();
        return all.stream().map(TenantProbe::getLabel).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    void create(@RequestBody String label) {
        probes.save(new TenantProbe(label));
    }
}
