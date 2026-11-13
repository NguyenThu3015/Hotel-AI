package com.hotelai.notification.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.notification.domain.DeliveryAttempt;
import com.hotelai.notification.domain.DeliveryAttemptRepository;
import com.hotelai.notification.domain.NotificationJob;
import com.hotelai.notification.domain.NotificationJobRepository;
import com.hotelai.notification.domain.NotificationOutboxService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobResultServiceTest {
    private final NotificationJobRepository jobs = mock(NotificationJobRepository.class);
    private final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    private final NotificationOutboxService outbox = mock(NotificationOutboxService.class);
    private final JobResultService service = new JobResultService(jobs, attempts, outbox, 1);

    @Test
    void successPersistsAttemptAndDeliveryEventAtomically() {
        UUID id = UUID.randomUUID();
        NotificationJob job = mock(NotificationJob.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(job.getAttemptCount()).thenReturn(1);

        service.success(id, "smtp-reference");

        verify(job).delivered("smtp-reference");
        verify(attempts).save(any(DeliveryAttempt.class));
        verify(outbox).delivered(job, "smtp-reference");
    }

    @Test
    void terminalFailureEmitsDeadLetterEvent() {
        UUID id = UUID.randomUUID();
        NotificationJob job = mock(NotificationJob.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(job.getAttemptCount()).thenReturn(5);
        when(job.getStatus()).thenReturn(NotificationJob.Status.DEAD_LETTERED);

        service.failure(id, "provider unavailable");

        verify(job).failed(org.mockito.ArgumentMatchers.eq("provider unavailable"), any());
        verify(attempts).save(any(DeliveryAttempt.class));
        verify(outbox).deadLettered(job, "provider unavailable");
    }

    @Test
    void retryableFailureDoesNotEmitTerminalEvent() {
        UUID id = UUID.randomUUID();
        NotificationJob job = mock(NotificationJob.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        when(job.getAttemptCount()).thenReturn(1);
        when(job.getStatus()).thenReturn(NotificationJob.Status.RETRY_SCHEDULED);

        service.failure(id, "temporary error");

        verify(outbox, never()).deadLettered(any(), any());
    }
}
