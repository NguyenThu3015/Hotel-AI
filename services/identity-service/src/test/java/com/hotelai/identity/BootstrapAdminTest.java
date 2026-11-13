package com.hotelai.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class BootstrapAdminTest {
  @Test
  void publishesBootstrapAdministratorForDownstreamProjections() {
    AccountRepository accounts = mock(AccountRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    OutboxService outbox = mock(OutboxService.class);
    IdentityProperties properties = new IdentityProperties("HOTEL-1", null, null);
    Role admin = new Role("ADMIN", "Administrator", null, Set.of());
    when(accounts.existsByEmailIgnoreCase("admin@hotelai.local")).thenReturn(false);
    when(roles.findByCode("ADMIN")).thenReturn(Optional.of(admin));
    when(passwords.encode("Admin@12345")).thenReturn("encoded");
    when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    BootstrapAdmin bootstrap = new BootstrapAdmin(accounts, roles, passwords, properties, outbox);
    ReflectionTestUtils.setField(bootstrap, "email", "admin@hotelai.local");
    ReflectionTestUtils.setField(bootstrap, "password", "Admin@12345");
    ReflectionTestUtils.setField(bootstrap, "name", "System Administrator");

    bootstrap.run(mock(ApplicationArguments.class));

    ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(account.capture());
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
    verify(outbox)
        .append(
            eq("Account"),
            eq(account.getValue().getId()),
            eq("identity.staff.created.v1"),
            payload.capture());
    assertThat(payload.getValue())
        .containsEntry("identityId", account.getValue().getId())
        .containsEntry("email", "admin@hotelai.local")
        .containsEntry("accountType", "STAFF")
        .containsEntry("roles", java.util.List.of("ADMIN"));
  }
}
