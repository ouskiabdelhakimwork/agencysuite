package ma.agencysuite.iam.application;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.AuditEntry;
import ma.agencysuite.iam.infrastructure.AuditEntryRepository;
import ma.agencysuite.shared.security.ActorContext;
import ma.agencysuite.shared.tenant.AgencyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ecrit une entree du journal d'audit dans la transaction de l'action tracee
 * ({@link Propagation#MANDATORY}) : l'entree et l'action sont enregistrees ensemble, ou aucune.
 * Un appel hors transaction est une erreur de programmation ({@code IllegalTransactionStateException}).
 *
 * <p>N'ouvre aucun perimetre : l'ecriture passe par la Row Level Security du perimetre de l'appelant
 * (agence courante uniquement, ou toute agence en perimetre plateforme). L'auteur est lu dans
 * {@link ActorContext} ({@code SYSTEM} sans contexte), l'adresse IP dans la requete HTTP courante.
 *
 * <p>Rien n'est journalise ici : l'adresse IP est une donnee personnelle, gardee seulement en base.
 */
@Service
public class AuditTrail {

    private final AuditEntryRepository entries;

    AuditTrail(AuditEntryRepository entries) {
        this.entries = entries;
    }

    /**
     * @param details codes techniques uniquement (ex. {@code {"module":"TRAVEL"}}), jamais d'e-mail,
     *        de nom ni de numero de document
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AgencyId agencyId, AuditAction action, String entityType, String entityId,
            Map<String, Object> details) {
        entries.save(new AuditEntry(agencyId, ActorContext.current(), action, entityType, entityId, details,
                remoteAddress(), Instant.now()));
    }

    /**
     * Adresse distante de la requete HTTP courante, {@code null} hors requete.
     * TODO(etape-12): derriere le proxy de production, configurer les en-tetes transferes
     * (server.forward-headers-strategy) pour obtenir l'adresse reelle du client.
     */
    private static String remoteAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
