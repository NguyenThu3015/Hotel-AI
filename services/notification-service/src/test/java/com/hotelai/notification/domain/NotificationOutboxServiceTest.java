package com.hotelai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class NotificationOutboxServiceTest {
    @Test
    void deliveredEventContainsStablePublicDeliverySnapshot() throws Exception {
        NotificationOutboxRepository events = mock(NotificationOutboxRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"status\":\"DELIVERED\"}");
        NotificationOutboxService outbox = new NotificationOutboxService(events, objectMapper);
        NotificationJob job = mock(NotificationJob.class);
        UUID jobId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        when(job.getId()).thenReturn(jobId);
        when(job.getSourceEventId()).thenReturn(UUID.randomUUID().toString());
        when(job.getRecipientIdentityId()).thenReturn(identityId);
        when(job.getChannel()).thenReturn(NotificationChannel.EMAIL);
        when(job.getCorrelationId()).thenReturn("correlation-1");
        when(job.getAttemptCount()).thenReturn(1);

        outbox.delivered(job, "smtp-reference");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> publicPayload = ArgumentCaptor.forClass(Map.class);
        verify(objectMapper).writeValueAsString(publicPayload.capture());
        assertThat(publicPayload.getValue())
                .containsEntry("notificationId", jobId)
                .containsEntry("recipientIdentityId", identityId)
                .containsEntry("channel", "EMAIL")
                .containsEntry("status", "DELIVERED")
                .containsEntry("providerReference", "smtp-reference")
                .containsEntry("attemptCount", 1);
        ArgumentCaptor<NotificationOutboxEvent> stored =
                ArgumentCaptor.forClass(NotificationOutboxEvent.class);
        verify(events).save(stored.capture());
        assertThat(stored.getValue().eventType()).isEqualTo("notification.delivered.v1");
        assertThat(stored.getValue().aggregateId()).isEqualTo(jobId);
    }
}
