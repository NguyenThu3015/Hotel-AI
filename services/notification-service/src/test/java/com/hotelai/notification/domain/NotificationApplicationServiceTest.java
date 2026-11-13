package com.hotelai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.notification.api.NotificationDtos;
import com.hotelai.notification.common.ApiException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationApplicationServiceTest {
    private final NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
    private final NotificationJobRepository jobs = mock(NotificationJobRepository.class);
    private final InboxEventRepository inbox = mock(InboxEventRepository.class);
    private final NotificationPreferenceRepository preferences =
            mock(NotificationPreferenceRepository.class);
    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(
                templates, jobs, inbox, preferences, new TemplateRenderer());
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void otpEventIsRenderedAndPersistedWithInboxInOneUseCase() {
        NotificationTemplate template = new NotificationTemplate(
                "OTP_ACCOUNT_ACTIVATION", NotificationChannel.EMAIL, "vi", "Mã xác minh", "OTP {{otp}}");
        when(templates.findByCodeAndChannelAndLocaleAndActiveTrue(
                "OTP_ACCOUNT_ACTIVATION", NotificationChannel.EMAIL, "vi"))
                .thenReturn(Optional.of(template));
        String eventId = UUID.randomUUID().toString();

        NotificationDtos.JobResponse response = service.enqueue(new NotificationDtos.EnqueueRequest(
                eventId, "notification.otp.requested.v1", "correlation-1", null,
                "OTP_ACCOUNT_ACTIVATION", NotificationChannel.EMAIL, "guest@example.com", "vi",
                Map.of("otp", "123456"), null, 5, eventId, false));

        assertThat(response.content()).isEqualTo("OTP 123456");
        assertThat(response.recipient()).isEqualTo("g***@example.com");
        verify(jobs).save(any(NotificationJob.class));
        verify(inbox).save(any(InboxEvent.class));
    }

    @Test
    void duplicateEventReturnsExistingJobWithoutSendingAnother() {
        String eventId = UUID.randomUUID().toString();
        NotificationJob existing = new NotificationJob(
                eventId, eventId, "correlation-1", null,
                NotificationChannel.EMAIL, "guest@example.com",
                "Subject", "Body", java.time.Instant.now(), 5);
        when(inbox.existsById(eventId)).thenReturn(true);
        when(inbox.findById(eventId))
                .thenReturn(Optional.of(new InboxEvent(
                        eventId, "notification.requested.v1", null)));
        when(jobs.findBySourceEventId(eventId)).thenReturn(Optional.of(existing));

        service.enqueue(new NotificationDtos.EnqueueRequest(
                eventId, "notification.requested.v1", null, null, "ANY", NotificationChannel.EMAIL,
                "guest@example.com", "vi", Map.of(), null, 5, eventId, false));

        verify(jobs, never()).save(any());
        verify(inbox, never()).save(any());
    }

    @Test
    void rejectsInvalidRecipientBeforeCreatingInboxRecord() {
        NotificationTemplate template = new NotificationTemplate(
                "OTP_ACCOUNT_ACTIVATION", NotificationChannel.EMAIL, "vi", "Subject", "Body");
        when(templates.findByCodeAndChannelAndLocaleAndActiveTrue(
                "OTP_ACCOUNT_ACTIVATION", NotificationChannel.EMAIL, "vi"))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.enqueue(new NotificationDtos.EnqueueRequest(
                UUID.randomUUID().toString(), null, null, null, "OTP_ACCOUNT_ACTIVATION",
                NotificationChannel.EMAIL, "not-an-email", "vi", Map.of(), null, 5,
                null, false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("email");
        verify(inbox, never()).save(any());
    }

    @Test
    void consentRequiredMessageIsCancelledWhenChannelIsDisabled() {
        NotificationTemplate template = new NotificationTemplate(
                "BOOKING_REMINDER", NotificationChannel.SMS, "vi", null, "Nhắc {{code}}");
        when(templates.findByCodeAndChannelAndLocaleAndActiveTrue(
                "BOOKING_REMINDER", NotificationChannel.SMS, "vi"))
                .thenReturn(Optional.of(template));
        UUID identityId = UUID.randomUUID();

        NotificationDtos.JobResponse response = service.enqueue(
                new NotificationDtos.EnqueueRequest(
                        UUID.randomUUID().toString(),
                        "notification.requested.v1",
                        null,
                        identityId,
                        "BOOKING_REMINDER",
                        NotificationChannel.SMS,
                        "0901234567",
                        "vi",
                        Map.of("code", "RSV-1"),
                        null,
                        5,
                        "reminder:RSV-1",
                        true));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.lastError()).isEqualTo("RECIPIENT_OPTED_OUT");
    }
}
