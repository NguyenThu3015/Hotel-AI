package com.hotelai.inventory.api;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.domain.AvailabilityApplicationService;
import com.hotelai.inventory.domain.CatalogApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalInventoryController {
    private final AvailabilityApplicationService availability;
    private final CatalogApplicationService catalog;

    public InternalInventoryController(
            AvailabilityApplicationService availability, CatalogApplicationService catalog) {
        this.availability = availability;
        this.catalog = catalog;
    }

    @PostMapping("/availability/check")
    AvailabilityCheckResponse check(@Valid @RequestBody AvailabilityCheckRequest request) {
        return availability.check(request);
    }

    @PostMapping("/inventory/holds")
    ResponseEntity<HoldView> createHold(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateHoldRequest request) {
        HoldView created = availability.createHold(idempotencyKey, request);
        return ResponseEntity.created(URI.create("/internal/v1/inventory/holds/" + created.id()))
                .body(created);
    }

    @GetMapping("/inventory/holds/{id}")
    HoldView hold(@PathVariable UUID id) {
        return availability.getHold(id);
    }

    @PostMapping("/inventory/holds/{id}/confirm")
    HoldView confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmHoldRequest request) {
        return availability.confirmHold(id, request.reservationId());
    }

    @PostMapping("/inventory/holds/{id}/replace")
    HoldView replace(@PathVariable UUID id, @Valid @RequestBody ReplaceHoldRequest request) {
        return availability.replaceConfirmedHold(
                request.previousHoldId(), id, request.reservationId());
    }

    @DeleteMapping("/inventory/holds/{id}")
    HoldView release(@PathVariable UUID id) {
        return availability.releaseHold(id);
    }

    @PutMapping("/rooms/{id}/status")
    RoomView synchronizeStatus(
            @PathVariable UUID id, @Valid @RequestBody RoomStatusChangeRequest request) {
        return catalog.changeStatus(id, request, "service-api", null);
    }

    @PostMapping("/rooms/{id}/occupancy")
    RoomOccupancyView occupy(
            @PathVariable UUID id, @Valid @RequestBody RoomOccupancyRequest request) {
        return catalog.occupy(id, request);
    }

    @PostMapping("/rooms/{id}/occupancy/release")
    RoomOccupancyView releaseOccupancy(
            @PathVariable UUID id, @Valid @RequestBody RoomOccupancyRequest request) {
        return catalog.releaseOccupancy(id, request);
    }
}
