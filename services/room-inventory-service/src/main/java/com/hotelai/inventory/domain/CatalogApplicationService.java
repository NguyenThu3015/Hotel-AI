package com.hotelai.inventory.domain;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.common.ApiException;
import com.hotelai.inventory.common.RequestContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogApplicationService {
    private final FloorRepository floors;
    private final AmenityRepository amenities;
    private final RoomTypeRepository roomTypes;
    private final RoomImageRepository images;
    private final RoomRepository rooms;
    private final RoomStatusHistoryRepository statusHistory;
    private final MaintenanceBlockRepository maintenanceBlocks;
    private final InventoryHoldRepository holds;
    private final OutboxEventService outbox;
    private final EntityManager entityManager;
    private final DestinationRepository destinations;
    private final RoomTypePriceSnapshotRepository prices;

    public CatalogApplicationService(
            FloorRepository floors,
            AmenityRepository amenities,
            RoomTypeRepository roomTypes,
            RoomImageRepository images,
            RoomRepository rooms,
            RoomStatusHistoryRepository statusHistory,
            MaintenanceBlockRepository maintenanceBlocks,
            InventoryHoldRepository holds,
            OutboxEventService outbox,
            EntityManager entityManager) {
        this(floors, amenities, roomTypes, images, rooms, statusHistory, maintenanceBlocks,
                holds, outbox, entityManager, null, null);
    }

    @Autowired
    public CatalogApplicationService(
            FloorRepository floors,
            AmenityRepository amenities,
            RoomTypeRepository roomTypes,
            RoomImageRepository images,
            RoomRepository rooms,
            RoomStatusHistoryRepository statusHistory,
            MaintenanceBlockRepository maintenanceBlocks,
            InventoryHoldRepository holds,
            OutboxEventService outbox,
            EntityManager entityManager,
            DestinationRepository destinations,
            RoomTypePriceSnapshotRepository prices) {
        this.floors = floors;
        this.amenities = amenities;
        this.roomTypes = roomTypes;
        this.images = images;
        this.rooms = rooms;
        this.statusHistory = statusHistory;
        this.maintenanceBlocks = maintenanceBlocks;
        this.holds = holds;
        this.outbox = outbox;
        this.entityManager = entityManager;
        this.destinations = destinations;
        this.prices = prices;
    }

    private Map<String, Destination> loadDestinations() {
        if (destinations == null) return Map.of();
        return destinations.findAll().stream()
                .collect(Collectors.toMap(Destination::getCode, Function.identity(), (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public List<DestinationView> listDestinations(LocalDate requestedCheckIn, LocalDate requestedCheckOut) {
        return listDestinationsInternal(requestedCheckIn, requestedCheckOut, false);
    }

    @Transactional(readOnly = true)
    public List<DestinationView> listAllDestinationsForAdmin() {
        return listDestinationsInternal(null, null, true);
    }

    private List<DestinationView> listDestinationsInternal(
            LocalDate requestedCheckIn, LocalDate requestedCheckOut, boolean includeInactive) {
        if (destinations == null) return List.of();
        LocalDate checkIn = requestedCheckIn != null && !requestedCheckIn.isBefore(LocalDate.now())
                ? requestedCheckIn
                : LocalDate.now().plusDays(1);
        LocalDate checkOut = requestedCheckOut != null && requestedCheckOut.isAfter(checkIn)
                ? requestedCheckOut
                : checkIn.plusDays(2);
        Instant now = Instant.now();
        List<RoomType> sourceTypes = includeInactive ? roomTypes.findAllDetailed() : roomTypes.findPublishedDetailed();
        Map<UUID, RoomTypePriceSnapshot> priceByType = prices == null
                ? Map.of()
                : prices.findAllById(sourceTypes.stream().map(RoomType::getId).toList()).stream()
                        .collect(Collectors.toMap(RoomTypePriceSnapshot::getRoomTypeId, Function.identity()));

        List<Destination> sourceDestinations = includeInactive
                ? destinations.findAllByOrderByDisplayOrderAscNameAsc()
                : destinations.findByActiveTrueOrderByDisplayOrderAsc();

        return sourceDestinations.stream()
                .map(dest -> {
                    List<RoomType> matching = sourceTypes.stream()
                            .filter(t -> dest.getCode().equalsIgnoreCase(t.getDestinationCode()))
                            .toList();
                    int availableTotal = 0;
                    for (RoomType type : matching) {
                        long total = rooms.countActiveByRoomType(type.getId());
                        long maint = maintenanceBlocks.countUnavailableRooms(type.getId(), checkIn, checkOut);
                        long committed = holds.sumCommittedQuantity(type.getId(), checkIn, checkOut, now);
                        availableTotal += (int) Math.max(0, total - maint - committed);
                    }
                    BigDecimal minPrice = matching.stream()
                            .map(t -> priceByType.get(t.getId()))
                            .filter(Objects::nonNull)
                            .map(RoomTypePriceSnapshot::getNightlyPrice)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    return new DestinationView(
                            dest.getCode(),
                            dest.getName(),
                            dest.getProvince(),
                            dest.getRegion(),
                            dest.getRegionLabel(),
                            dest.getStyleTag(),
                            dest.getPropertyName(),
                            dest.getLandmark(),
                            dest.getDescription(),
                            dest.getDisplayOrder(),
                            dest.isActive(),
                            matching.size(),
                            availableTotal,
                            minPrice);
                })
                .toList();
    }

    @Transactional
    public DestinationView upsertDestination(String code, DestinationUpsertRequest request) {
        if (destinations == null) {
            throw ApiException.conflict("destinations-unavailable", "Danh mục điểm đến chưa sẵn sàng.");
        }
        String normalizedCode = (code != null && !code.isBlank() ? code : request.code()).trim().toUpperCase();
        String region = request.region().trim().toUpperCase();
        String regionLabel = request.regionLabel() != null && !request.regionLabel().isBlank()
                ? request.regionLabel().trim()
                : switch (region) {
                    case "BAC" -> "Miền Bắc";
                    case "TRUNG" -> "Miền Trung";
                    default -> "Miền Nam";
                };
        Destination dest = destinations.findById(normalizedCode).orElse(null);
        if (dest == null) {
            dest = destinations.save(new Destination(
                    normalizedCode,
                    request.name(),
                    request.province(),
                    region,
                    regionLabel,
                    request.styleTag(),
                    request.propertyName(),
                    request.landmark(),
                    request.description(),
                    request.displayOrder(),
                    request.active()));
        } else {
            dest.update(
                    request.name(),
                    request.province(),
                    region,
                    regionLabel,
                    request.styleTag(),
                    request.propertyName(),
                    request.landmark(),
                    request.description(),
                    request.displayOrder(),
                    request.active());
            entityManager.flush();
        }
        Destination saved = dest;
        return listAllDestinationsForAdmin().stream()
                .filter(d -> d.code().equalsIgnoreCase(saved.getCode()))
                .findFirst()
                .orElse(new DestinationView(
                        saved.getCode(), saved.getName(), saved.getProvince(), saved.getRegion(),
                        saved.getRegionLabel(), saved.getStyleTag(), saved.getPropertyName(),
                        saved.getLandmark(), saved.getDescription(), saved.getDisplayOrder(),
                        saved.isActive(), 0, 0, null));
    }

    @Transactional(readOnly = true)
    public List<FloorView> listFloors(boolean includeInactive) {
        return floors.findAllByOrderByLevelNumberAsc().stream()
                .filter(floor -> includeInactive || floor.isActive())
                .map(CatalogApplicationService::view)
                .toList();
    }

    @Transactional
    public FloorView createFloor(FloorCreateRequest request) {
        Floor floor = floors.save(new Floor(request.code(), request.name(), request.levelNumber()));
        outbox.emit("Floor", floor.getId(), "inventory.floor.created.v1", floorPayload(floor));
        return view(floor);
    }

    @Transactional
    public FloorView updateFloor(UUID id, FloorUpdateRequest request) {
        Floor floor = floor(id);
        verifyVersion(floor.getVersion(), request.expectedVersion());
        if (floor.isActive() && !request.active() && rooms.countByFloorIdAndActiveTrue(id) > 0) {
            throw ApiException.conflict(
                    "floor-has-active-rooms", "Không thể ngừng tầng khi vẫn còn phòng đang hoạt động.");
        }
        if (floors.countByIdNotAndLevelNumber(id, request.levelNumber()) > 0) {
            throw ApiException.conflict("duplicate-floor-level", "Số tầng đã được sử dụng.");
        }
        floor.update(request.code(), request.name(), request.levelNumber(), request.active());
        outbox.emit("Floor", floor.getId(), "inventory.floor.updated.v1", floorPayload(floor));
        entityManager.flush();
        return view(floor);
    }

    @Transactional(readOnly = true)
    public List<AmenityView> listAmenities(boolean includeInactive) {
        List<Amenity> source = includeInactive
                ? amenities.findAllByOrderByNameAsc()
                : amenities.findByActiveTrueOrderByNameAsc();
        return source.stream().map(CatalogApplicationService::view).toList();
    }

    @Transactional
    public AmenityView createAmenity(AmenityCreateRequest request) {
        Amenity amenity = amenities.save(new Amenity(
                request.code(), request.name(), request.description(), request.iconUrl()));
        outbox.emit("Amenity", amenity.getId(), "inventory.amenity.created.v1", amenityPayload(amenity));
        return view(amenity);
    }

    @Transactional
    public AmenityView updateAmenity(UUID id, AmenityUpdateRequest request) {
        Amenity amenity = amenity(id);
        verifyVersion(amenity.getVersion(), request.expectedVersion());
        amenity.update(request.code(), request.name(), request.description(), request.iconUrl(), request.active());
        outbox.emit("Amenity", amenity.getId(), "inventory.amenity.updated.v1", amenityPayload(amenity));
        entityManager.flush();
        return view(amenity);
    }

    @Transactional(readOnly = true)
    public List<RoomTypeView> listRoomTypes(boolean admin) {
        Map<String, Destination> destinationByCode = loadDestinations();
        return (admin ? roomTypes.findAllDetailed() : roomTypes.findPublishedDetailed()).stream()
                .map(type -> view(type, destinationByCode.get(type.getDestinationCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeView getRoomType(UUID id, boolean admin) {
        RoomType type = detailedRoomType(id);
        if (!admin && (!type.isActive() || !type.isPublished())) {
            throw ApiException.notFound("Không tìm thấy loại phòng đang công bố.");
        }
        Destination dest = destinations == null ? null : destinations.findById(type.getDestinationCode()).orElse(null);
        return view(type, dest);
    }

    @Transactional
    public RoomTypeView createRoomType(RoomTypeCreateRequest request) {
        validateCapacity(request.maxAdults(), request.maxChildren(), request.maxOccupancy());
        Set<Amenity> selectedAmenities = activeAmenities(request.amenityIds());
        RoomType type = roomTypes.save(new RoomType(
                request.code(), request.name(), request.description(), request.maxAdults(),
                request.maxChildren(), request.maxOccupancy(), request.sizeSqm(),
                request.bedDescription(), request.published(), selectedAmenities,
                request.destinationCode()));
        outbox.emit("RoomType", type.getId(), "inventory.room-type.created.v1", roomTypePayload(type));
        Destination dest = destinations == null ? null : destinations.findById(type.getDestinationCode()).orElse(null);
        return view(type, dest);
    }

    @Transactional
    public RoomTypeView updateRoomType(UUID id, RoomTypeUpdateRequest request) {
        validateCapacity(request.maxAdults(), request.maxChildren(), request.maxOccupancy());
        RoomType type = detailedRoomType(id);
        verifyVersion(type.getVersion(), request.expectedVersion());
        if (!request.active() && request.published()) {
            throw ApiException.badRequest(
                    "inactive-type-published", "Loại phòng ngừng hoạt động không thể được công bố.");
        }
        if (type.isActive() && !request.active()
                && holds.hasFutureCommitments(id, LocalDate.now(), Instant.now())) {
            throw ApiException.conflict(
                    "room-type-has-future-reservations",
                    "Loại phòng có giữ chỗ/đặt phòng tương lai; không thể ngừng hoạt động.");
        }
        type.update(
                request.code(), request.name(), request.description(), request.maxAdults(),
                request.maxChildren(), request.maxOccupancy(), request.sizeSqm(),
                request.bedDescription(), request.published(),
                request.active(), activeAmenities(request.amenityIds()),
                request.destinationCode());
        entityManager.flush();
        outbox.emit("RoomType", type.getId(), "inventory.room-type.updated.v1", roomTypePayload(type));
        Destination dest = destinations == null ? null : destinations.findById(type.getDestinationCode()).orElse(null);
        return view(type, dest);
    }

    @Transactional
    public RoomImageView addImage(UUID roomTypeId, RoomImageCreateRequest request) {
        RoomType type = detailedRoomType(roomTypeId);
        if (!type.isActive()) {
            throw ApiException.conflict("inactive-room-type", "Không thể thêm ảnh cho loại phòng đã ngừng.");
        }
        RoomImage image = new RoomImage(
                type, request.url(), request.altText(), request.mediaType(), request.byteSize(),
                request.displayOrder());
        type.attachImage(image);
        // RoomType is the aggregate owner and cascades persistence to its images.
        // Persisting the child separately and cascading it again can make Hibernate
        // treat the same new entity as detached during the transaction flush.
        entityManager.flush();
        outbox.emit("RoomType", type.getId(), "inventory.room-type.image-added.v1", imagePayload(image));
        return view(image);
    }

    @Transactional
    public RoomImageView updateImage(UUID id, RoomImageUpdateRequest request) {
        RoomImage image = images.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ảnh phòng."));
        verifyVersion(image.getVersion(), request.expectedVersion());
        image.update(
                request.url(), request.altText(), request.mediaType(), request.byteSize(),
                request.displayOrder(), request.active());
        outbox.emit(
                "RoomType", image.getRoomTypeId(), "inventory.room-type.image-updated.v1",
                imagePayload(image));
        entityManager.flush();
        return view(image);
    }

    @Transactional(readOnly = true)
    public List<RoomImageView> listImages(UUID roomTypeId, boolean admin) {
        if (!roomTypes.existsById(roomTypeId)) {
            throw ApiException.notFound("Không tìm thấy loại phòng.");
        }
        return images.findByRoomTypeIdOrderByDisplayOrderAscIdAsc(roomTypeId).stream()
                .filter(image -> admin || image.isActive())
                .map(CatalogApplicationService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomView> listRooms() {
        Map<String, Destination> destinationByCode = loadDestinations();
        return rooms.findAllDetailed().stream()
                .map(room -> view(room, destinationByCode.get(room.getRoomType().getDestinationCode())))
                .toList();
    }

    @Transactional
    public RoomView createRoom(RoomCreateRequest request) {
        Floor floor = activeFloor(request.floorId());
        RoomType type = activeRoomType(request.roomTypeId());
        Room room = rooms.save(new Room(request.roomNumber(), floor, type, request.note()));
        outbox.emit("Room", room.getId(), "inventory.room.created.v1", roomPayload(room));
        Destination dest = destinations == null ? null : destinations.findById(type.getDestinationCode()).orElse(null);
        return view(room, dest);
    }

    @Transactional
    public RoomView updateRoom(UUID id, RoomUpdateRequest request) {
        Room room = room(id);
        verifyVersion(room.getVersion(), request.expectedVersion());
        boolean reducesOldTypeCapacity = room.isActive()
                && (!request.active() || !room.getRoomType().getId().equals(request.roomTypeId()));
        if (reducesOldTypeCapacity && holds.hasFutureCommitments(
                room.getRoomType().getId(), LocalDate.now(), Instant.now())) {
            throw ApiException.conflict(
                    "room-has-future-reservations",
                    "Thay đổi làm giảm tồn của loại phòng đang có đặt phòng tương lai.");
        }
        if (!request.active() && Set.of(RoomStatus.RESERVED, RoomStatus.OCCUPIED)
                .contains(room.getOperationalStatus())) {
            throw ApiException.conflict(
                    "room-in-use", "Không thể ngừng phòng đang được giữ hoặc có khách.");
        }
        RoomType targetType = activeRoomType(request.roomTypeId());
        room.update(
                request.roomNumber(), activeFloor(request.floorId()),
                targetType, request.note(), request.active());
        outbox.emit("Room", room.getId(), "inventory.room.updated.v1", roomPayload(room));
        entityManager.flush();
        Destination dest = destinations == null ? null : destinations.findById(targetType.getDestinationCode()).orElse(null);
        return view(room, dest);
    }

    @Transactional
    public RoomView changeStatus(
            UUID id, RoomStatusChangeRequest request, String source, UUID actorId) {
        Room room = rooms.findForStatusChange(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng."));
        verifyVersion(room.getVersion(), request.expectedVersion());
        if (!room.isActive()) {
            throw ApiException.conflict("inactive-room", "Phòng đã ngừng hoạt động.");
        }
        if (room.getCurrentStayId() != null && request.status() != RoomStatus.OCCUPIED) {
            throw ApiException.conflict(
                    "room-owned-by-stay",
                    "Phòng đang thuộc một hồ sơ lưu trú; phải trả phòng qua Stay Service.");
        }
        RoomStatus previous = room.getOperationalStatus();
        Destination dest = destinations == null || room.getRoomType() == null
                ? null
                : destinations.findById(room.getRoomType().getDestinationCode()).orElse(null);
        if (!room.transitionTo(request.status())) return view(room, dest);

        UUID correlationId = RequestContext.correlationId();
        statusHistory.save(new RoomStatusHistory(
                room, previous, request.status(), request.reason(), actorId, source, correlationId));
        synchronizeMaintenance(room, previous, request.status(), request.reason());
        outbox.emit("Room", room.getId(), "inventory.room.status-changed.v1", Map.of(
                "roomId", room.getId(),
                "roomNumber", room.getRoomNumber(),
                "roomTypeId", room.getRoomType().getId(),
                "previousStatus", previous,
                "newStatus", request.status(),
                "reason", request.reason(),
                "changedBy", actorId == null ? "system" : actorId.toString(),
                "source", source,
                "version", room.getVersion() + 1));
        entityManager.flush();
        return view(room, dest);
    }

    @Transactional
    public RoomOccupancyView occupy(UUID id, RoomOccupancyRequest request) {
        Room room = rooms.findForStatusChange(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng."));
        verifyOptionalVersion(room.getVersion(), request.expectedVersion());
        if (!room.isActive()) {
            throw ApiException.conflict("inactive-room", "Phòng đã ngừng hoạt động.");
        }
        RoomStatus previous = room.getOperationalStatus();
        boolean changed = room.occupy(request.stayId());
        if (changed) {
            recordStayStatusChange(room, previous, RoomStatus.OCCUPIED, request.reason(), request.stayId());
        }
        entityManager.flush();
        return occupancyView(room);
    }

    @Transactional
    public RoomOccupancyView releaseOccupancy(UUID id, RoomOccupancyRequest request) {
        Room room = rooms.findForStatusChange(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng."));
        verifyOptionalVersion(room.getVersion(), request.expectedVersion());
        RoomStatus previous = room.getOperationalStatus();
        boolean changed = room.releaseFromStay(request.stayId());
        if (changed) {
            recordStayStatusChange(
                    room, previous, RoomStatus.WAITING_CLEANING, request.reason(), request.stayId());
        }
        entityManager.flush();
        return occupancyView(room);
    }

    private void recordStayStatusChange(
            Room room, RoomStatus previous, RoomStatus next, String reason, UUID stayId) {
        UUID correlationId = RequestContext.correlationId();
        statusHistory.save(new RoomStatusHistory(
                room, previous, next, reason, null, "stay-service", correlationId));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("roomNumber", room.getRoomNumber());
        payload.put("roomTypeId", room.getRoomType().getId());
        payload.put("previousStatus", previous);
        payload.put("newStatus", next);
        payload.put("reason", reason);
        payload.put("stayId", stayId);
        payload.put("source", "stay-service");
        payload.put("version", room.getVersion() + 1);
        outbox.emit("Room", room.getId(), "inventory.room.status-changed.v1", payload);
    }

    private static RoomOccupancyView occupancyView(Room room) {
        return new RoomOccupancyView(
                room.getId(), room.getRoomNumber(), room.getRoomType().getId(),
                room.getOperationalStatus(), room.getCurrentStayId(), room.getVersion());
    }

    @Transactional(readOnly = true)
    public List<RoomStatusHistoryView> statusHistory(UUID roomId) {
        room(roomId);
        return statusHistory.findTop100ByRoomIdOrderByChangedAtDesc(roomId).stream()
                .map(history -> new RoomStatusHistoryView(
                        history.getId(), history.getPreviousStatus(), history.getNewStatus(),
                        history.getReason(), history.getChangedBy(), history.getSource(),
                        history.getCorrelationId(), history.getChangedAt()))
                .toList();
    }

    private void synchronizeMaintenance(
            Room room, RoomStatus previous, RoomStatus next, String reason) {
        if (next == RoomStatus.MAINTENANCE && previous != RoomStatus.MAINTENANCE) {
            maintenanceBlocks.save(new MaintenanceBlock(room, LocalDate.now(), reason));
        } else if (previous == RoomStatus.MAINTENANCE && next != RoomStatus.MAINTENANCE) {
            maintenanceBlocks.findFirstByRoomIdAndStatusOrderByCreatedAtDesc(room.getId(), "OPEN")
                    .ifPresent(block -> block.resolve(LocalDate.now()));
        }
    }

    private Floor floor(UUID id) {
        return floors.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tầng."));
    }

    private Floor activeFloor(UUID id) {
        Floor floor = floor(id);
        if (!floor.isActive()) throw ApiException.conflict("inactive-floor", "Tầng đã ngừng hoạt động.");
        return floor;
    }

    private Amenity amenity(UUID id) {
        return amenities.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tiện nghi."));
    }

    private RoomType detailedRoomType(UUID id) {
        return roomTypes.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại phòng."));
    }

    private RoomType activeRoomType(UUID id) {
        RoomType type = roomTypes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại phòng."));
        if (!type.isActive()) {
            throw ApiException.conflict("inactive-room-type", "Loại phòng đã ngừng hoạt động.");
        }
        return type;
    }

    private Room room(UUID id) {
        return rooms.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng."));
    }

    private Set<Amenity> activeAmenities(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        List<Amenity> selected = amenities.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw ApiException.badRequest("unknown-amenity", "Có tiện nghi không tồn tại.");
        }
        if (selected.stream().anyMatch(amenity -> !amenity.isActive())) {
            throw ApiException.conflict("inactive-amenity", "Không thể gắn tiện nghi đã ngừng hoạt động.");
        }
        return new LinkedHashSet<>(selected);
    }

    private static void validateCapacity(int adults, int children, int occupancy) {
        if (occupancy < adults || occupancy > adults + children) {
            throw ApiException.badRequest(
                    "invalid-capacity",
                    "Sức chứa tối đa phải không nhỏ hơn số người lớn và không vượt tổng giới hạn đã khai báo.");
        }
    }

    private static void verifyVersion(long current, long expected) {
        if (current != expected) {
            throw ApiException.conflict(
                    "stale-version", "Dữ liệu đã thay đổi; phiên bản hiện tại là " + current + ".");
        }
    }

    private static void verifyOptionalVersion(long current, Long expected) {
        if (expected != null) verifyVersion(current, expected);
    }

    public static FloorView view(Floor floor) {
        return new FloorView(
                floor.getId(), floor.getCode(), floor.getName(), floor.getLevelNumber(),
                floor.isActive(), floor.getVersion());
    }

    public static AmenityView view(Amenity amenity) {
        return new AmenityView(
                amenity.getId(), amenity.getCode(), amenity.getName(), amenity.getDescription(),
                amenity.getIconUrl(), amenity.isActive(), amenity.getVersion());
    }

    public static RoomImageView view(RoomImage image) {
        return new RoomImageView(
                image.getId(), image.getRoomTypeId(), image.getUrl(), image.getAltText(),
                image.getMediaType(), image.getByteSize(), image.getDisplayOrder(), image.isActive(),
                image.getVersion());
    }

    public static RoomTypeView view(RoomType type) {
        return view(type, null);
    }

    public static RoomTypeView view(RoomType type, Destination dest) {
        return new RoomTypeView(
                type.getId(), type.getCode(), type.getName(), type.getDescription(),
                type.getMaxAdults(), type.getMaxChildren(), type.getMaxOccupancy(),
                type.getSizeSqm(), type.getBedDescription(), type.getPolicySummary(),
                type.isPublished(), type.isActive(),
                type.getAmenities().stream()
                        .sorted(Comparator.comparing(Amenity::getName).thenComparing(Amenity::getId))
                        .map(CatalogApplicationService::view).toList(),
                type.activeImages().stream().map(CatalogApplicationService::view).toList(),
                type.getVersion(),
                type.getDestinationCode(),
                dest != null ? dest.getName() : "Phú Quốc",
                dest != null ? dest.getProvince() : "Kiên Giang",
                dest != null ? dest.getRegion() : "NAM",
                dest != null ? dest.getRegionLabel() : "Miền Nam",
                dest != null ? dest.getStyleTag() : "Biển đảo",
                dest != null ? dest.getPropertyName() : "HotelAI Phú Quốc",
                dest != null ? dest.getLandmark() : "Bãi Kem");
    }

    public static RoomView view(Room room) {
        return view(room, null);
    }

    public static RoomView view(Room room, Destination dest) {
        String destCode = room.getRoomType() != null && room.getRoomType().getDestinationCode() != null
                ? room.getRoomType().getDestinationCode()
                : "PHU_QUOC";
        return new RoomView(
                room.getId(), room.getRoomNumber(), view(room.getFloor()),
                room.getRoomType().getId(), room.getRoomType().getCode(), room.getRoomType().getName(),
                room.getOperationalStatus(), room.isActive(), room.getNote(), room.getVersion(),
                destCode,
                dest != null ? dest.getName() : "Phú Quốc",
                dest != null ? dest.getPropertyName() : "HotelAI Phú Quốc");
    }

    private static Map<String, ?> floorPayload(Floor floor) {
        return Map.of(
                "floorId", floor.getId(), "code", floor.getCode(), "name", floor.getName(),
                "levelNumber", floor.getLevelNumber(), "active", floor.isActive());
    }

    private static Map<String, ?> amenityPayload(Amenity amenity) {
        return Map.of(
                "amenityId", amenity.getId(), "code", amenity.getCode(), "name", amenity.getName(),
                "active", amenity.isActive());
    }

    private static Map<String, ?> roomTypePayload(RoomType type) {
        return Map.ofEntries(
                Map.entry("roomTypeId", type.getId()), Map.entry("code", type.getCode()),
                Map.entry("name", type.getName()), Map.entry("maxAdults", type.getMaxAdults()),
                Map.entry("maxChildren", type.getMaxChildren()),
                Map.entry("maxOccupancy", type.getMaxOccupancy()),
                Map.entry("amenityIds", type.getAmenities().stream().map(Amenity::getId).toList()),
                Map.entry("published", type.isPublished()), Map.entry("active", type.isActive()),
                Map.entry("version", type.getVersion()));
    }

    private static Map<String, ?> imagePayload(RoomImage image) {
        return Map.of(
                "imageId", image.getId(), "roomTypeId", image.getRoomTypeId(), "url", image.getUrl(),
                "altText", image.getAltText(), "mediaType", image.getMediaType(),
                "byteSize", image.getByteSize(), "displayOrder", image.getDisplayOrder(),
                "active", image.isActive());
    }

    private static Map<String, ?> roomPayload(Room room) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("roomNumber", room.getRoomNumber());
        payload.put("floorId", room.getFloor().getId());
        payload.put("roomTypeId", room.getRoomType().getId());
        payload.put("status", room.getOperationalStatus());
        payload.put("active", room.isActive());
        payload.put("currentStayId", room.getCurrentStayId());
        payload.put("version", room.getVersion());
        return payload;
    }
}
