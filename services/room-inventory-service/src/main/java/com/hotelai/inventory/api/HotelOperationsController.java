package com.hotelai.inventory.api;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.domain.OperationsApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HotelOperationsController {
    private final OperationsApplicationService operations;

    public HotelOperationsController(OperationsApplicationService operations) {
        this.operations = operations;
    }

    @GetMapping("/api/v1/catalog/hotels")
    List<HotelProfileView> hotels() {
        return operations.publicHotels();
    }

    @PutMapping("/api/v1/admin/hotels/{id:\\d+}")
    HotelProfileView updateHotel(
            @PathVariable short id,
            @Valid @RequestBody HotelProfileUpdateRequest request) {
        return operations.updateHotel(id, request);
    }

    @GetMapping("/api/v1/housekeeping/tasks")
    List<HousekeepingTaskView> housekeepingTasks() {
        return operations.housekeepingTasks();
    }

    @PatchMapping("/api/v1/housekeeping/tasks/{taskId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    HousekeepingTaskView assign(
            @PathVariable UUID taskId,
            @Valid @RequestBody HousekeepingAssignmentRequest request) {
        return operations.assign(taskId, request);
    }

    @PatchMapping("/api/v1/housekeeping/tasks/{taskId}/status")
    HousekeepingTaskView taskStatus(
            @PathVariable UUID taskId,
            @Valid @RequestBody HousekeepingStatusRequest request) {
        return operations.changeTaskStatus(taskId, request);
    }

    @GetMapping("/api/v1/maintenance")
    List<MaintenanceView> maintenance() {
        return operations.maintenance();
    }

    @PostMapping("/api/v1/maintenance")
    MaintenanceView createMaintenance(
            @Valid @RequestBody MaintenanceCreateRequest request) {
        return operations.createMaintenance(request);
    }

    @PostMapping("/api/v1/maintenance/{maintenanceId}/resolve")
    MaintenanceView resolveMaintenance(
            @PathVariable UUID maintenanceId,
            @Valid @RequestBody MaintenanceResolveRequest request) {
        return operations.resolveMaintenance(maintenanceId, request);
    }
}
