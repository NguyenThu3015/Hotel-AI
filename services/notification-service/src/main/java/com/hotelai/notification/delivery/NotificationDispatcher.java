package com.hotelai.notification.delivery;

import com.hotelai.notification.domain.DeliveryAttempt;
import com.hotelai.notification.domain.DeliveryAttemptRepository;
import com.hotelai.notification.domain.NotificationChannel;
import com.hotelai.notification.domain.NotificationJob;
import com.hotelai.notification.domain.NotificationJobRepository;
import com.hotelai.notification.domain.NotificationOutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDispatcher {
    private final JobClaimService claimer;
    private final JobResultService resultService;
    private final Map<NotificationChannel, DeliveryAdapter> adapters;

    public NotificationDispatcher(JobClaimService claimer, JobResultService resultService, List<DeliveryAdapter> adapters) {
        this.claimer = claimer;
        this.resultService = resultService;
        this.adapters = new EnumMap<>(NotificationChannel.class);
        adapters.forEach(adapter -> {
            if (this.adapters.put(adapter.channel(), adapter) != null) {
                throw new IllegalStateException("Multiple adapters configured for " + adapter.channel());
            }
        });
    }

    @Scheduled(fixedDelayString = "${hotel.notification.dispatch-interval-ms:1000}")
    public void dispatchDueJobs() {
        for (int i = 0; i < 20; i++) {
            Optional<NotificationJob> claimed = claimer.claim();
            if (claimed.isEmpty()) return;
            NotificationJob job = claimed.get();
            try {
                DeliveryAdapter adapter = Optional.ofNullable(adapters.get(job.getChannel()))
                        .orElseThrow(() -> new IllegalStateException("No adapter for " + job.getChannel()));
                DeliveryAdapter.DeliveryResult result = adapter.send(new DeliveryAdapter.DeliveryRequest(
                        job.getRecipient(), job.getSubject(), job.getContent(), job.getCorrelationId()));
                resultService.success(job.getId(), result.providerReference());
            } catch (Exception ex) {
                resultService.failure(job.getId(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
    }
}

@Service
class JobClaimService {
    private final NotificationJobRepository jobs;

    JobClaimService(NotificationJobRepository jobs) { this.jobs = jobs; }

    @Transactional
    public Optional<NotificationJob> claim() {
        Optional<NotificationJob> job = jobs.lockNextDue(Instant.now());
        job.ifPresent(NotificationJob::claim);
        return job;
    }
}

@Service
class JobResultService {
    private final NotificationJobRepository jobs;
    private final DeliveryAttemptRepository attempts;
    private final NotificationOutboxService outbox;
    private final Duration baseRetryDelay;

    JobResultService(
            NotificationJobRepository jobs,
            DeliveryAttemptRepository attempts,
            NotificationOutboxService outbox,
            @Value("${hotel.notification.base-retry-seconds:30}") long retrySeconds) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.outbox = outbox;
        this.baseRetryDelay = Duration.ofSeconds(retrySeconds);
    }

    @Transactional
    public void success(UUID jobId, String reference) {
        NotificationJob job = jobs.findById(jobId).orElseThrow();
        job.delivered(reference);
        attempts.save(new DeliveryAttempt(jobId, job.getAttemptCount(), true, reference, null));
        outbox.delivered(job, reference);
    }

    @Transactional
    public void failure(UUID jobId, String error) {
        NotificationJob job = jobs.findById(jobId).orElseThrow();
        long multiplier = 1L << Math.min(Math.max(job.getAttemptCount() - 1, 0), 10);
        Instant retryAt = Instant.now().plus(baseRetryDelay.multipliedBy(multiplier));
        job.failed(error, retryAt);
        attempts.save(new DeliveryAttempt(jobId, job.getAttemptCount(), false, null, error));
        if (job.getStatus() == NotificationJob.Status.DEAD_LETTERED) {
            outbox.deadLettered(job, error);
        }
    }
}
