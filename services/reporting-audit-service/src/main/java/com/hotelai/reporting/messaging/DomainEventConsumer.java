package com.hotelai.reporting.messaging;

import com.hotelai.reporting.reporting.ReportingProjectionService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DomainEventConsumer {
    private final ObjectMapper objectMapper;
    private final ReportingProjectionService projections;

    public DomainEventConsumer(
            ObjectMapper objectMapper, ReportingProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.reporting.messaging.queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put("correlationId", envelope.correlationId());
        }
        try {
            projections.ingest(envelope);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.eventType() == null
                || envelope.eventType().isBlank()
                || envelope.producer() == null
                || envelope.producer().isBlank()
                || envelope.payload() == null
                || envelope.schemaVersion() != 1) {
            throw new IllegalArgumentException("Invalid domain event envelope");
        }
    }

    public record EventEnvelope(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            String producer,
            String correlationId,
            String aggregateType,
            UUID aggregateId,
            int schemaVersion,
            JsonNode payload) {}
}
