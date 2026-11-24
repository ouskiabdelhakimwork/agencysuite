package ma.agencysuite.iam;

import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.module.RequiresModule;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TESTS UNIQUEMENT : endpoints proteges par @RequiresPermission (et @RequiresModule). */
@RestController
@RequestMapping("/api/v1/test/permission-probes")
@RequiresPermission(PermissionCode.AUDIT_READ)
class PermissionProbeController {

    /** Permission de la classe : audit.read. */
    @GetMapping
    String audit() {
        return "audit";
    }

    /** L'annotation de methode l'emporte sur celle de la classe : identity.read. */
    @GetMapping("/identity")
    @RequiresPermission(PermissionCode.IDENTITY_READ)
    String identity() {
        return "identity";
    }

    /** Module TRAVEL requis, puis audit.read (classe). */
    @GetMapping("/travel")
    @RequiresModule(ModuleCode.TRAVEL)
    String travel() {
        return "travel";
    }
}
