package ma.agencysuite.iam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.iam.application.AuditJournal;
import ma.agencysuite.iam.domain.AuditEntry;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal d'audit de l'agence de l'utilisateur connecte. L'agence vient du jeton, jamais d'un
 * parametre. L'adresse IP n'est pas renvoyee (enquetes de securite de l'editeur seulement).
 */
@RestController
@Tag(name = "audit", description = "Journal d'audit de l'agence")
@RequiresPermission(PermissionCode.AUDIT_READ)
class AuditEventsController {

    private final AuditJournal journal;

    AuditEventsController(AuditJournal journal) {
        this.journal = journal;
    }

    @GetMapping("/api/v1/audit-events")
    @Operation(summary = "Entrees du journal d'audit de l'agence, de la plus recente a la plus ancienne")
    @ApiResponse(responseCode = "200", description = "Page d'entrees")
    @ApiResponse(responseCode = "400", description = "Pagination invalide (taille de 1 a 200)")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Permission audit.read absente")
    AuditEventsResponse list(
            @Parameter(description = "Numero de page, a partir de 0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 200)")
            @RequestParam(defaultValue = "50") @Min(1) @Max(AuditJournal.MAX_PAGE_SIZE) int size) {
        Page<AuditEntry> result = journal.page(page, size);
        return new AuditEventsResponse(result.map(AuditEvent::of).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Page du journal. */
    record AuditEventsResponse(List<AuditEvent> items, int page, int size, long totalElements, int totalPages) {
    }

    /** Entree du journal, sans adresse IP. {@code actorId} est nul pour une action du systeme. */
    record AuditEvent(Instant occurredAt, String actorType, UUID actorId, String action, String entityType,
            String entityId, Map<String, Object> details) {

        static AuditEvent of(AuditEntry entry) {
            return new AuditEvent(entry.getOccurredAt(), entry.getActorType().name(), entry.getActorId(),
                    entry.getAction().name(), entry.getEntityType(), entry.getEntityId(), entry.getDetails());
        }
    }
}
