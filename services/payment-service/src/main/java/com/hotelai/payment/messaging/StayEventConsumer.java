package com.hotelai.payment.messaging;

import com.hotelai.payment.config.CorrelationIdFilter;
import com.hotelai.payment.domain.ReservationProjectionService;
import com.hotelai.payment.domain.ReservationProjectionService.StayFolioSnapshot;
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
    private final ReservationProjectionService projections;

    public StayEventConsumer(
            ObjectMapper objectMapper, ReservationProjectionService projections) {
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
            StayFolioSnapshot snapshot =
                    objectMapper.treeToValue(envelope.payload(), StayFolioSnapshot.class);
            validate(snapshot);
            projections.synchronizeStay(
                    envelope.eventId(), envelope.eventType(), envelope.producer(),
                    envelope.correlationId(), envelope.occurredAt(), snapshot);
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

    private static void validate(StayFolioSnapshot snapshot) {
        if (snapshot.stayId() == null
                || snapshot.reservationId() == null
                || snapshot.currency() == null
                || snapshot.chargesTotal() == null
                || snapshot.paidTotal() == null
                || snapshot.chargesTotal().signum() < 0
                || snapshot.paidTotal().signum() < 0) {
            throw new IllegalArgumentException("Stay folio projection is incomplete");
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
