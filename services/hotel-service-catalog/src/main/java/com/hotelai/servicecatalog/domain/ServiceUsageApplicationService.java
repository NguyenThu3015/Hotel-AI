package com.hotelai.servicecatalog.domain;

import com.hotelai.servicecatalog.common.ApiException;
import com.hotelai.servicecatalog.config.CatalogProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServiceUsageApplicationService {
    private final HotelServiceItemRepository services;
    private final ServicePriceVersionRepository prices;
    private final StayProjectionRepository stays;
    private final ServiceUsageRepository usages;
    private final ServiceUsageRevisionRepository revisions;
    private final CatalogEventService events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId hotelZone;

    public ServiceUsageApplicationService(
            HotelServiceItemRepository services,
            ServicePriceVersionRepository prices,
            StayProjectionRepository stays,
            ServiceUsageRepository usages,
            ServiceUsageRevisionRepository revisions,
            CatalogEventService events,
            ObjectMapper objectMapper,
            Clock clock,
            CatalogProperties properties) {
        this.services = services;
        this.prices = prices;
        this.stays = stays;
        this.usages = usages;
        this.revisions = revisions;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hotelZone = ZoneId.of(properties.timeZone());
    }

    @Transactional(readOnly = true)
    public List<StayProjection> searchStays(String query) {
        return stays.findTop100ByOrderBySourceUpdatedAtDesc().stream()
                .filter(StayProjection::isOpen)
                .filter(value -> value.matches(query))
                .limit(50)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsageLedger ledger(UUID stayId) {
        StayProjection stay = requiredStay(stayId);
        List<ServiceUsage> values = usages.findByStayIdOrderByOccurredAtDesc(stayId);
        BigDecimal total = values.stream()
                .filter(value -> value.getStatus() != ServiceUsageStatus.CANCELLED)
                .map(ServiceUsage::getGrossAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        String paymentStatus = stay.getBalance().signum() <= 0 ? "PAID" : "OUTSTANDING";
        return new UsageLedger(stay, values, total.setScale(2), paymentStatus);
    }

    @Transactional
    public UsageLedger record(
            UUID actorId, String idempotencyKey, RecordUsage command) {
        requireKey(idempotencyKey);
        String requestHash = hash("RECORD", null, command);
        ServiceUsage replay = replay(idempotencyKey, requestHash, null);
        if (replay != null) return ledger(replay.getStayId());

        StayProjection stay = requiredOpenStay(command.stayId());
        HotelServiceItem service = requiredService(command.serviceId());
        Instant occurredAt = command.occurredAt() == null ? clock.instant() : command.occurredAt();
        validateServiceTime(stay, service, occurredAt, true);
        if (command.performed() && occurredAt.isAfter(clock.instant())) {
            throw ApiException.validation("A future service usage cannot already be performed");
        }
        ServicePriceVersion price = effectivePrice(service.getId(), occurredAt);
        requireCurrency(stay, price);
        ServiceUsage usage = usages.save(new ServiceUsage(
                stay, service, price, command.quantity(), occurredAt,
                command.performed(), actorId));
        revisions.save(new ServiceUsageRevision(
                usage, "RECORDED", idempotencyKey, requestHash,
                usage.getGrossAmount(), command.reason(), actorId));
        events.usageChanged(
                usage, "RECORDED", usage.getGrossAmount(), actorId,
                "hotel-service.usage-recorded.v1");
        return ledger(stay.getStayId());
    }

    @Transactional
    public UsageLedger adjust(
            UUID actorId, UUID usageId, String idempotencyKey, AdjustUsage command) {
        requireKey(idempotencyKey);
        if (command.reason() == null || command.reason().isBlank()) {
            throw ApiException.validation("Adjustment reason is required");
        }
        String requestHash = hash("ADJUST", usageId, command);
        ServiceUsage replay = replay(idempotencyKey, requestHash, usageId);
        if (replay != null) return ledger(replay.getStayId());

        ServiceUsage usage = requiredLockedUsage(usageId);
        StayProjection stay = requiredOpenStay(usage.getStayId());
        HotelServiceItem service = requiredService(usage.getServiceId());
        Instant occurredAt =
                command.occurredAt() == null ? usage.getOccurredAt() : command.occurredAt();
        validateServiceTime(stay, service, occurredAt, false);
        ServicePriceVersion price = effectivePrice(service.getId(), occurredAt);
        requireCurrency(stay, price);
        BigDecimal delta = usage.adjust(service, price, command.quantity(), occurredAt);
        revisions.save(new ServiceUsageRevision(
                usage, "ADJUSTED", idempotencyKey, requestHash,
                delta, command.reason(), actorId));
        events.usageChanged(
                usage, "ADJUSTED", delta, actorId,
                "hotel-service.usage-adjusted.v1");
        return ledger(stay.getStayId());
    }

    @Transactional
    public UsageLedger markPerformed(
            UUID actorId, UUID usageId, String idempotencyKey, String reason) {
        requireKey(idempotencyKey);
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("reason", reason);
        String requestHash = hash("PERFORM", usageId, command);
        ServiceUsage replay = replay(idempotencyKey, requestHash, usageId);
        if (replay != null) return ledger(replay.getStayId());

        ServiceUsage usage = requiredLockedUsage(usageId);
        StayProjection stay = requiredOpenStay(usage.getStayId());
        if (!usage.markPerformed()) return ledger(stay.getStayId());
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        revisions.save(new ServiceUsageRevision(
                usage, "PERFORMED", idempotencyKey, requestHash, zero, reason, actorId));
        events.usageChanged(
                usage, "PERFORMED", zero, actorId,
                "hotel-service.usage-performed.v1");
        return ledger(stay.getStayId());
    }

    @Transactional
    public UsageLedger cancel(
            UUID actorId,
            boolean manager,
            UUID usageId,
            String idempotencyKey,
            CancelUsage command) {
        requireKey(idempotencyKey);
        String requestHash = hash("CANCEL", usageId, command);
        ServiceUsage replay = replay(idempotencyKey, requestHash, usageId);
        if (replay != null) return ledger(replay.getStayId());

        ServiceUsage usage = requiredLockedUsage(usageId);
        StayProjection stay = requiredOpenStay(usage.getStayId());
        if (usage.getStatus() == ServiceUsageStatus.CANCELLED) {
            return ledger(stay.getStayId());
        }
        if (usage.getStatus() == ServiceUsageStatus.PERFORMED && !manager) {
            throw ApiException.forbidden(
                    "Manager approval is required to cancel a performed service");
        }
        BigDecimal delta = usage.cancel(actorId, command.reason(), manager);
        revisions.save(new ServiceUsageRevision(
                usage, "CANCELLED", idempotencyKey, requestHash,
                delta, command.reason(), actorId));
        events.usageChanged(
                usage, "CANCELLED", delta, actorId,
                "hotel-service.usage-cancelled.v1");
        return ledger(stay.getStayId());
    }

    private void validateServiceTime(
            StayProjection stay,
            HotelServiceItem service,
            Instant occurredAt,
            boolean newUsage) {
        LocalDate localDate = occurredAt.atZone(hotelZone).toLocalDate();
        if (localDate.isBefore(stay.getScheduledCheckIn())
                || localDate.isAfter(stay.getScheduledCheckOut())) {
            throw ApiException.conflict(
                    "usage-outside-stay", "Service time is outside the stay period");
        }
        boolean available = newUsage
                ? service.isAvailableAt(occurredAt, hotelZone)
                : service.isScheduledAt(occurredAt, hotelZone);
        if (!available) {
            throw ApiException.conflict(
                    newUsage ? "service-unavailable" : "invalid-service-time",
                    newUsage
                            ? "Service is inactive or unavailable at the selected time"
                            : "Selected time is outside the service schedule");
        }
    }

    private ServiceUsage replay(
            String key, String requestHash, UUID expectedUsageId) {
        ServiceUsageRevision revision = revisions.findByIdempotencyKey(key).orElse(null);
        if (revision == null) return null;
        if (!revision.getRequestHash().equals(requestHash)
                || (expectedUsageId != null && !expectedUsageId.equals(revision.getUsageId()))) {
            throw ApiException.conflict(
                    "idempotency-key-reused",
                    "Idempotency-Key was already used for a different command");
        }
        return usages.findById(revision.getUsageId())
                .orElseThrow(() -> new IllegalStateException("Idempotent usage no longer exists"));
    }

    private ServicePriceVersion effectivePrice(UUID serviceId, Instant at) {
        return prices.findByServiceIdAndValidFromLessThanEqualOrderByValidFromDesc(serviceId, at)
                .stream()
                .filter(value -> value.getValidTo() == null || at.isBefore(value.getValidTo()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict(
                        "service-price-unavailable",
                        "No effective service price exists at the selected time"));
    }

    private StayProjection requiredStay(UUID id) {
        return stays.findById(id).orElseThrow(() -> ApiException.notFound("Stay not found"));
    }

    private StayProjection requiredOpenStay(UUID id) {
        StayProjection stay = requiredStay(id);
        if (!stay.isOpen()) {
            throw ApiException.conflict(
                    "stay-not-open", "Service usage requires an open stay");
        }
        return stay;
    }

    private HotelServiceItem requiredService(UUID id) {
        return services.findById(id)
                .orElseThrow(() -> ApiException.notFound("Hotel service not found"));
    }

    private ServiceUsage requiredLockedUsage(UUID id) {
        return usages.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("Service usage not found"));
    }

    private static void requireCurrency(
            StayProjection stay, ServicePriceVersion price) {
        if (!stay.getCurrency().equals(price.getCurrency())) {
            throw ApiException.conflict(
                    "currency-mismatch",
                    "Service price currency does not match the stay currency");
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation(
                    "Idempotency-Key must contain 8-160 safe characters");
        }
    }

    private String hash(String operation, UUID usageId, Object command) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("operation", operation);
            value.put("usageId", usageId);
            value.put("command", command);
            byte[] bytes = objectMapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash service command", error);
        }
    }

    public record RecordUsage(
            UUID stayId,
            UUID serviceId,
            BigDecimal quantity,
            Instant occurredAt,
            boolean performed,
            String reason) {}

    public record AdjustUsage(
            BigDecimal quantity,
            Instant occurredAt,
            String reason) {}

    public record CancelUsage(String reason) {}

    public record UsageLedger(
            StayProjection stay,
            List<ServiceUsage> usages,
            BigDecimal serviceTotal,
            String paymentStatus) {}
}
