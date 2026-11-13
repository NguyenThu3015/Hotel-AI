package com.hotelai.backend.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelai.backend.account.Role;
import com.hotelai.backend.account.RoleRepository;
import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityProjectionServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final IdentityProjectionInboxRepository inbox =
      mock(IdentityProjectionInboxRepository.class);
  private final IdentityProjectionService service =
      new IdentityProjectionService(users, roles, inbox);
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void activatedAccountCreatesLegacyShadowUser() throws Exception {
    UUID identityId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Role customer = mock(Role.class);
    when(inbox.existsById(eventId)).thenReturn(false);
    when(users.findByIdentityId(identityId)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.empty());
    when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(customer));
    EventEnvelope event =
        envelope(
            eventId,
            "identity.account.activated.v1",
            """
            {
              "identityId":"%s",
              "email":"guest@example.com",
              "fullName":"Guest One",
              "phone":"0900000000",
              "status":"ACTIVE",
              "emailVerified":true,
              "accountType":"CUSTOMER",
              "roles":["CUSTOMER"]
            }
            """
                .formatted(identityId));

    service.apply(event);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getIdentityId()).isEqualTo(identityId);
    assertThat(saved.getValue().getEmail()).isEqualTo("guest@example.com");
    assertThat(saved.getValue().getFullName()).isEqualTo("Guest One");
    assertThat(saved.getValue().isEmailVerified()).isTrue();
    assertThat(saved.getValue().getRoles()).containsExactly(customer);
    verify(inbox).save(any(IdentityProjectionInbox.class));
  }

  @Test
  void duplicateEventIsIgnored() throws Exception {
    UUID eventId = UUID.randomUUID();
    when(inbox.existsById(eventId)).thenReturn(true);

    service.apply(envelope(eventId, "identity.account.activated.v1", "{}"));

    verify(users, never()).save(any());
    verify(inbox, never()).save(any());
  }

  @Test
  void customIdentityRoleDoesNotPoisonLegacyProjection() throws Exception {
    UUID identityId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Role customer = mock(Role.class);
    when(inbox.existsById(eventId)).thenReturn(false);
    when(users.findByIdentityId(identityId)).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.empty());
    when(roles.findByName("CUSTOM_SUPPORT")).thenReturn(Optional.empty());
    when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(customer));
    EventEnvelope event =
        envelope(
            eventId,
            "identity.staff.created.v1",
            """
            {
              "identityId":"%s",
              "email":"staff@example.com",
              "fullName":"Custom Staff",
              "status":"ACTIVE",
              "emailVerified":true,
              "accountType":"STAFF",
              "roles":["CUSTOM_SUPPORT"]
            }
            """
                .formatted(identityId));

    service.apply(event);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getRoles()).isEmpty();
  }

  private EventEnvelope envelope(UUID eventId, String type, String payload) throws Exception {
    return new EventEnvelope(
        eventId,
        type,
        Instant.now(),
        "identity-service",
        UUID.randomUUID(),
        "Account",
        UUID.randomUUID(),
        1,
        json.readTree(payload));
  }
}
