package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_jobs")
public class NotificationJob {
    public enum Status { PENDING, PROCESSING, RETRY_SCHEDULED, SENT, FAILED, DEAD_LETTERED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "source_event_id", nullable = false, unique = true, length = 120)
    private String sourceEventId;
    @Column(name = "deduplication_key", nullable = false, unique = true, length = 200)
    private String deduplicationKey;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;
    @Column(name = "recipient_identity_id")
    private UUID recipientIdentityId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    @Column(nullable = false, length = 500)
    private String recipient;
    @Column(length = 300)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "external_reference", length = 300)
    private String externalReference;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected NotificationJob() {}

    public NotificationJob(
            String sourceEventId,
            String deduplicationKey,
            String correlationId,
            UUID recipientIdentityId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String content,
            Instant scheduledAt,
            int maxAttempts) {
        this.sourceEventId = sourceEventId;
        this.deduplicationKey = deduplicationKey;
        this.correlationId = correlationId;
        this.recipientIdentityId = recipientIdentityId;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.content = content;
        this.nextAttemptAt = scheduledAt;
        this.maxAttempts = maxAttempts;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void claim() {
        this.status = Status.PROCESSING;
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public void delivered(String externalReference) {
        this.status = Status.SENT;
        this.externalReference = externalReference;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void failed(String error, Instant retryAt) {
        this.lastError = error;
        this.updatedAt = Instant.now();
        if (attemptCount >= maxAttempts) {
            this.status = Status.DEAD_LETTERED;
        } else {
            this.status = Status.RETRY_SCHEDULED;
            this.nextAttemptAt = retryAt;
        }
    }

    public void retryNow() {
        if (status != Status.DEAD_LETTERED && status != Status.FAILED) {
            throw new IllegalStateException("Only failed jobs can be retried manually");
        }
        this.status = Status.PENDING;
        this.attemptCount = 0;
        this.lastError = null;
        this.nextAttemptAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = Status.CANCELLED;
        this.lastError = reason;
        this.updatedAt = Instant.now();
    }

    public void markRead() {
        if (readAt == null) readAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSourceEventId() { return sourceEventId; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public String getCorrelationId() { return correlationId; }
    public UUID getRecipientIdentityId() { return recipientIdentityId; }
    public NotificationChannel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getExternalReference() { return externalReference; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
