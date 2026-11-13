package com.hotelai.inventory.api;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.common.RequestContext;
import com.hotelai.inventory.domain.CatalogApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminInventoryController {
    private final CatalogApplicationService catalog;

    public AdminInventoryController(CatalogApplicationService catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"/destinations", "/hotels/destinations"})
    List<DestinationView> destinations() {
        return catalog.listAllDestinationsForAdmin();
    }

    @PostMapping({"/destinations", "/hotels/destinations"})
    ResponseEntity<DestinationView> createDestination(@Valid @RequestBody DestinationUpsertRequest request) {
        DestinationView created = catalog.upsertDestination(request.code(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/destinations/" + created.code())).body(created);
    }

    @PutMapping({"/destinations/{code}", "/hotels/destinations/{code}"})
    DestinationView updateDestination(
            @PathVariable String code, @Valid @RequestBody DestinationUpsertRequest request) {
        return catalog.upsertDestination(code, request);
    }

    @GetMapping("/floors")
    List<FloorView> floors() {
        return catalog.listFloors(true);
    }

    @PostMapping("/floors")
    ResponseEntity<FloorView> createFloor(@Valid @RequestBody FloorCreateRequest request) {
        FloorView created = catalog.createFloor(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/floors/" + created.id())).body(created);
    }

    @PutMapping("/floors/{id}")
    FloorView updateFloor(@PathVariable UUID id, @Valid @RequestBody FloorUpdateRequest request) {
        return catalog.updateFloor(id, request);
    }

    @GetMapping("/amenities")
    List<AmenityView> amenities() {
        return catalog.listAmenities(true);
    }

    @PostMapping("/amenities")
    ResponseEntity<AmenityView> createAmenity(@Valid @RequestBody AmenityCreateRequest request) {
        AmenityView created = catalog.createAmenity(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/amenities/" + created.id())).body(created);
    }

    @PutMapping("/amenities/{id}")
    AmenityView updateAmenity(@PathVariable UUID id, @Valid @RequestBody AmenityUpdateRequest request) {
        return catalog.updateAmenity(id, request);
    }

    @GetMapping("/room-types")
    List<RoomTypeView> roomTypes() {
        return catalog.listRoomTypes(true);
    }

    @PostMapping("/room-types")
    ResponseEntity<RoomTypeView> createRoomType(@Valid @RequestBody RoomTypeCreateRequest request) {
        RoomTypeView created = catalog.createRoomType(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/room-types/" + created.id())).body(created);
    }

    @PutMapping("/room-types/{id}")
    RoomTypeView updateRoomType(@PathVariable UUID id, @Valid @RequestBody RoomTypeUpdateRequest request) {
        return catalog.updateRoomType(id, request);
    }

    @PostMapping("/room-types/{id}/images")
    ResponseEntity<RoomImageView> addImage(
            @PathVariable UUID id, @Valid @RequestBody RoomImageCreateRequest request) {
        RoomImageView created = catalog.addImage(id, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/images/" + created.id())).body(created);
    }

    @GetMapping("/room-types/{id}/images")
    List<RoomImageView> images(@PathVariable UUID id) {
        return catalog.listImages(id, true);
    }

    @PutMapping("/images/{id}")
    RoomImageView updateImage(@PathVariable UUID id, @Valid @RequestBody RoomImageUpdateRequest request) {
        return catalog.updateImage(id, request);
    }

    @GetMapping({"/rooms", "/hotels/{ignoredSingleHotelId}/rooms"})
    List<RoomView> rooms() {
        return catalog.listRooms();
    }

    @PostMapping("/rooms")
    ResponseEntity<RoomView> createRoom(@Valid @RequestBody RoomCreateRequest request) {
        RoomView created = catalog.createRoom(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/rooms/" + created.id())).body(created);
    }

    @PutMapping("/rooms/{id}")
    RoomView updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomUpdateRequest request) {
        return catalog.updateRoom(id, request);
    }

    @PatchMapping("/rooms/{id}/status")
    RoomView changeStatus(@PathVariable UUID id, @Valid @RequestBody RoomStatusChangeRequest request) {
        return catalog.changeStatus(id, request, "admin-api", RequestContext.actorId());
    }

    @GetMapping("/rooms/{id}/status-history")
    List<RoomStatusHistoryView> statusHistory(@PathVariable UUID id) {
        return catalog.statusHistory(id);
    }
}
