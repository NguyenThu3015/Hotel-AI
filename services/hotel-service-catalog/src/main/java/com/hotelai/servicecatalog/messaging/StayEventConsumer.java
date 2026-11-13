package com.hotelai.servicecatalog.messaging;

import com.hotelai.servicecatalog.config.CorrelationIdFilter;
import com.hotelai.servicecatalog.domain.StayProjection.StaySnapshot;
import com.hotelai.servicecatalog.domain.StayProjectionService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class StayEventConsumer {
    private final ObjectMapper objectMapper;
    private final StayProjectionService projections;

    public StayEventConsumer(
            ObjectMapper objectMapper, StayProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.messaging.stay-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            StaySnapshot snapshot =
                    objectMapper.treeToValue(envelope.payload(), StaySnapshot.class);
            projections.synchronize(
                    envelope.eventId(), envelope.eventType(), envelope.producer(),
                    envelope.occurredAt(), snapshot);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.payload() == null
                || envelope.schemaVersion() != 1
                || !"stay-service".equals(envelope.producer())
                || envelope.eventType() == null
                || !envelope.eventType().startsWith("stay.")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Invalid stay event envelope");
        }
    }

    record EventEnvelope(
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
