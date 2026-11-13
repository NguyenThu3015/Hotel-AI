package com.hotelai.backend.operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OperationController {
  private final OperationService service;

  @PostMapping("/operations/bookings/{id}/check-in")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  com.hotelai.backend.booking.BookingService.View checkIn(
      @PathVariable Long id, @Valid @RequestBody CheckIn r) {
    return service.checkIn(id, r.roomIds);
  }

  @PostMapping("/operations/bookings/{id}/check-out")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  OperationService.Checkout checkOut(@PathVariable Long id) {
    return service.checkOut(id);
  }

  @PostMapping("/operations/bookings/{id}/no-show")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  com.hotelai.backend.booking.BookingService.View noShow(@PathVariable Long id) {
    return service.noShow(id);
  }

  @PostMapping("/operations/bookings/{id}/services")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  OperationService.UsageView usage(
      @PathVariable Long id, Principal p, @Valid @RequestBody Usage r) {
    return service.useService(id, r.serviceId, r.quantity, r.note, p);
  }

  @PostMapping("/bookings/{id}/services")
  OperationService.UsageView prebookService(
      @PathVariable Long id, Principal principal, @Valid @RequestBody Usage request) {
    return service.prebookService(id, request.serviceId, request.quantity, request.note, principal);
  }

  @GetMapping("/catalog/hotels/{id}/services")
  List<ServiceView> services(@PathVariable Long id) {
    return service.services(id).stream().map(ServiceView::of).toList();
  }

  @PostMapping("/admin/services")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  ServiceView createService(@Valid @RequestBody ServiceRequest r) {
    return ServiceView.of(
        service.saveService(null, r.hotelId, r.code, r.name, r.unit, r.price, r.active));
  }

  @GetMapping("/housekeeping/tasks")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HOUSEKEEPER')")
  List<OperationService.TaskView> tasks(Principal p) {
    return service.tasks(p);
  }

  @PatchMapping("/housekeeping/tasks/{id}/assign")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  OperationService.TaskView assign(@PathVariable Long id, @RequestBody Assign r) {
    return service.assignTask(id, r.userId);
  }

  @PatchMapping("/housekeeping/tasks/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HOUSEKEEPER')")
  OperationService.TaskView taskStatus(@PathVariable Long id, @Valid @RequestBody TaskStatus r) {
    return service.taskStatus(id, r.status);
  }

  @PostMapping("/maintenance")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','HOUSEKEEPER')")
  MaintenanceView maintenance(Principal p, @Valid @RequestBody Maintenance r) {
    return MaintenanceView.of(service.report(r.roomId, r.title, r.description, r.severity, p));
  }

  @GetMapping("/maintenance")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','HOUSEKEEPER')")
  List<MaintenanceView> maintenance(@RequestParam(defaultValue = "false") boolean includeResolved) {
    return service.maintenance(includeResolved).stream().map(MaintenanceView::of).toList();
  }

  @PostMapping("/maintenance/{id}/resolve")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  MaintenanceView resolve(@PathVariable Long id, @RequestBody Resolve r) {
    return MaintenanceView.of(service.resolve(id, r.note));
  }

  record CheckIn(@NotEmpty List<Long> roomIds) {}

  record Usage(@NotNull Long serviceId, @Positive BigDecimal quantity, String note) {}

  record ServiceRequest(
      @NotNull Long hotelId,
      @NotBlank String code,
      @NotBlank String name,
      @NotBlank String unit,
      @PositiveOrZero BigDecimal price,
      boolean active) {}

  record Assign(@NotNull Long userId) {}

  record TaskStatus(@Pattern(regexp = "IN_PROGRESS|COMPLETED") String status) {}

  record Maintenance(
      @NotNull Long roomId,
      @NotBlank String title,
      String description,
      @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity) {}

  record Resolve(@NotBlank String note) {}

  record ServiceView(
      Long id, Long hotelId, String code, String name, String unit, BigDecimal price) {
    static ServiceView of(ExtraService s) {
      return new ServiceView(
          s.getId(), s.getHotel().getId(), s.getCode(), s.getName(), s.getUnit(), s.getPrice());
    }
  }

  record MaintenanceView(
      Long id, Long roomId, String title, String severity, String status, String resolution) {
    static MaintenanceView of(MaintenanceRequest m) {
      return new MaintenanceView(
          m.getId(),
          m.getRoom().getId(),
          m.getTitle(),
          m.getSeverity(),
          m.getStatus(),
          m.getResolutionNote());
    }
  }
}
