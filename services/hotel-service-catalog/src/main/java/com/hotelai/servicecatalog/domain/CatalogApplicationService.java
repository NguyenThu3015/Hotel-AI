package com.hotelai.servicecatalog.domain;

import com.hotelai.servicecatalog.common.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogApplicationService {
    private final HotelServiceItemRepository services;
    private final ServicePriceVersionRepository prices;
    private final ServiceUsageRepository usages;
    private final CatalogEventService events;
    private final Clock clock;

    public CatalogApplicationService(
            HotelServiceItemRepository services,
            ServicePriceVersionRepository prices,
            ServiceUsageRepository usages,
            CatalogEventService events,
            Clock clock) {
        this.services = services;
        this.prices = prices;
        this.usages = usages;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CatalogEntry> list(boolean includeInactive) {
        List<HotelServiceItem> values = includeInactive
                ? services.findAllByOrderByCodeAsc()
                : services.findByActiveTrueOrderByCodeAsc();
        return values.stream().map(this::entry).toList();
    }

    @Transactional(readOnly = true)
    public CatalogEntry get(UUID id, boolean allowInactive) {
        HotelServiceItem service = required(id);
        if (!allowInactive && !service.isActive()) {
            throw ApiException.notFound("Hotel service not found");
        }
        return entry(service);
    }

    @Transactional
    public CatalogEntry create(UUID actorId, CatalogCommand command) {
        validateCodeUnique(command.code(), null);
        Instant effectiveAt = command.priceEffectiveAt() == null
                ? clock.instant()
                : command.priceEffectiveAt();
        HotelServiceItem service = services.save(new HotelServiceItem(
                command.code(), command.name(), command.description(), command.unit(),
                command.serviceDays(), command.serviceStartTime(), command.serviceEndTime(),
                command.serviceConditions(), command.active(), actorId));
        ServicePriceVersion price = prices.save(new ServicePriceVersion(
                service.getId(), 1, command.unitPrice(), command.taxRate(),
                normalizeCurrency(command.currency()), effectiveAt, actorId));
        events.catalogChanged(service, price, "hotel-service.catalog-created.v1");
        return new CatalogEntry(service, price, List.of(price), false);
    }

    @Transactional
    public CatalogEntry update(UUID actorId, UUID id, CatalogCommand command) {
        HotelServiceItem service = services.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("Hotel service not found"));
        validateCodeUnique(command.code(), id);
        service.update(
                command.code(), command.name(), command.description(), command.unit(),
                command.serviceDays(), command.serviceStartTime(), command.serviceEndTime(),
                command.serviceConditions(), command.active());
        ServicePriceVersion current = currentPrice(id);
        BigDecimal newPrice = money(command.unitPrice(), "unitPrice");
        BigDecimal newTax = tax(command.taxRate());
        String newCurrency = normalizeCurrency(command.currency());
        if (!current.sameFinancialTerms(newPrice, newTax, newCurrency)) {
            Instant effectiveAt = command.priceEffectiveAt() == null
                    ? clock.instant()
                    : command.priceEffectiveAt();
            current.closeAt(effectiveAt);
            current = prices.save(new ServicePriceVersion(
                    id, current.getVersionNumber() + 1, newPrice, newTax,
                    newCurrency, effectiveAt, actorId));
        }
        events.catalogChanged(service, current, "hotel-service.catalog-updated.v1");
        return entry(service);
    }

    private CatalogEntry entry(HotelServiceItem service) {
        List<ServicePriceVersion> history =
                prices.findByServiceIdOrderByValidFromDesc(service.getId());
        ServicePriceVersion current = history.stream()
                .filter(value -> value.getValidTo() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Service has no active price"));
        return new CatalogEntry(
                service, current, history, usages.existsByServiceId(service.getId()));
    }

    private HotelServiceItem required(UUID id) {
        return services.findById(id)
                .orElseThrow(() -> ApiException.notFound("Hotel service not found"));
    }

    private ServicePriceVersion currentPrice(UUID serviceId) {
        return prices.findFirstByServiceIdAndValidToIsNullOrderByValidFromDesc(serviceId)
                .orElseThrow(() -> new IllegalStateException("Service has no active price"));
    }

    private void validateCodeUnique(String code, UUID excludedId) {
        if (code == null || code.isBlank()) throw ApiException.validation("Service code is required");
        UUID excluded = excludedId == null ? new UUID(0, 0) : excludedId;
        if (services.existsByCodeIgnoreCaseAndIdNot(code.trim(), excluded)) {
            throw ApiException.conflict("duplicate-service-code", "Service code already exists");
        }
    }

    private static BigDecimal money(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw ApiException.validation(name + " must be non-negative");
        }
        return value.setScale(2);
    }

    private static BigDecimal tax(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.validation("taxRate must be between 0 and 1");
        }
        return value.setScale(4);
    }

    private static String normalizeCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw ApiException.validation("Currency must be an ISO-4217 code");
        }
        return value;
    }

    public record CatalogCommand(
            String code,
            String name,
            String description,
            String unit,
            Set<DayOfWeek> serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime,
            String serviceConditions,
            boolean active,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            String currency,
            Instant priceEffectiveAt) {}

    public record CatalogEntry(
            HotelServiceItem service,
            ServicePriceVersion currentPrice,
            List<ServicePriceVersion> priceHistory,
            boolean hasUsage) {}
}
