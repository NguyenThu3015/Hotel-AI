package com.hotelai.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminServiceTest {
  @Test
  void publishesCompleteStaffSnapshotForLegacyProjection() {
    AccountRepository accounts = mock(AccountRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    PermissionRepository permissions = mock(PermissionRepository.class);
    IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    AuthService auth = mock(AuthService.class);
    OutboxService outbox = mock(OutboxService.class);
    IdentityProperties properties = new IdentityProperties("HOTEL-1", null, null);
    Role receptionist = new Role("RECEPTIONIST", "Receptionist", null, Set.of());
    when(roles.findAllByCodeIn(Set.of("RECEPTIONIST"))).thenReturn(java.util.List.of(receptionist));
    when(passwords.encode(anyString())).thenReturn("encoded");
    when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    AdminService service =
        new AdminService(
            accounts, roles, permissions, idempotency, passwords, auth, outbox, properties);
    UUID actorId = UUID.randomUUID();

    AdminService.StaffView created =
        service.createStaff(
            "staff@example.com", "Staff One", null, Set.of("RECEPTIONIST"), null, actorId);

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
    verify(outbox)
        .append(
            eq("Account"), eq(created.id()), eq("identity.staff.created.v1"), payload.capture());
    assertThat(payload.getValue())
        .containsEntry("identityId", created.id())
        .containsEntry("email", "staff@example.com")
        .containsEntry("fullName", "Staff One")
        .containsEntry("status", "ACTIVE")
        .containsEntry("emailVerified", true)
        .containsEntry("accountType", "STAFF")
        .containsEntry("hotelId", "HOTEL-1")
        .containsEntry("roles", java.util.List.of("RECEPTIONIST"))
        .containsEntry("actorId", actorId)
        .containsKey("phone");
    verify(auth).issueChallenge(any(Account.class), eq(OtpPurpose.RESET_PASSWORD));
  }

  @Test
  void preventsActorFromRemovingOwnRoleManagementPermission() {
    AccountRepository accounts = mock(AccountRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    PermissionRepository permissions = mock(PermissionRepository.class);
    IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
    AuthService auth = mock(AuthService.class);
    OutboxService outbox = mock(OutboxService.class);
    IdentityProperties properties = new IdentityProperties("HOTEL-1", null, null);
    Permission manageRoles = mock(Permission.class);
    when(manageRoles.getCode()).thenReturn("roles:write");
    Role currentAdmin = new Role("CUSTOM_ADMIN", "Custom admin", null, Set.of(manageRoles));
    Role receptionist = new Role("RECEPTIONIST", "Receptionist", null, Set.of());
    Account actor =
        Account.staff(
            "admin@example.com", "encoded", "Admin", null, "HOTEL-1", Set.of(currentAdmin));
    when(accounts.findById(actor.getId())).thenReturn(Optional.of(actor));
    when(roles.findAllByCodeIn(Set.of("RECEPTIONIST"))).thenReturn(java.util.List.of(receptionist));
    AdminService service =
        new AdminService(
            accounts,
            roles,
            permissions,
            idempotency,
            mock(PasswordEncoder.class),
            auth,
            outbox,
            properties);

    assertThatThrownBy(
            () -> service.replaceStaffRoles(actor.getId(), Set.of("RECEPTIONIST"), actor.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("tự xóa quyền");
    verifyNoInteractions(auth, outbox);
  }
}
