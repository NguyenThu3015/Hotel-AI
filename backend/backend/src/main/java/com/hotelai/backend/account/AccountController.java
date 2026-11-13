package com.hotelai.backend.account;

import com.hotelai.backend.audit.AuditLogRepository;
import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {
  private final UserRepository users;
  private final RoleRepository roles;
  private final AuditService audit;
  private final AuditLogRepository logs;

  @GetMapping("/me")
  UserView me(Principal p) {
    return view(current(p));
  }

  @PutMapping("/me")
  UserView update(Principal p, @Valid @RequestBody Profile r) {
    User u = current(p);
    u.updateProfile(r.fullName, r.phone);
    users.save(u);
    audit.record("UPDATE_PROFILE", "USER", u.getId(), null);
    return view(u);
  }

  @GetMapping("/admin/users")
  @PreAuthorize("hasRole('ADMIN')")
  Page<UserView> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return users.findAll(PageRequest.of(page, Math.min(size, 100))).map(this::view);
  }

  @PatchMapping("/admin/users/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  UserView status(@PathVariable Long id, @Valid @RequestBody Status r) {
    User u =
        users
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    u.setStatus(r.status);
    audit.record("CHANGE_USER_STATUS", "USER", id, r.status);
    return view(u);
  }

  @PutMapping("/admin/users/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  UserView roles(@PathVariable Long id, @Valid @RequestBody Roles r) {
    User user =
        users
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    var assigned = new java.util.HashSet<>(roles.findAllById(r.roleIds));
    if (assigned.size() != r.roleIds.size())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Có vai trò không tồn tại");
    user.getRoles().clear();
    user.getRoles().addAll(assigned);
    users.save(user);
    audit.record(
        "CHANGE_USER_ROLES", "USER", id, assigned.stream().map(Role::getName).toList().toString());
    return view(user);
  }

  @GetMapping("/admin/roles")
  @PreAuthorize("hasRole('ADMIN')")
  java.util.List<RoleView> roles() {
    return roles.findAll().stream()
        .map(r -> new RoleView(r.getId(), r.getName(), r.getDescription()))
        .toList();
  }

  @GetMapping("/admin/audit-logs")
  @PreAuthorize("hasRole('ADMIN')")
  Page<Map<String, Object>> logs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return logs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)))
        .map(
            x ->
                Map.of(
                    "id",
                    x.getId(),
                    "action",
                    x.getAction(),
                    "entityType",
                    x.getEntityType(),
                    "entityId",
                    x.getEntityId() == null ? "" : x.getEntityId(),
                    "details",
                    x.getDetails() == null ? "" : x.getDetails(),
                    "createdAt",
                    x.getCreatedAt()));
  }

  private User current(Principal p) {
    return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
  }

  private UserView view(User u) {
    return new UserView(
        u.getId(),
        u.getEmail(),
        u.getFullName(),
        u.getPhone(),
        u.getStatus(),
        u.isEmailVerified(),
        u.getRoles().stream().map(Role::getName).toList());
  }

  record Profile(@NotBlank @Size(max = 150) String fullName, @Size(max = 20) String phone) {}

  record Status(@Pattern(regexp = "ACTIVE|LOCKED") String status) {}

  record Roles(@NotEmpty java.util.List<Long> roleIds) {}

  record RoleView(Long id, String name, String description) {}

  record UserView(
      Long id,
      String email,
      String fullName,
      String phone,
      String status,
      boolean emailVerified,
      java.util.List<String> roles) {}
}
