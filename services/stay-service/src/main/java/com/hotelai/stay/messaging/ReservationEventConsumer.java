package com.hotelai.stay.messaging;

import com.hotelai.stay.config.CorrelationIdFilter;
import com.hotelai.stay.domain.ProjectionService;
import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReservationEventConsumer {
    private final ObjectMapper objectMapper;
    private final ProjectionService projections;

    public ReservationEventConsumer(ObjectMapper objectMapper, ProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.messaging.reservation-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            ReservationSnapshot snapshot =
                    objectMapper.treeToValue(envelope.payload(), ReservationSnapshot.class);
            validate(snapshot);
            projections.reservation(
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
                || !"reservation-service".equals(envelope.producer())
                || envelope.eventType() == null
                || !envelope.eventType().startsWith("reservation.")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Invalid reservation event envelope");
        }
    }

    private static void validate(ReservationSnapshot value) {
        if (value.reservationId() == null
                || value.customerId() == null
                || value.roomTypeId() == null
                || value.checkIn() == null
                || value.checkOut() == null
                || value.roomCount() < 1
                || value.adults() < 1
                || value.children() < 0
                || value.totalAmount() == null
                || value.depositRequired() == null
                || value.paidAmount() == null) {
            throw new IllegalArgumentException("Reservation stay projection is incomplete");
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
