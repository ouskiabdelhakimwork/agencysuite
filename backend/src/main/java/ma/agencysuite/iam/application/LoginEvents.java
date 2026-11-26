package ma.agencysuite.iam.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.infrastructure.AuditEntryRepository;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connexion de l'utilisateur courant de l'agence, signalee par l'interface juste apres le retour du
 * fournisseur d'identite : met a jour last_login_at et trace USER_LOGGED_IN, dans une transaction.
 *
 * <p>Une seconde connexion du meme utilisateur a moins de {@link #MIN_INTERVAL} est ignoree (ni
 * entree ni mise a jour) : protection contre les rechargements en boucle, pas une securite. Les
 * echecs de connexion sont vus par Keycloak (evenements du realm), pas par l'application.
 */
@Service
public class LoginEvents {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(60);
    static final String USER = "USER";

    private final UserAccountRepository users;
    private final AuditEntryRepository entries;
    private final AuditTrail audit;

    LoginEvents(UserAccountRepository users, AuditEntryRepository entries, AuditTrail audit) {
        this.users = users;
        this.entries = entries;
        this.audit = audit;
    }

    /** @return vrai si la connexion a ete tracee, faux si elle a ete ignoree (trop rapprochee) */
    @Transactional
    public boolean recordLogin() {
        CurrentUser current = UserContext.current().orElseThrow(UserRequiredException::new);
        AgencyId agencyId = TenantContext.requireAgency();
        UserAccount user = users.findById(current.userId())
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));
        Instant now = Instant.now();
        String userId = user.getId().toString();
        if (entries.existsByAgencyIdAndActionAndEntityIdAndOccurredAtAfter(agencyId.value(),
                AuditAction.USER_LOGGED_IN, userId, now.minus(MIN_INTERVAL))) {
            return false;
        }
        user.recordLogin(now);
        audit.record(agencyId, AuditAction.USER_LOGGED_IN, USER, userId, Map.of());
        return true;
    }
}
