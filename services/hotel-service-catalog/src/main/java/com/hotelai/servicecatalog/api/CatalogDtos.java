package com.hotelai.servicecatalog.api;

import com.hotelai.servicecatalog.domain.CatalogApplicationService.CatalogCommand;
import com.hotelai.servicecatalog.domain.CatalogApplicationService.CatalogEntry;
import com.hotelai.servicecatalog.domain.HotelServiceItem;
import com.hotelai.servicecatalog.domain.ServicePriceVersion;
import com.hotelai.servicecatalog.domain.ServiceUsage;
import com.hotelai.servicecatalog.domain.ServiceUsageApplicationService.UsageLedger;
import com.hotelai.servicecatalog.domain.ServiceUsageStatus;
import com.hotelai.servicecatalog.domain.StayProjection;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CatalogRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 30) String unit,
            @NotEmpty Set<DayOfWeek> serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime,
            @Size(max = 1000) String serviceConditions,
            boolean active,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal taxRate,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            Instant priceEffectiveAt) {

        CatalogCommand command() {
            return new CatalogCommand(
                    code, name, description, unit, serviceDays,
                    serviceStartTime, serviceEndTime, serviceConditions, active,
                    unitPrice, taxRate, currency, priceEffectiveAt);
        }
    }

    public record PriceVersionView(
            UUID id,
            long version,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            String currency,
            Instant validFrom,
            Instant validTo) {
        static PriceVersionView from(ServicePriceVersion value) {
            return new PriceVersionView(
                    value.getId(), value.getVersionNumber(), value.getUnitPrice(),
                    value.getTaxRate(), value.getCurrency(), value.getValidFrom(),
                    value.getValidTo());
        }
    }

    public record CatalogView(
            UUID id,
            String code,
            String name,
            String description,
            String unit,
            Set<DayOfWeek> serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime,
            String serviceConditions,
            boolean active,
            long catalogVersion,
            boolean hasUsage,
            PriceVersionView currentPrice,
            List<PriceVersionView> priceHistory) {
        static CatalogView from(CatalogEntry entry, boolean includeHistory) {
            HotelServiceItem value = entry.service();
            return new CatalogView(
                    value.getId(), value.getCode(), value.getName(), value.getDescription(),
                    value.getUnit(), value.getServiceDays(), value.getServiceStartTime(),
                    value.getServiceEndTime(), value.getServiceConditions(), value.isActive(),
                    value.getVersion(), entry.hasUsage(),
                    PriceVersionView.from(entry.currentPrice()),
                    includeHistory
                            ? entry.priceHistory().stream().map(PriceVersionView::from).toList()
                            : List.of());
        }
    }

    public record RecordUsageRequest(
            @NotNull UUID stayId,
            @NotNull UUID serviceId,
            @NotNull @DecimalMin("0.001") @DecimalMax("999.999") BigDecimal quantity,
            Instant occurredAt,
            boolean performed,
            @Size(max = 500) String reason) {}

    public record AdjustUsageRequest(
            @NotNull @DecimalMin("0.001") @DecimalMax("999.999") BigDecimal quantity,
            Instant occurredAt,
            @NotBlank @Size(max = 500) String reason) {}

    public record CancelUsageRequest(
            @NotBlank @Size(max = 500) String reason) {}

    public record MarkPerformedRequest(@Size(max = 500) String reason) {}

    public record StaySummaryView(
            UUID stayId,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String contactName,
            String status,
            LocalDate scheduledCheckIn,
            LocalDate scheduledCheckOut,
            List<String> roomNumbers,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            BigDecimal balance) {
        static StaySummaryView from(StayProjection value) {
            return new StaySummaryView(
                    value.getStayId(), value.getReservationId(), value.getCustomerId(),
                    value.getReservationReference(), value.getContactName(), value.getStatus(),
                    value.getScheduledCheckIn(), value.getScheduledCheckOut(),
                    value.roomNumberList(), value.getCurrency(), value.getChargesTotal(),
                    value.getPaidTotal(), value.getBalance());
        }
    }

    public record UsageView(
            UUID id,
            UUID serviceId,
            String serviceCode,
            String serviceName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            String currency,
            Instant occurredAt,
            ServiceUsageStatus status,
            int revision,
            UUID cancelledBy,
            Instant cancelledAt,
            String cancellationReason,
            long version) {
        static UsageView from(ServiceUsage value) {
            return new UsageView(
                    value.getId(), value.getServiceId(), value.getServiceCode(),
                    value.getServiceName(), value.getUnit(), value.getQuantity(),
                    value.getUnitPrice(), value.getTaxRate(), value.getNetAmount(),
                    value.getTaxAmount(), value.getGrossAmount(), value.getCurrency(),
                    value.getOccurredAt(), value.getStatus(), value.getRevision(),
                    value.getCancelledBy(), value.getCancelledAt(),
                    value.getCancellationReason(), value.getVersion());
        }
    }

    public record UsageLedgerView(
            StaySummaryView stay,
            List<UsageView> usages,
            BigDecimal serviceTotal,
            String paymentStatus) {
        static UsageLedgerView from(UsageLedger value) {
            return new UsageLedgerView(
                    StaySummaryView.from(value.stay()),
                    value.usages().stream().map(UsageView::from).toList(),
                    value.serviceTotal(), value.paymentStatus());
        }
    }
}
