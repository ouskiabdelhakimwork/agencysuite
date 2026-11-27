package ma.agencysuite.iam.application;

import java.util.List;
import java.util.Map;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.platform.AgencyCreated;
import ma.agencysuite.platform.ModuleDisabled;
import ma.agencysuite.platform.ModuleEnabled;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.module.ModuleCode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Traduit les evenements du module platform, et les {@link AuditableEvent} des modules metier,
 * en entrees du journal d'audit.
 *
 * <p>Ecouteurs <b>synchrones</b> ({@code @EventListener}) : ils s'executent pendant
 * {@code publishEvent}, dans le fil et la transaction de l'action, donc avec son perimetre, son
 * auteur et sa requete HTTP. Une erreur d'ecriture fait echouer l'action ; une action annulee
 * annule son entree. Prefere a {@code @TransactionalEventListener(BEFORE_COMMIT)}, qui ignore
 * silencieusement un evenement publie hors transaction : ici, {@link AuditTrail} exige une
 * transaction (MANDATORY), et l'oubli est une erreur visible. Exception assumee, pour l'audit, a
 * la regle « ecouteurs apres le commit » (06-conventions-code.md).
 */
@Component
class AuditListeners {

    static final String AGENCY = "AGENCY";
    static final String MODULE = "MODULE";

    private final AuditTrail audit;

    AuditListeners(AuditTrail audit) {
        this.audit = audit;
    }

    @EventListener
    void on(AgencyCreated event) {
        List<String> modules = event.modules().stream().map(ModuleCode::name).sorted().toList();
        audit.record(event.agencyId(), AuditAction.AGENCY_CREATED, AGENCY, event.agencyId().value().toString(),
                Map.of("plan", event.planCode(), "modules", modules));
    }

    @EventListener
    void on(ModuleEnabled event) {
        audit.record(event.agencyId(), AuditAction.MODULE_ENABLED, MODULE, event.module().name(),
                Map.of("module", event.module().name()));
    }

    @EventListener
    void on(ModuleDisabled event) {
        audit.record(event.agencyId(), AuditAction.MODULE_DISABLED, MODULE, event.module().name(),
                Map.of("module", event.module().name()));
    }

    /**
     * Action d'un module metier (crm...), qui ne depend pas de iam. Un code inconnu de
     * {@link AuditAction} est une erreur de programmation : {@code valueOf} leve une exception,
     * propagee a l'action, dont la transaction est annulee.
     */
    @EventListener
    void on(AuditableEvent event) {
        audit.record(event.agencyId(), AuditAction.valueOf(event.action()), event.entityType(), event.entityId(),
                event.details());
    }
}
