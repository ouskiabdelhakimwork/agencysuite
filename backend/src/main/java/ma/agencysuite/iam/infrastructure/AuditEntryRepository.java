package ma.agencysuite.iam.infrastructure;

import java.time.Instant;
import java.util.UUID;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Journal d'audit, en ajout seul : ni modification ni suppression exposees (et interdites en base).
 *
 * <p>{@link AuditEntry} n'est pas filtree par Hibernate (pas de {@code @TenantId}) : chaque lecture
 * prend l'agence explicitement, en plus de la Row Level Security.
 */
public interface AuditEntryRepository extends Repository<AuditEntry, UUID> {

    AuditEntry save(AuditEntry entry);

    /** Entrees de l'agence, de la plus recente a la plus ancienne (tri du Pageable ignore). */
    Page<AuditEntry> findByAgencyIdOrderByOccurredAtDescIdDesc(UUID agencyId, Pageable pageable);

    /** Vrai si l'agence a deja une entree de cette action sur cette entite depuis cet instant. */
    boolean existsByAgencyIdAndActionAndEntityIdAndOccurredAtAfter(UUID agencyId, AuditAction action,
            String entityId, Instant since);
}
