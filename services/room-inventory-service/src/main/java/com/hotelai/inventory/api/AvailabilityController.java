package com.hotelai.inventory.api;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.domain.AvailabilityApplicationService;
import com.hotelai.inventory.domain.CatalogApplicationService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {
    private final AvailabilityApplicationService availability;
    private final CatalogApplicationService catalog;

    public AvailabilityController(
            AvailabilityApplicationService availability, CatalogApplicationService catalog) {
        this.availability = availability;
        this.catalog = catalog;
    }

    @GetMapping("/destinations")
    List<DestinationView> destinations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return catalog.listDestinations(checkIn, checkOut);
    }

    @GetMapping("/search")
    AvailabilityResponse search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam @Min(1) @Max(40) int adults,
            @RequestParam(defaultValue = "0") @Min(0) @Max(40) int children,
            @RequestParam(defaultValue = "1") @Min(1) @Max(20) int rooms,
            @RequestParam(required = false) Set<UUID> roomTypeIds,
            @RequestParam(required = false) Set<UUID> amenityIds,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(defaultValue = "NAME_ASC") AvailabilitySort sort,
            @RequestParam(required = false) String destinationCode,
            @RequestParam(required = false) String region) {
        return availability.search(new SearchCriteria(
                checkIn, checkOut, adults, children, rooms, roomTypeIds, amenityIds,
                minPrice, maxPrice, sort, destinationCode, region));
    }
}
