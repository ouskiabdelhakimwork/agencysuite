package ma.agencysuite.iam.application;

import ma.agencysuite.iam.domain.AuditEntry;
import ma.agencysuite.iam.infrastructure.AuditEntryRepository;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture du journal d'audit de l'agence courante (permission audit.read, controlee par le
 * controleur). N'ouvre aucun perimetre ; filtre explicitement sur l'agence courante en plus de la
 * Row Level Security, puisque Hibernate ne filtre pas {@link AuditEntry}. Hors perimetre agence :
 * {@link IllegalStateException} (erreur de programmation).
 */
@Service
public class AuditJournal {

    public static final int MAX_PAGE_SIZE = 200;

    private final AuditEntryRepository entries;

    AuditJournal(AuditEntryRepository entries) {
        this.entries = entries;
    }

    /** Entrees de l'agence courante, de la plus recente a la plus ancienne. */
    @Transactional(readOnly = true)
    public Page<AuditEntry> page(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Pagination hors limites");
        }
        return entries.findByAgencyIdOrderByOccurredAtDescIdDesc(TenantContext.requireAgency().value(),
                PageRequest.of(page, size));
    }
}
