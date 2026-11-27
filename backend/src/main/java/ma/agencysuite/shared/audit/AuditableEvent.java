package ma.agencysuite.shared.audit;

import java.util.Map;
import java.util.Objects;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Action d'un module metier a tracer dans le journal d'audit, sans que ce module depende de
 * {@code iam} (qui possede le journal et la liste fermee des actions).
 *
 * <p>Publie avec {@code ApplicationEventPublisher} <b>dans la transaction de l'action</b> : le
 * journal l'ecrit de facon synchrone dans cette transaction (les deux sont enregistres, ou aucun).
 * Un code d'action inconnu du journal est une erreur de programmation : l'action est annulee.
 *
 * @param agencyId agence concernee (celle du perimetre courant)
 * @param action code de l'action, nom d'une valeur de {@code AuditAction} (ex. {@code CUSTOMER_ARCHIVED})
 * @param entityType type de l'objet (ex. {@code CUSTOMER})
 * @param entityId identifiant de l'objet
 * @param details codes techniques uniquement (ex. {@code {"type":"COMPANY"}}), jamais de nom,
 *        d'e-mail, de telephone ni de numero de document
 */
public record AuditableEvent(AgencyId agencyId, String action, String entityType, String entityId,
        Map<String, Object> details) {

    public AuditableEvent {
        Objects.requireNonNull(agencyId, "agencyId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }
}
