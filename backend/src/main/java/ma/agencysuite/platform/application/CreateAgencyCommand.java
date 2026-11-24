package ma.agencysuite.platform.application;

import java.time.LocalDate;
import java.util.List;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.shared.module.ModuleCode;

/** Creation d'une agence cliente depuis le back-office (US-101). */
public record CreateAgencyCommand(
        String name,
        String subdomain,
        String legalName,
        String ice,
        String ifNumber,
        String rc,
        String patente,
        String address,
        String planCode,
        SubscriptionPeriod period,
        LocalDate startDate,
        List<ModuleCode> modules) {

    public CreateAgencyCommand {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}
