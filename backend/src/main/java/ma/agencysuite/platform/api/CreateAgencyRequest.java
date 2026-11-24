package ma.agencysuite.platform.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import ma.agencysuite.platform.application.CreateAgencyCommand;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.shared.module.ModuleCode;

/** Corps de POST /api/v1/backoffice/agencies (US-101). */
@Schema(description = "Creation d'une agence cliente avec son palier et ses modules")
record CreateAgencyRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Atlas Voyages") String name,
        @NotBlank
        @Schema(description = "Sous-domaine : minuscules, chiffres et tirets, 3 a 63 caracteres, hors noms reserves",
                example = "atlas-voyages")
        String subdomain,
        @Size(max = 200) String legalName,
        @Pattern(regexp = "\\d{15}")
        @Schema(description = "ICE : 15 chiffres (format a confirmer avec l'expert-comptable)",
                example = "001234567000089")
        String ice,
        @Size(max = 20) String ifNumber,
        @Size(max = 30) String rc,
        @Size(max = 30) String patente,
        @Size(max = 500) String address,
        @NotBlank @Schema(example = "PRO") String planCode,
        @NotNull SubscriptionPeriod period,
        @NotNull LocalDate startDate,
        @Schema(description = "Modules actives des la creation (peut etre vide)")
        List<@NotNull ModuleCode> modules) {

    CreateAgencyCommand toCommand() {
        return new CreateAgencyCommand(name, subdomain, legalName, ice, ifNumber, rc, patente, address, planCode,
                period, startDate, modules);
    }
}
