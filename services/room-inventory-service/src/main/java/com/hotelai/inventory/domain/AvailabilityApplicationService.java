package com.hotelai.inventory.domain;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityApplicationService {
    private final RoomTypeRepository roomTypes;
    private final RoomRepository rooms;
    private final MaintenanceBlockRepository maintenanceBlocks;
    private final InventoryHoldRepository holds;
    private final RoomTypePriceSnapshotRepository prices;
    private final OutboxEventService outbox;
    private final EntityManager entityManager;
    private final DestinationRepository destinations;
    private final int defaultHoldMinutes;
    private final int maxSearchNights;

    public AvailabilityApplicationService(
            RoomTypeRepository roomTypes,
            RoomRepository rooms,
            MaintenanceBlockRepository maintenanceBlocks,
            InventoryHoldRepository holds,
            RoomTypePriceSnapshotRepository prices,
            OutboxEventService outbox,
            EntityManager entityManager,
            int defaultHoldMinutes,
            int maxSearchNights) {
        this(roomTypes, rooms, maintenanceBlocks, holds, prices, outbox, entityManager, null,
                defaultHoldMinutes, maxSearchNights);
    }

    @Autowired
    public AvailabilityApplicationService(
            RoomTypeRepository roomTypes,
            RoomRepository rooms,
            MaintenanceBlockRepository maintenanceBlocks,
            InventoryHoldRepository holds,
            RoomTypePriceSnapshotRepository prices,
            OutboxEventService outbox,
            EntityManager entityManager,
            DestinationRepository destinations,
            @Value("${hotel.inventory.default-hold-minutes:15}") int defaultHoldMinutes,
            @Value("${hotel.inventory.max-search-nights:90}") int maxSearchNights) {
        this.roomTypes = roomTypes;
        this.rooms = rooms;
        this.maintenanceBlocks = maintenanceBlocks;
        this.holds = holds;
        this.prices = prices;
        this.outbox = outbox;
        this.entityManager = entityManager;
        this.destinations = destinations;
        this.defaultHoldMinutes = defaultHoldMinutes;
        this.maxSearchNights = maxSearchNights;
    }

    private Map<String, Destination> loadDestinations() {
        if (destinations == null) return Map.of();
        return destinations.findAll().stream()
                .collect(Collectors.toMap(Destination::getCode, Function.identity(), (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse search(SearchCriteria criteria) {
        validate(criteria.checkIn(), criteria.checkOut(), criteria.adults(), criteria.children(), criteria.rooms());
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw ApiException.badRequest("invalid-price-range", "Giá tối thiểu phải nhỏ hơn hoặc bằng giá tối đa.");
        }
        Map<String, Destination> destinationByCode = loadDestinations();
        List<RoomType> candidates = roomTypes.findPublishedDetailed();
        Map<UUID, RoomTypePriceSnapshot> priceByType = prices.findAllById(
                        candidates.stream().map(RoomType::getId).toList()).stream()
                .collect(Collectors.toMap(RoomTypePriceSnapshot::getRoomTypeId, Function.identity()));
        List<AvailabilityItem> result = buildItems(criteria, candidates, priceByType, destinationByCode);
        result.sort(comparator(criteria.sort() == null ? AvailabilitySort.NAME_ASC : criteria.sort()));
        AlternativeStay alternative = result.isEmpty()
                ? nearestAlternative(criteria, candidates, destinationByCode)
                : null;
        return new AvailabilityResponse(criteria, result.size(), result, alternative, Instant.now());
    }

    @Transactional(readOnly = true)
    public AvailabilityCheckResponse check(AvailabilityCheckRequest request) {
        validate(request.checkIn(), request.checkOut(), 1, 0, request.quantity());
        RoomType type = roomTypes.findById(request.roomTypeId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại phòng."));
        if (!type.isActive() || !type.isPublished()) {
            throw ApiException.conflict("room-type-not-sellable", "Loại phòng không còn được bán.");
        }
        int available = availableCount(type.getId(), request.checkIn(), request.checkOut(), Instant.now());
        return new AvailabilityCheckResponse(
                type.getId(), request.checkIn(), request.checkOut(), request.quantity(), available,
                available >= request.quantity(), Instant.now());
    }

    @Transactional
    public HoldView createHold(String idempotencyKey, CreateHoldRequest request) {
        requireIdempotencyKey(idempotencyKey);
        validate(request.checkIn(), request.checkOut(), 1, 0, request.quantity());
        String hash = requestHash(request);
        // Serialize this key in PostgreSQL for the whole transaction. A RoomType row lock alone
        // cannot protect the global idempotency-key uniqueness when two requests target
        // different room types.
        holds.lockIdempotencyKey(idempotencyKey);
        InventoryHold existing = holds.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict(
                        "idempotency-key-reused",
                        "Idempotency-Key đã được dùng cho một yêu cầu giữ chỗ khác.");
            }
            return view(existing);
        }

        RoomType type = roomTypes.findByIdForUpdate(request.roomTypeId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại phòng."));
        if (!type.isActive() || !type.isPublished()) {
            throw ApiException.conflict("room-type-not-sellable", "Loại phòng không còn được bán.");
        }
        int available = availableCount(type.getId(), request.checkIn(), request.checkOut(), Instant.now());
        if (available < request.quantity()) {
            throw ApiException.conflict(
                    "insufficient-inventory",
                    "Chỉ còn " + available + " phòng trong khoảng ngày được chọn.");
        }
        int ttl = request.ttlMinutes() == null ? defaultHoldMinutes : request.ttlMinutes();
        InventoryHold hold = holds.save(new InventoryHold(
                type, request.externalReference(), request.checkIn(), request.checkOut(), request.quantity(),
                Instant.now().plus(Duration.ofMinutes(ttl)), idempotencyKey, hash));
        outbox.emit("InventoryHold", hold.getId(), "inventory.hold.created.v1", holdPayload(hold));
        return view(hold);
    }

    @Transactional
    public HoldView confirmHold(UUID holdId, UUID reservationId) {
        InventoryHold hold = holds.findByIdForUpdate(holdId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giữ chỗ."));
        if ("CONFIRMED".equals(hold.getStatus())) {
            if (!reservationId.equals(hold.getReservationId())) {
                throw ApiException.conflict(
                        "hold-already-confirmed", "Giữ chỗ đã thuộc về một đặt phòng khác.");
            }
            return view(hold);
        }
        hold.confirm(reservationId);
        outbox.emit("InventoryHold", hold.getId(), "inventory.hold.confirmed.v1", holdPayload(hold));
        entityManager.flush();
        return view(hold);
    }

    @Transactional
    public HoldView replaceConfirmedHold(
            UUID previousHoldId, UUID replacementHoldId, UUID reservationId) {
        if (previousHoldId.equals(replacementHoldId)) {
            return confirmHold(replacementHoldId, reservationId);
        }
        InventoryHold requestedPrevious = holds.findByIdForUpdate(previousHoldId)
                .orElseThrow(() -> ApiException.notFound("Previous inventory hold was not found."));
        InventoryHold replacement = holds.findByIdForUpdate(replacementHoldId)
                .orElseThrow(() -> ApiException.notFound("Replacement inventory hold was not found."));

        if ("CONFIRMED".equals(replacement.getStatus())
                && reservationId.equals(replacement.getReservationId())) {
            return view(replacement);
        }
        InventoryHold previous = requestedPrevious;
        if (!"CONFIRMED".equals(previous.getStatus())
                || !reservationId.equals(previous.getReservationId())) {
            previous = holds.findConfirmedByReservationIdForUpdate(reservationId)
                    .orElseThrow(() -> ApiException.conflict(
                            "previous-hold-mismatch",
                            "No confirmed inventory hold belongs to this reservation."));
        }

        previous.release();
        outbox.emit(
                "InventoryHold", previous.getId(), "inventory.hold.released.v1",
                holdPayload(previous));
        // Clear the partial unique index before confirming the replacement. Both
        // changes remain in this transaction and roll back together on failure.
        entityManager.flush();

        replacement.confirm(reservationId);
        outbox.emit(
                "InventoryHold", replacement.getId(), "inventory.hold.confirmed.v1",
                holdPayload(replacement));
        entityManager.flush();
        return view(replacement);
    }

    @Transactional
    public HoldView releaseHold(UUID holdId) {
        InventoryHold hold = holds.findByIdForUpdate(holdId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giữ chỗ."));
        String previous = hold.getStatus();
        hold.release();
        if (!previous.equals(hold.getStatus())) {
            outbox.emit("InventoryHold", hold.getId(), "inventory.hold.released.v1", holdPayload(hold));
            entityManager.flush();
        }
        return view(hold);
    }

    @Transactional(readOnly = true)
    public HoldView getHold(UUID holdId) {
        return holds.findById(holdId).map(AvailabilityApplicationService::view)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giữ chỗ."));
    }

    @Scheduled(fixedDelayString = "${hotel.inventory.expiry-delay-ms:30000}")
    @Transactional
    public void expireHolds() {
        Instant now = Instant.now();
        for (InventoryHold hold : holds.findExpired(now, PageRequest.of(0, 100))) {
            hold.expire(now);
            outbox.emit("InventoryHold", hold.getId(), "inventory.hold.expired.v1", holdPayload(hold));
        }
    }

    private List<AvailabilityItem> buildItems(
            SearchCriteria criteria, List<RoomType> candidates,
            Map<UUID, RoomTypePriceSnapshot> priceByType,
            Map<String, Destination> destinationByCode) {
        List<AvailabilityItem> result = new ArrayList<>();
        Set<UUID> selectedTypes = criteria.roomTypeIds() == null ? Set.of() : criteria.roomTypeIds();
        Set<UUID> requiredAmenities = criteria.amenityIds() == null ? Set.of() : criteria.amenityIds();
        Instant now = Instant.now();
        for (RoomType type : candidates) {
            if (!matchesDestinationAndRegion(type, criteria, destinationByCode)) continue;
            if (!selectedTypes.isEmpty() && !selectedTypes.contains(type.getId())) continue;
            if (type.getMaxAdults() * criteria.rooms() < criteria.adults()) continue;
            if (type.getMaxOccupancy() * criteria.rooms() < criteria.adults() + criteria.children()) continue;
            Set<UUID> typeAmenities = type.getAmenities().stream()
                    .filter(Amenity::isActive).map(Amenity::getId).collect(Collectors.toSet());
            if (!typeAmenities.containsAll(requiredAmenities)) continue;
            int available = availableCount(type.getId(), criteria.checkIn(), criteria.checkOut(), now);
            if (available < criteria.rooms()) continue;

            RoomTypePriceSnapshot snapshot = priceByType.get(type.getId());
            if (!matchesPrice(snapshot, criteria.minPrice(), criteria.maxPrice())) continue;
            Destination dest = destinationByCode.get(type.getDestinationCode());
            result.add(new AvailabilityItem(
                    type.getId(), type.getCode(), type.getName(), type.getDescription(),
                    type.getMaxAdults(), type.getMaxChildren(), type.getMaxOccupancy(),
                    type.getSizeSqm(), type.getBedDescription(), available,
                    estimate(snapshot, criteria.checkIn(), criteria.checkOut(), criteria.rooms()),
                    type.getAmenities().stream().filter(Amenity::isActive)
                            .sorted(Comparator.comparing(Amenity::getName).thenComparing(Amenity::getId))
                            .map(CatalogApplicationService::view).toList(),
                    type.activeImages().stream().map(CatalogApplicationService::view).toList(),
                    type.getPolicySummary(),
                    type.getDestinationCode(),
                    dest != null ? dest.getName() : "Phú Quốc",
                    dest != null ? dest.getProvince() : "Kiên Giang",
                    dest != null ? dest.getRegion() : "NAM",
                    dest != null ? dest.getRegionLabel() : "Miền Nam",
                    dest != null ? dest.getStyleTag() : "Biển đảo",
                    dest != null ? dest.getPropertyName() : "HotelAI Phú Quốc",
                    dest != null ? dest.getLandmark() : "Bãi Kem"));
        }
        return result;
    }

    private static boolean matchesDestinationAndRegion(
            RoomType type, SearchCriteria criteria, Map<String, Destination> destinationByCode) {
        String requestedDest = criteria.destinationCode();
        if (requestedDest != null && !requestedDest.isBlank()
                && !"ALL".equalsIgnoreCase(requestedDest.trim())) {
            String normalized = requestedDest.trim().toUpperCase(Locale.ROOT);
            if (!normalized.equalsIgnoreCase(type.getDestinationCode())) {
                return false;
            }
        }
        String requestedRegion = criteria.region();
        if (requestedRegion != null && !requestedRegion.isBlank()
                && !"ALL".equalsIgnoreCase(requestedRegion.trim())) {
            Destination dest = destinationByCode.get(type.getDestinationCode());
            String typeRegion = dest != null ? dest.getRegion() : "NAM";
            if (!requestedRegion.trim().equalsIgnoreCase(typeRegion)) {
                return false;
            }
        }
        return true;
    }

    private int availableCount(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, Instant now) {
        long total = rooms.countActiveByRoomType(roomTypeId);
        long maintenance = maintenanceBlocks.countUnavailableRooms(roomTypeId, checkIn, checkOut);
        long committed = holds.sumCommittedQuantity(roomTypeId, checkIn, checkOut, now);
        return (int) Math.max(0, total - maintenance - committed);
    }

    private AlternativeStay nearestAlternative(
            SearchCriteria criteria, List<RoomType> candidates,
            Map<String, Destination> destinationByCode) {
        long nights = ChronoUnit.DAYS.between(criteria.checkIn(), criteria.checkOut());
        Instant now = Instant.now();
        for (int offset = 1; offset <= 14; offset++) {
            LocalDate checkIn = criteria.checkIn().plusDays(offset);
            LocalDate checkOut = checkIn.plusDays(nights);
            boolean found = candidates.stream().anyMatch(type ->
                    matchesDestinationAndRegion(type, criteria, destinationByCode)
                            && matchesStaticFilters(type, criteria)
                            && type.getMaxAdults() * criteria.rooms() >= criteria.adults()
                            && type.getMaxOccupancy() * criteria.rooms() >= criteria.adults() + criteria.children()
                            && availableCount(type.getId(), checkIn, checkOut, now) >= criteria.rooms());
            if (found) return new AlternativeStay(checkIn, checkOut);
        }
        return null;
    }

    private static boolean matchesStaticFilters(RoomType type, SearchCriteria criteria) {
        Set<UUID> selectedTypes = criteria.roomTypeIds() == null ? Set.of() : criteria.roomTypeIds();
        Set<UUID> requiredAmenities = criteria.amenityIds() == null ? Set.of() : criteria.amenityIds();
        if (!selectedTypes.isEmpty() && !selectedTypes.contains(type.getId())) return false;
        Set<UUID> actualAmenities = type.getAmenities().stream()
                .filter(Amenity::isActive).map(Amenity::getId).collect(Collectors.toSet());
        return actualAmenities.containsAll(requiredAmenities);
    }

    private static boolean matchesPrice(
            RoomTypePriceSnapshot snapshot, BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null && maxPrice == null) return true;
        if (snapshot == null) return false;
        return (minPrice == null || snapshot.getNightlyPrice().compareTo(minPrice) >= 0)
                && (maxPrice == null || snapshot.getNightlyPrice().compareTo(maxPrice) <= 0);
    }

    private static PriceEstimate estimate(
            RoomTypePriceSnapshot snapshot, LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (snapshot == null) {
            return new PriceEstimate("UNAVAILABLE", null, null, null, null, null, null, 0, null);
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal subtotal = money(snapshot.getNightlyPrice()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(rooms)));
        BigDecimal fee = money(snapshot.getServiceFee());
        BigDecimal tax = money(subtotal.add(fee).multiply(snapshot.getTaxRate()));
        return new PriceEstimate(
                "AVAILABLE", money(snapshot.getNightlyPrice()), snapshot.getCurrency(), subtotal,
                tax, fee, money(subtotal.add(fee).add(tax)), snapshot.getPricingVersion(),
                snapshot.getEffectiveAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Comparator<AvailabilityItem> comparator(AvailabilitySort sort) {
        Comparator<AvailabilityItem> tie = Comparator.comparing(AvailabilityItem::roomTypeId);
        return switch (sort) {
            case PRICE_ASC -> Comparator.comparing(
                            AvailabilityApplicationService::nightlyPrice,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(tie);
            case PRICE_DESC -> Comparator.comparing(
                            AvailabilityApplicationService::nightlyPrice,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(tie);
            case CAPACITY_ASC -> Comparator.comparingInt(AvailabilityItem::maxOccupancy).thenComparing(tie);
            case CAPACITY_DESC -> Comparator.comparingInt(AvailabilityItem::maxOccupancy).reversed()
                    .thenComparing(tie);
            case NAME_ASC -> Comparator.comparing(AvailabilityItem::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(tie);
        };
    }

    private static BigDecimal nightlyPrice(AvailabilityItem item) {
        return item.price() == null ? null : item.price().nightlyPrice();
    }

    private void validate(LocalDate checkIn, LocalDate checkOut, int adults, int children, int quantity) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw ApiException.badRequest("invalid-stay-dates", "Ngày trả phải sau ngày nhận phòng.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("past-check-in", "Ngày nhận phòng không được ở trong quá khứ.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > maxSearchNights) {
            throw ApiException.badRequest(
                    "stay-too-long", "Khoảng lưu trú tối đa là " + maxSearchNights + " đêm.");
        }
        if (adults < 1 || children < 0 || quantity < 1 || quantity > 20) {
            throw ApiException.badRequest("invalid-occupancy", "Số khách hoặc số phòng không hợp lệ.");
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,150}")) {
            throw ApiException.badRequest(
                    "invalid-idempotency-key", "Idempotency-Key phải có từ 8 đến 150 ký tự hợp lệ.");
        }
    }

    private static String requestHash(CreateHoldRequest request) {
        try {
            String canonical = String.join("|",
                    request.roomTypeId().toString(), request.checkIn().toString(),
                    request.checkOut().toString(), String.valueOf(request.quantity()),
                    request.externalReference() == null ? "" : request.externalReference().trim(),
                    request.ttlMinutes() == null ? "" : request.ttlMinutes().toString());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static HoldView view(InventoryHold hold) {
        return new HoldView(
                hold.getId(), hold.getRoomTypeId(), hold.getReservationId(),
                hold.getExternalReference(), hold.getCheckIn(), hold.getCheckOut(), hold.getQuantity(),
                hold.getStatus(), hold.getExpiresAt(), hold.getVersion());
    }

    private static Map<String, ?> holdPayload(InventoryHold hold) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("holdId", hold.getId());
        payload.put("roomTypeId", hold.getRoomTypeId());
        payload.put("reservationId", hold.getReservationId());
        payload.put("externalReference", hold.getExternalReference());
        payload.put("checkIn", hold.getCheckIn());
        payload.put("checkOut", hold.getCheckOut());
        payload.put("quantity", hold.getQuantity());
        payload.put("status", hold.getStatus());
        payload.put("expiresAt", hold.getExpiresAt());
        return payload;
    }
}
