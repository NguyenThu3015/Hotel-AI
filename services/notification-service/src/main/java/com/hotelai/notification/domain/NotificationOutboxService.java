package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationOutboxService {
    private final NotificationOutboxRepository events;
    private final ObjectMapper objectMapper;

    public NotificationOutboxService(NotificationOutboxRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public void delivered(NotificationJob job, String providerReference) {
        append(job, "notification.delivered.v1", "DELIVERED", providerReference, null);
    }

    public void deadLettered(NotificationJob job, String error) {
        append(job, "notification.failed.v1", "DEAD_LETTERED", null, error);
    }

    private void append(
            NotificationJob job,
            String eventType,
            String deliveryStatus,
            String providerReference,
            String error) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("notificationId", job.getId());
            payload.put("sourceEventId", job.getSourceEventId());
            payload.put("recipientIdentityId", job.getRecipientIdentityId());
            payload.put("channel", job.getChannel().name());
            payload.put("status", deliveryStatus);
            payload.put("providerReference", providerReference);
            payload.put("error", error);
            payload.put("attemptCount", job.getAttemptCount());
            events.save(new NotificationOutboxEvent(
                    "NotificationJob",
                    job.getId(),
                    eventType,
                    job.getCorrelationId(),
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create notification outbox event", exception);
        }
    }
}

@Entity
@Table(name = "notification_outbox_events")
class NotificationOutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Version
    private long version;

    protected NotificationOutboxEvent() {}

    NotificationOutboxEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String correlationId,
            String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.schemaVersion = 1;
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    void published() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    void failed(String error) {
        this.attemptCount++;
        this.lastError = error;
    }

    UUID id() { return id; }
    String aggregateType() { return aggregateType; }
    UUID aggregateId() { return aggregateId; }
    String eventType() { return eventType; }
    String correlationId() { return correlationId; }
    int schemaVersion() { return schemaVersion; }
    String payload() { return payload; }
    Instant occurredAt() { return occurredAt; }
}

interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEvent, UUID> {
    @Query("""
            select event from NotificationOutboxEvent event
            where event.publishedAt is null
            order by event.occurredAt asc
            """)
    List<NotificationOutboxEvent> findPending(org.springframework.data.domain.Pageable pageable);
}

@Component
class NotificationOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxRepository events;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final String exchange;

    NotificationOutboxPublisher(
            NotificationOutboxRepository events,
            RabbitTemplate rabbit,
            ObjectMapper objectMapper,
            @Value("${hotel.notification.exchange}") String exchange) {
        this.events = events;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${hotel.notification.outbox-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        events.findPending(PageRequest.of(0, 50)).forEach(this::publish);
    }

    private void publish(NotificationOutboxEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", event.id());
            envelope.put("eventType", event.eventType());
            envelope.put("occurredAt", event.occurredAt());
            envelope.put("producer", "notification-service");
            envelope.put("correlationId", event.correlationId());
            envelope.put("aggregateType", event.aggregateType());
            envelope.put("aggregateId", event.aggregateId());
            envelope.put("schemaVersion", event.schemaVersion());
            envelope.put("payload", objectMapper.readTree(event.payload()));
            CorrelationData confirmation = new CorrelationData(event.id().toString());
            rabbit.convertAndSend(
                    exchange,
                    event.eventType(),
                    objectMapper.writeValueAsString(envelope),
                    message -> {
                        message.getMessageProperties().setMessageId(event.id().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setHeader("eventType", event.eventType());
                        message.getMessageProperties().setHeader("schemaVersion", event.schemaVersion());
                        return message;
                    },
                    confirmation);
            CorrelationData.Confirm result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.ack()) throw new IllegalStateException("RabbitMQ rejected event: " + result.reason());
            if (confirmation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned unroutable event " + event.eventType() + ": "
                                + confirmation.getReturned().getReplyText());
            }
            event.published();
        } catch (Exception exception) {
            event.failed(exception.getMessage());
            log.warn("Cannot publish notification outbox event {}", event.id(), exception);
        }
    }
}
