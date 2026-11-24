package ma.agencysuite.platform.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.platform.application.AgencyDetails;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.AgencyKind;
import ma.agencysuite.platform.domain.AgencyStatus;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.shared.module.ModuleCode;

/** Agence vue du back-office : identite, palier de l'abonnement actif et modules actives. */
record AgencyResponse(
        UUID id,
        String name,
        String subdomain,
        AgencyStatus status,
        AgencyKind kind,
        String legalName,
        String ice,
        String planCode,
        SubscriptionPeriod period,
        LocalDate startDate,
        List<ModuleCode> enabledModules) {

    static AgencyResponse from(AgencyDetails details) {
        Agency agency = details.agency();
        Subscription subscription = details.activeSubscription().orElse(null);
        return new AgencyResponse(
                agency.getId(),
                agency.getName(),
                agency.getSubdomain(),
                agency.getStatus(),
                agency.getKind(),
                agency.getLegalName(),
                agency.getIce(),
                subscription == null ? null : subscription.getPlanCode(),
                subscription == null ? null : subscription.getPeriod(),
                subscription == null ? null : subscription.getStartDate(),
                List.copyOf(details.enabledModules()));
    }
}
