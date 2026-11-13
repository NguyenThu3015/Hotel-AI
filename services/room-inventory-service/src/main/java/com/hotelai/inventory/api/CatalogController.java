package com.hotelai.inventory.api;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.domain.CatalogApplicationService;
import com.hotelai.inventory.domain.AvailabilityApplicationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogApplicationService catalog;
    private final AvailabilityApplicationService availability;

    public CatalogController(
            CatalogApplicationService catalog, AvailabilityApplicationService availability) {
        this.catalog = catalog;
        this.availability = availability;
    }

    @GetMapping("/destinations")
    List<DestinationView> destinations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return catalog.listDestinations(checkIn, checkOut);
    }

    @GetMapping("/floors")
    List<FloorView> floors() {
        return catalog.listFloors(false);
    }

    @GetMapping("/amenities")
    List<AmenityView> amenities() {
        return catalog.listAmenities(false);
    }

    @GetMapping("/room-types")
    List<RoomTypeView> roomTypes() {
        return catalog.listRoomTypes(false);
    }

    @GetMapping("/hotels/{ignoredSingleHotelId}/room-types")
    List<RoomTypeView> roomTypesForSingleHotel(@PathVariable String ignoredSingleHotelId) {
        return catalog.listRoomTypes(false);
    }

    @GetMapping("/room-types/{id}")
    RoomTypeView roomType(@PathVariable UUID id) {
        return catalog.getRoomType(id, false);
    }

    @GetMapping("/room-types/{id}/availability")
    AvailabilityResponse roomTypeAvailability(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam @Min(1) @Max(40) int adults,
            @RequestParam(defaultValue = "0") @Min(0) @Max(40) int children,
            @RequestParam(defaultValue = "1") @Min(1) @Max(20) int rooms) {
        catalog.getRoomType(id, false);
        return availability.search(new SearchCriteria(
                checkIn, checkOut, adults, children, rooms, Set.of(id), Set.of(),
                null, null, AvailabilitySort.NAME_ASC));
    }

    @GetMapping("/room-types/{id}/images")
    List<RoomImageView> images(@PathVariable UUID id) {
        return catalog.listImages(id, false);
    }
}
