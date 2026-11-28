package ma.agencysuite.crm.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.domain.Traveler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Voyageurs de l'agence courante (filtre @TenantId + RLS). */
public interface TravelerRepository extends JpaRepository<Traveler, UUID>, JpaSpecificationExecutor<Traveler> {

    /** Voyageurs non archives de meme cle de nom et de meme date de naissance (doublon probable). */
    List<Traveler> findTop10ByArchivedAtIsNullAndNameKeyAndBirthDateOrderByNameKeyAscIdAsc(String nameKey,
            LocalDate birthDate);

    /** Voyageurs non archives ayant cet e-mail (deja normalise), candidats au doublon probable. */
    List<Traveler> findTop10ByArchivedAtIsNullAndEmailOrderByNameKeyAscIdAsc(String email);

    /** Voyageurs non archives ayant ce telephone (E.164), candidats au doublon probable. */
    List<Traveler> findTop10ByArchivedAtIsNullAndPhoneOrderByNameKeyAscIdAsc(String phone);
}
