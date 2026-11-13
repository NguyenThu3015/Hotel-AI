package com.hotelai.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.customer.api.CustomerDtos;
import com.hotelai.customer.common.ApiException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CustomerApplicationServiceTest {
    private final CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
    private final CustomerNoteRepository notes = mock(CustomerNoteRepository.class);
    private final MergeProposalRepository merges = mock(MergeProposalRepository.class);
    private final CustomerAuditRepository audits = mock(CustomerAuditRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final ProcessedIdentityEventRepository inbox = mock(ProcessedIdentityEventRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private CustomerApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CustomerApplicationService(profiles, notes, merges, audits, outbox, inbox, objectMapper);
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void activationEventCreatesOneProfileAndOneInboxRecord() {
        UUID identityId = UUID.randomUUID();
        when(profiles.findByIdentityId(identityId)).thenReturn(Optional.empty());
        CustomerDtos.ProfileResponse response = service.createFromIdentity(new CustomerDtos.IdentityCreatedRequest(
                UUID.randomUUID().toString(), identityId, "Nguyễn An", "an@example.com", "0900000000",
                true, "ACTIVE", Set.of("CUSTOMER")));

        assertThat(response.identityId()).isEqualTo(identityId);
        assertThat(response.emailVerified()).isTrue();
        verify(profiles).save(any(CustomerProfile.class));
        verify(inbox).save(any(ProcessedIdentityEvent.class));
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void duplicateActivationEventDoesNotCreateAnotherProfile() {
        UUID identityId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        CustomerProfile existing = new CustomerProfile(
                identityId, "Nguyễn An", "an@example.com", null, true, "ACTIVE", "CUSTOMER");
        when(inbox.existsById(eventId)).thenReturn(true);
        when(profiles.findByIdentityId(identityId)).thenReturn(Optional.of(existing));

        service.createFromIdentity(new CustomerDtos.IdentityCreatedRequest(
                eventId, identityId, "Ignored", "ignored@example.com", null, true, "ACTIVE", Set.of("CUSTOMER")));

        verify(profiles, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void profileCannotChangeIdentityOwnedEmailWithoutReverification() {
        UUID identityId = UUID.randomUUID();
        CustomerProfile existing = new CustomerProfile(
                identityId, "Nguyễn An", "an@example.com", null, true, "ACTIVE", "CUSTOMER");
        when(profiles.findByIdentityId(identityId)).thenReturn(Optional.of(existing));

        CustomerDtos.UpdateProfileRequest request = new CustomerDtos.UpdateProfileRequest(
                "Nguyễn An", "other@example.com", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateMine(identityId, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Identity Service");
        verify(outbox, never()).save(any());
    }

    @Test
    void managerCanUpdateCustomerProfileWithCurrentVersion() {
        UUID identityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerProfile existing = new CustomerProfile(
                identityId, "Nguyễn An", "an@example.com", "0900000000", true, "ACTIVE", "CUSTOMER");
        when(profiles.findById(customerId)).thenReturn(Optional.of(existing));

        CustomerDtos.ProfileResponse response = service.updateForStaff(
                customerId,
                new CustomerDtos.StaffUpdateRequest(
                        "Nguyễn An mới", "0911111111", null, null, "vi", "{\"quiet\":true}", 0L),
                actorId,
                Set.of("MANAGER"));

        assertThat(response.fullName()).isEqualTo("Nguyễn An mới");
        assertThat(response.phone()).isEqualTo("0911111111");
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void receptionistCannotEditCustomerPii() {
        UUID identityId = UUID.randomUUID();
        CustomerProfile existing = new CustomerProfile(
                identityId, "Nguyễn An", "an@example.com", "0900000000", true, "ACTIVE", "CUSTOMER");

        assertThatThrownBy(() -> service.updateForStaff(
                        UUID.randomUUID(),
                        new CustomerDtos.StaffUpdateRequest(
                                "Tên khác", null, null, null, null, null, 0L),
                        UUID.randomUUID(),
                        Set.of("RECEPTIONIST")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }
}
