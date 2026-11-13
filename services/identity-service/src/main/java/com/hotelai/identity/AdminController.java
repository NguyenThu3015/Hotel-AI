package com.hotelai.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
class AdminController {
  private final AdminService admin;

  @GetMapping("/staff")
  @PreAuthorize("hasAuthority('staff:read')")
  Page<AdminService.StaffView> staff(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return admin.listStaff(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
  }

  @GetMapping("/staff/{id}")
  @PreAuthorize("hasAuthority('staff:read')")
  AdminService.StaffView staff(@PathVariable UUID id) {
    return admin.getStaff(id);
  }

  @PostMapping("/staff")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('staff:write')")
  AdminService.StaffView createStaff(
      @Valid @RequestBody StaffRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    return admin.createStaff(
        request.email(),
        request.fullName(),
        request.phone(),
        request.roles(),
        idempotencyKey,
        actor(jwt));
  }

  @PutMapping("/staff/{id}")
  @PreAuthorize("hasAuthority('staff:write')")
  AdminService.StaffView updateStaff(
      @PathVariable UUID id,
      @Valid @RequestBody StaffProfile request,
      @AuthenticationPrincipal Jwt jwt) {
    return admin.updateStaff(id, request.fullName(), request.phone(), actor(jwt));
  }

  @PatchMapping("/staff/{id}/status")
  @PreAuthorize("hasAuthority('staff:lock')")
  AdminService.StaffView status(
      @PathVariable UUID id,
      @Valid @RequestBody StatusRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return admin.changeStatus(id, request.status(), actor(jwt));
  }

  @PutMapping("/staff/{id}/roles")
  @PreAuthorize("hasAuthority('roles:write')")
  AdminService.StaffView roles(
      @PathVariable UUID id,
      @Valid @RequestBody RoleAssignment request,
      @AuthenticationPrincipal Jwt jwt) {
    return admin.replaceStaffRoles(id, request.roles(), actor(jwt));
  }

  @PostMapping("/staff/{id}/reset-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAuthority('staff:reset-password')")
  void resetPassword(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    admin.requestPasswordReset(id, actor(jwt));
  }

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('roles:read')")
  List<AdminService.RoleView> roles() {
    return admin.listRoles();
  }

  @GetMapping("/permissions")
  @PreAuthorize("hasAuthority('roles:read')")
  List<AdminService.PermissionView> permissions() {
    return admin.listPermissions();
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('roles:write')")
  AdminService.RoleView createRole(
      @Valid @RequestBody RoleRequest request, @AuthenticationPrincipal Jwt jwt) {
    return admin.createRole(
        request.code(), request.name(), request.description(), request.permissions(), actor(jwt));
  }

  @PutMapping("/roles/{id}")
  @PreAuthorize("hasAuthority('roles:write')")
  AdminService.RoleView updateRole(
      @PathVariable UUID id,
      @Valid @RequestBody RoleUpdate request,
      @AuthenticationPrincipal Jwt jwt) {
    return admin.updateRole(
        id, request.name(), request.description(), request.permissions(), actor(jwt));
  }

  @DeleteMapping("/roles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('roles:write')")
  void deleteRole(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    admin.deleteRole(id, actor(jwt));
  }

  private UUID actor(Jwt jwt) {
    return UUID.fromString(jwt.getClaimAsString("uid"));
  }

  record StaffRequest(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 150) String fullName,
      @Size(max = 30) String phone,
      @NotEmpty Set<@NotBlank String> roles) {}

  record StaffProfile(@NotBlank @Size(max = 150) String fullName, @Size(max = 30) String phone) {}

  record StatusRequest(@NotNull AccountStatus status) {}

  record RoleAssignment(@NotEmpty Set<@NotBlank String> roles) {}

  record RoleRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{2,49}") String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 255) String description,
      Set<@NotBlank String> permissions) {}

  record RoleUpdate(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 255) String description,
      Set<@NotBlank String> permissions) {}
}
