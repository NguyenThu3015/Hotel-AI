package com.hotelai.identity;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AdminService {
  private final AccountRepository accounts;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final IdempotencyRepository idempotency;
  private final PasswordEncoder passwords;
  private final AuthService auth;
  private final OutboxService outbox;
  private final IdentityProperties properties;
  private final SecureRandom random = new SecureRandom();

  @Transactional(readOnly = true)
  Page<StaffView> listStaff(Pageable pageable) {
    return accounts.findAllByType(AccountType.STAFF, pageable).map(this::view);
  }

  @Transactional(readOnly = true)
  StaffView getStaff(UUID id) {
    return view(staff(id));
  }

  @Transactional(noRollbackFor = ApiException.class)
  StaffView createStaff(
      String email,
      String fullName,
      String phone,
      Set<String> roleCodes,
      String idempotencyKey,
      UUID actorId) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    Set<String> normalizedRoles = normalizeCodes(roleCodes);
    String requestHash =
        Hashing.sha256(normalizedEmail + '|' + fullName + '|' + phone + '|' + normalizedRoles);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyRecord prior =
          idempotency.findByOperationAndIdempotencyKey("create-staff", idempotencyKey).orElse(null);
      if (prior != null) {
        if (!Hashing.constantTimeEquals(prior.getRequestHash(), requestHash)) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "idempotency_key_reused",
              "Idempotency-Key đã được dùng cho yêu cầu khác");
        }
        return view(staff(prior.getResourceId()));
      }
    }
    if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
      throw new ApiException(HttpStatus.CONFLICT, "email_already_used", "Email đã được sử dụng");
    }
    Set<Role> assigned = staffRoles(normalizedRoles);
    String unusableInitialPassword = randomToken() + "Aa1!";
    Account account =
        accounts.save(
            Account.staff(
                normalizedEmail,
                passwords.encode(unusableInitialPassword),
                fullName.trim(),
                phone == null || phone.isBlank() ? null : phone.trim(),
                properties.hotelId(),
                assigned));
    auth.issueChallenge(account, OtpPurpose.RESET_PASSWORD);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      idempotency.save(
          new IdempotencyRecord("create-staff", idempotencyKey, requestHash, account.getId()));
    }
    outbox.append(
        "Account",
        account.getId(),
        "identity.staff.created.v1",
        IdentityEventPayloads.staffSnapshot(account, actorId, properties.hotelId()));
    return view(account);
  }

  @Transactional
  StaffView updateStaff(UUID id, String fullName, String phone, UUID actorId) {
    Account account = staff(id);
    account.updateProfile(fullName.trim(), phone == null || phone.isBlank() ? null : phone.trim());
    outbox.append(
        "Account",
        account.getId(),
        "identity.staff.updated.v1",
        IdentityEventPayloads.staffSnapshot(account, actorId, properties.hotelId()));
    return view(account);
  }

  @Transactional(noRollbackFor = ApiException.class)
  StaffView changeStatus(UUID id, AccountStatus status, UUID actorId) {
    if (status != AccountStatus.ACTIVE && status != AccountStatus.LOCKED) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "invalid_status", "Chỉ có thể khóa hoặc mở khóa nhân viên");
    }
    Account target = staff(id);
    if (target.getId().equals(actorId) && status == AccountStatus.LOCKED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "self_lockout_prevented",
          "Không thể tự khóa tài khoản đang thao tác");
    }
    if (status == AccountStatus.LOCKED) ensureNotLastAdmin(target);
    target.changeStatus(status);
    if (status == AccountStatus.LOCKED) auth.revokeAllSessions(target.getId(), "ACCOUNT_LOCKED");
    outbox.append(
        "Account",
        target.getId(),
        "identity.staff.status-changed.v1",
        IdentityEventPayloads.staffSnapshot(target, actorId, properties.hotelId()));
    return view(target);
  }

  @Transactional(noRollbackFor = ApiException.class)
  StaffView replaceStaffRoles(UUID id, Set<String> roleCodes, UUID actorId) {
    Account target = staff(id);
    Set<Role> assigned = staffRoles(normalizeCodes(roleCodes));
    boolean retainsAdmin = assigned.stream().anyMatch(role -> role.getCode().equals("ADMIN"));
    if (target.hasRole("ADMIN") && !retainsAdmin) ensureNotLastAdmin(target);
    if (target.getId().equals(actorId)) {
      boolean retainsRoleManagement =
          assigned.stream()
              .flatMap(role -> role.getPermissions().stream())
              .anyMatch(permission -> permission.getCode().equals("roles:write"));
      if (!retainsRoleManagement) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "self_lockout_prevented",
            "Không thể tự xóa quyền quản lý phân quyền");
      }
    }
    target.replaceRoles(assigned);
    auth.revokeAllSessions(target.getId(), "ROLES_CHANGED");
    outbox.append(
        "Account",
        target.getId(),
        "identity.staff.roles-changed.v1",
        IdentityEventPayloads.staffSnapshot(target, actorId, properties.hotelId()));
    return view(target);
  }

  @Transactional
  void requestPasswordReset(UUID id, UUID actorId) {
    Account target = staff(id);
    auth.issueChallenge(target, OtpPurpose.RESET_PASSWORD);
    auth.revokeAllSessions(target.getId(), "ADMIN_PASSWORD_RESET");
    outbox.append(
        "Account",
        target.getId(),
        "audit.identity.staff-password-reset-requested.v1",
        Map.of(
            "accountId", target.getId(),
            "actorId", actorId,
            "hotelId", properties.hotelId(),
            "occurredAt", Instant.now()));
  }

  @Transactional(readOnly = true)
  java.util.List<RoleView> listRoles() {
    return roles.findAll().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  java.util.List<PermissionView> listPermissions() {
    return permissions.findAll().stream()
        .map(permission -> new PermissionView(permission.getCode(), permission.getDescription()))
        .toList();
  }

  @Transactional
  RoleView createRole(
      String code, String name, String description, Set<String> permissionCodes, UUID actorId) {
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    if (roles.existsByCode(normalized)) {
      throw new ApiException(HttpStatus.CONFLICT, "role_exists", "Mã vai trò đã tồn tại");
    }
    Role role =
        roles.save(
            new Role(
                normalized,
                name.trim(),
                description,
                permissions(normalizePermissionCodes(permissionCodes))));
    roleEvent("identity.role.created.v1", role, actorId);
    return view(role);
  }

  @Transactional(noRollbackFor = ApiException.class)
  RoleView updateRole(
      UUID id, String name, String description, Set<String> permissionCodes, UUID actorId) {
    Role role = role(id);
    if (role.isSystemRole()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "system_role_immutable", "Không thể thay đổi vai trò hệ thống");
    }
    Set<Permission> assigned = permissions(normalizePermissionCodes(permissionCodes));
    Account actor = staff(actorId);
    if (actor.getRoles().stream().anyMatch(value -> value.getId().equals(role.getId()))) {
      boolean hasRolesWriteElsewhere =
          actor.getRoles().stream()
              .filter(value -> !value.getId().equals(role.getId()))
              .flatMap(value -> value.getPermissions().stream())
              .anyMatch(permission -> permission.getCode().equals("roles:write"));
      boolean newRoleKeepsPermission =
          assigned.stream().anyMatch(permission -> permission.getCode().equals("roles:write"));
      if (!hasRolesWriteElsewhere && !newRoleKeepsPermission) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "self_lockout_prevented",
            "Không thể tự xóa quyền quản lý phân quyền");
      }
    }
    role.update(name.trim(), description, assigned);
    roleEvent("identity.role.updated.v1", role, actorId);
    return view(role);
  }

  @Transactional(noRollbackFor = ApiException.class)
  void deleteRole(UUID id, UUID actorId) {
    Role role = role(id);
    if (role.isSystemRole()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "system_role_immutable", "Không thể xóa vai trò hệ thống");
    }
    Account actor = staff(actorId);
    if (actor.getRoles().stream().anyMatch(value -> value.getId().equals(id))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "self_lockout_prevented",
          "Không thể xóa vai trò đang dùng bởi tài khoản hiện tại");
    }
    roleEvent("identity.role.deleted.v1", role, actorId);
    roles.delete(role);
    roles.flush();
  }

  private void roleEvent(String eventType, Role role, UUID actorId) {
    outbox.append(
        "Role",
        role.getId(),
        eventType,
        Map.of(
            "roleId", role.getId(),
            "code", role.getCode(),
            "actorId", actorId,
            "hotelId", properties.hotelId(),
            "occurredAt", Instant.now()));
  }

  private Set<Role> staffRoles(Set<String> codes) {
    if (codes.isEmpty() || codes.contains("CUSTOMER")) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_staff_roles",
          "Nhân viên phải có ít nhất một vai trò nhân viên");
    }
    java.util.List<Role> found = roles.findAllByCodeIn(codes);
    if (found.size() != codes.size()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_role", "Có vai trò không tồn tại");
    }
    return new HashSet<>(found);
  }

  private Set<Permission> permissions(Set<String> codes) {
    java.util.List<Permission> found = permissions.findAllByCodeIn(codes);
    if (found.size() != codes.size()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "unknown_permission", "Có quyền không tồn tại");
    }
    return new HashSet<>(found);
  }

  private Set<String> normalizeCodes(Set<String> codes) {
    if (codes == null) return Set.of();
    return codes.stream()
        .map(value -> value.trim().toUpperCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toSet());
  }

  private Set<String> normalizePermissionCodes(Set<String> codes) {
    if (codes == null) return Set.of();
    return codes.stream()
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toSet());
  }

  private void ensureNotLastAdmin(Account target) {
    if (target.hasRole("ADMIN") && accounts.countActiveAdmins(properties.hotelId()) <= 1) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "last_admin_protected",
          "Khách sạn phải luôn còn ít nhất một quản trị viên hoạt động");
    }
  }

  private Account staff(UUID id) {
    Account account =
        accounts
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "staff_not_found", "Không tìm thấy nhân viên"));
    if (account.getType() != AccountType.STAFF
        || !properties.hotelId().equals(account.getHotelId())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "staff_not_found", "Không tìm thấy nhân viên");
    }
    return account;
  }

  private Role role(UUID id) {
    return roles
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(HttpStatus.NOT_FOUND, "role_not_found", "Không tìm thấy vai trò"));
  }

  private String randomToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private StaffView view(Account account) {
    return new StaffView(
        account.getId(),
        account.getEmail(),
        account.getFullName(),
        account.getPhone(),
        account.getStatus(),
        account.getHotelId(),
        account.getRoles().stream().map(Role::getCode).sorted().toList(),
        account.getCreatedAt(),
        account.getLastLoginAt());
  }

  private RoleView view(Role role) {
    return new RoleView(
        role.getId(),
        role.getCode(),
        role.getName(),
        role.getDescription(),
        role.isSystemRole(),
        role.getPermissions().stream().map(Permission::getCode).sorted().toList());
  }

  record StaffView(
      UUID id,
      String email,
      String fullName,
      String phone,
      AccountStatus status,
      String hotelId,
      java.util.List<String> roles,
      Instant createdAt,
      Instant lastLoginAt) {}

  record RoleView(
      UUID id,
      String code,
      String name,
      String description,
      boolean systemRole,
      java.util.List<String> permissions) {}

  record PermissionView(String code, String description) {}
}
