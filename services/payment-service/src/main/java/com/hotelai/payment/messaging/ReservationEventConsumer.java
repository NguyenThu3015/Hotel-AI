package com.hotelai.payment.messaging;

import com.hotelai.payment.config.CorrelationIdFilter;
import com.hotelai.payment.domain.ReservationProjectionService;
import com.hotelai.payment.domain.ReservationProjectionService.ReservationPaymentSnapshot;
import java.math.BigDecimal;
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
    private final ReservationProjectionService projections;

    public ReservationEventConsumer(
            ObjectMapper objectMapper, ReservationProjectionService projections) {
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
            ReservationPaymentSnapshot snapshot =
                    objectMapper.treeToValue(envelope.payload(), ReservationPaymentSnapshot.class);
            validate(snapshot);
            projections.synchronize(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.producer(),
                    envelope.correlationId(),
                    envelope.occurredAt(),
                    snapshot);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Reservation event envelope is incomplete");
        }
        if (!"reservation-service".equals(envelope.producer())) {
            throw new IllegalArgumentException("Unexpected reservation event producer");
        }
        if (envelope.eventType() == null
                || !envelope.eventType().startsWith("reservation.")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Unsupported reservation event");
        }
        if (envelope.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported reservation event schema version");
        }
    }

    private static void validate(ReservationPaymentSnapshot value) {
        if (value.reservationId() == null
                || value.customerId() == null
                || value.status() == null
                || value.currency() == null
                || value.totalAmount() == null
                || value.depositRequired() == null
                || value.paidAmount() == null) {
            throw new IllegalArgumentException("Reservation payment projection is incomplete");
        }
        if (!value.currency().matches("[A-Z]{3}")
                || value.totalAmount().signum() < 0
                || value.depositRequired().signum() < 0
                || value.paidAmount().signum() < 0) {
            throw new IllegalArgumentException("Reservation payment projection has invalid money");
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
