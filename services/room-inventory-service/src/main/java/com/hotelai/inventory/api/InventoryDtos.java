package com.hotelai.inventory.api;

import com.hotelai.inventory.domain.RoomStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record FloorCreateRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @Min(-10) @Max(300) int levelNumber) {}

    public record FloorUpdateRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @Min(-10) @Max(300) int levelNumber,
            boolean active,
            @Min(0) long expectedVersion) {}

    public record FloorView(
            UUID id, String code, String name, int levelNumber, boolean active, long version) {}

    public record AmenityCreateRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Size(max = 1000) @Pattern(regexp = "^https?://.+$", message = "iconUrl phải là HTTP(S)") String iconUrl) {}

    public record AmenityUpdateRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Size(max = 1000) @Pattern(regexp = "^https?://.+$", message = "iconUrl phải là HTTP(S)") String iconUrl,
            boolean active,
            @Min(0) long expectedVersion) {}

    public record AmenityView(
            UUID id, String code, String name, String description, String iconUrl,
            boolean active, long version) {}

    public record DestinationView(
            String code, String name, String province, String region, String regionLabel,
            String styleTag, String propertyName, String landmark, String description,
            int displayOrder, boolean active,
            int roomTypeCount, int availableRooms, BigDecimal minNightlyPrice) {}

    public record DestinationUpsertRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String province,
            @NotBlank @Pattern(regexp = "BAC|TRUNG|NAM") String region,
            @Size(max = 60) String regionLabel,
            @NotBlank @Size(max = 80) String styleTag,
            @NotBlank @Size(max = 200) String propertyName,
            @NotBlank @Size(max = 200) String landmark,
            @NotBlank @Size(max = 1000) String description,
            @Min(0) @Max(1000) int displayOrder,
            boolean active) {}

    public record RoomTypeCreateRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 4000) String description,
            @Min(1) @Max(20) int maxAdults,
            @Min(0) @Max(20) int maxChildren,
            @Min(1) @Max(30) int maxOccupancy,
            @DecimalMin(value = "0.01") BigDecimal sizeSqm,
            @Size(max = 500) String bedDescription,
            boolean published,
            Set<UUID> amenityIds,
            @Size(max = 40) String destinationCode) {
        public RoomTypeCreateRequest(
                String code, String name, String description, int maxAdults, int maxChildren,
                int maxOccupancy, BigDecimal sizeSqm, String bedDescription, boolean published,
                Set<UUID> amenityIds) {
            this(code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                    bedDescription, published, amenityIds, "PHU_QUOC");
        }
    }

    public record RoomTypeUpdateRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 4000) String description,
            @Min(1) @Max(20) int maxAdults,
            @Min(0) @Max(20) int maxChildren,
            @Min(1) @Max(30) int maxOccupancy,
            @DecimalMin(value = "0.01") BigDecimal sizeSqm,
            @Size(max = 500) String bedDescription,
            boolean published,
            boolean active,
            Set<UUID> amenityIds,
            @Min(0) long expectedVersion,
            @Size(max = 40) String destinationCode) {
        public RoomTypeUpdateRequest(
                String code, String name, String description, int maxAdults, int maxChildren,
                int maxOccupancy, BigDecimal sizeSqm, String bedDescription, boolean published,
                boolean active, Set<UUID> amenityIds, long expectedVersion) {
            this(code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                    bedDescription, published, active, amenityIds, expectedVersion, null);
        }
    }

    public record RoomImageCreateRequest(
            @NotBlank @Size(max = 1000)
                    @Pattern(regexp = "^https?://.+$", message = "url phải là HTTP(S)") String url,
            @NotBlank @Size(max = 300) String altText,
            @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String mediaType,
            @Min(1) @Max(10_485_760) long byteSize,
            @Min(0) @Max(1000) int displayOrder) {}

    public record RoomImageUpdateRequest(
            @NotBlank @Size(max = 1000)
                    @Pattern(regexp = "^https?://.+$", message = "url phải là HTTP(S)") String url,
            @NotBlank @Size(max = 300) String altText,
            @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String mediaType,
            @Min(1) @Max(10_485_760) long byteSize,
            @Min(0) @Max(1000) int displayOrder,
            boolean active,
            @Min(0) long expectedVersion) {}

    public record RoomImageView(
            UUID id, UUID roomTypeId, String url, String altText, String mediaType,
            long byteSize, int displayOrder, boolean active, long version) {}

    public record RoomTypeView(
            UUID id, String code, String name, String description, int maxAdults,
            int maxChildren, int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
            String policySummary, boolean published, boolean active, List<AmenityView> amenities,
            List<RoomImageView> images, long version,
            String destinationCode, String destinationName, String province, String region,
            String regionLabel, String styleTag, String propertyName, String landmark) {
        public RoomTypeView(
                UUID id, String code, String name, String description, int maxAdults,
                int maxChildren, int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
                String policySummary, boolean published, boolean active, List<AmenityView> amenities,
                List<RoomImageView> images, long version) {
            this(id, code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                    bedDescription, policySummary, published, active, amenities, images, version,
                    "PHU_QUOC", "Phú Quốc", "Kiên Giang", "NAM", "Miền Nam", "Biển đảo",
                    "HotelAI Phú Quốc", "Bãi Kem");
        }
    }

    public record RoomCreateRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String roomNumber,
            @NotNull UUID floorId,
            @NotNull UUID roomTypeId,
            @Size(max = 1000) String note) {}

    public record RoomUpdateRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String roomNumber,
            @NotNull UUID floorId,
            @NotNull UUID roomTypeId,
            @Size(max = 1000) String note,
            boolean active,
            @Min(0) long expectedVersion) {}

    public record RoomStatusChangeRequest(
            @NotNull RoomStatus status,
            @NotBlank @Size(max = 500) String reason,
            @Min(0) long expectedVersion) {}

    public record RoomOccupancyRequest(
            @NotNull UUID stayId,
            @NotBlank @Size(max = 500) String reason,
            @Min(0) Long expectedVersion) {}

    public record RoomOccupancyView(
            UUID roomId, String roomNumber, UUID roomTypeId, RoomStatus status,
            UUID currentStayId, long version) {}

    public record RoomView(
            UUID id, String roomNumber, FloorView floor, UUID roomTypeId, String roomTypeCode,
            String roomTypeName, RoomStatus status, boolean active, String note, long version,
            String destinationCode, String destinationName, String propertyName) {
        public RoomView(
                UUID id, String roomNumber, FloorView floor, UUID roomTypeId, String roomTypeCode,
                String roomTypeName, RoomStatus status, boolean active, String note, long version) {
            this(id, roomNumber, floor, roomTypeId, roomTypeCode, roomTypeName, status, active,
                    note, version, "PHU_QUOC", "Phú Quốc", "HotelAI Phú Quốc");
        }
    }

    public record RoomStatusHistoryView(
            UUID id, RoomStatus previousStatus, RoomStatus newStatus, String reason,
            UUID changedBy, String source, UUID correlationId, Instant changedAt) {}

    public record HotelProfileUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address,
            @Size(max = 5000) String description,
            @Size(max = 30) String phone,
            @Size(max = 320) String email,
            boolean active,
            @Min(0) long expectedVersion) {}

    public record HotelProfileView(
            short id,
            String name,
            String address,
            String description,
            String phone,
            String email,
            boolean active,
            long version) {}

    public record HousekeepingAssignmentRequest(
            @NotNull UUID assignedTo,
            @Min(0) long expectedVersion) {}

    public record HousekeepingStatusRequest(
            @NotBlank @Pattern(regexp = "IN_PROGRESS|COMPLETED|CANCELLED") String status,
            @Min(0) long expectedVersion) {}

    public record HousekeepingTaskView(
            UUID id,
            UUID roomId,
            String roomNumber,
            UUID assignedTo,
            String status,
            String priority,
            String note,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            long version) {}

    public record MaintenanceCreateRequest(
            @NotNull UUID roomId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 5000) String description,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity) {}

    public record MaintenanceResolveRequest(
            @NotBlank @Size(max = 5000) String note,
            @Min(0) long expectedVersion) {}

    public record MaintenanceView(
            UUID id,
            UUID roomId,
            String roomNumber,
            String title,
            String description,
            String severity,
            String status,
            String resolution,
            UUID createdBy,
            LocalDate startDate,
            LocalDate endDate,
            Instant createdAt,
            Instant resolvedAt,
            long version) {}

    public enum AvailabilitySort {
        PRICE_ASC, PRICE_DESC, CAPACITY_ASC, CAPACITY_DESC, NAME_ASC
    }

    public record SearchCriteria(
            LocalDate checkIn, LocalDate checkOut, int adults, int children, int rooms,
            Set<UUID> roomTypeIds, Set<UUID> amenityIds, BigDecimal minPrice,
            BigDecimal maxPrice, AvailabilitySort sort,
            String destinationCode, String region) {
        public SearchCriteria(
                LocalDate checkIn, LocalDate checkOut, int adults, int children, int rooms,
                Set<UUID> roomTypeIds, Set<UUID> amenityIds, BigDecimal minPrice,
                BigDecimal maxPrice, AvailabilitySort sort) {
            this(checkIn, checkOut, adults, children, rooms, roomTypeIds, amenityIds,
                    minPrice, maxPrice, sort, null, null);
        }
    }

    public record PriceEstimate(
            String pricingStatus, BigDecimal nightlyPrice, String currency,
            BigDecimal subtotal, BigDecimal tax, BigDecimal serviceFee, BigDecimal total,
            long pricingVersion, Instant effectiveAt) {}

    public record AvailabilityItem(
            UUID roomTypeId, String code, String name, String description,
            int maxAdults, int maxChildren, int maxOccupancy, BigDecimal sizeSqm,
            String bedDescription, int availableRooms, PriceEstimate price,
            List<AmenityView> amenities, List<RoomImageView> images, String policySummary,
            String destinationCode, String destinationName, String province, String region,
            String regionLabel, String styleTag, String propertyName, String landmark) {
        public AvailabilityItem(
                UUID roomTypeId, String code, String name, String description,
                int maxAdults, int maxChildren, int maxOccupancy, BigDecimal sizeSqm,
                String bedDescription, int availableRooms, PriceEstimate price,
                List<AmenityView> amenities, List<RoomImageView> images, String policySummary) {
            this(roomTypeId, code, name, description, maxAdults, maxChildren, maxOccupancy,
                    sizeSqm, bedDescription, availableRooms, price, amenities, images, policySummary,
                    "PHU_QUOC", "Phú Quốc", "Kiên Giang", "NAM", "Miền Nam", "Biển đảo",
                    "HotelAI Phú Quốc", "Bãi Kem");
        }
    }

    public record AlternativeStay(LocalDate checkIn, LocalDate checkOut) {}

    public record AvailabilityResponse(
            SearchCriteria criteria, int resultCount, List<AvailabilityItem> content,
            AlternativeStay nearestAlternative, Instant refreshedAt) {}

    public record AvailabilityCheckRequest(
            @NotNull UUID roomTypeId,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) @Max(20) int quantity) {}

    public record AvailabilityCheckResponse(
            UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int requested,
            int available, boolean sufficient, Instant checkedAt) {}

    public record CreateHoldRequest(
            @NotNull UUID roomTypeId,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) @Max(20) int quantity,
            @Size(max = 120) String externalReference,
            @Min(1) @Max(30) Integer ttlMinutes) {}

    public record ConfirmHoldRequest(@NotNull UUID reservationId) {}

    public record ReplaceHoldRequest(
            @NotNull UUID previousHoldId,
            @NotNull UUID reservationId) {}

    public record HoldView(
            UUID id, UUID roomTypeId, UUID reservationId, String externalReference,
            LocalDate checkIn, LocalDate checkOut, int quantity, String status,
            Instant expiresAt, long version) {}
}
