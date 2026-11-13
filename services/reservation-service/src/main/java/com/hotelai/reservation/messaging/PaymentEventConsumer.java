package com.hotelai.reservation.messaging;

import com.hotelai.reservation.config.CorrelationIdFilter;
import com.hotelai.reservation.domain.ReservationApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventConsumer {
    private static final Set<String> SUPPORTED = Set.of(
            "payment.succeeded.v1", "payment.failed.v1", "payment.refund-completed.v1");

    private final ObjectMapper objectMapper;
    private final ReservationApplicationService reservations;

    public PaymentEventConsumer(ObjectMapper objectMapper, ReservationApplicationService reservations) {
        this.objectMapper = objectMapper;
        this.reservations = reservations;
    }

    @RabbitListener(queues = "${hotel.messaging.payment-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        // The topic binding also receives lifecycle events such as intent-created and
        // refund-requested. They are valid Payment events, but do not mutate a reservation.
        if (!SUPPORTED.contains(envelope.eventType())) return;
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            PaymentPayload payload = objectMapper.treeToValue(envelope.payload(), PaymentPayload.class);
            if (payload.reservationId() == null) throw new IllegalArgumentException("Payment event lacks reservationId");
            if (payload.amount() == null || payload.amount().signum() < 0
                    || payload.currency() == null || !payload.currency().matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Payment event has invalid amount or currency");
            }
            if ("payment.succeeded.v1".equals(envelope.eventType()) && payload.amount().signum() == 0) {
                throw new IllegalArgumentException("Successful payment amount must be positive");
            }
            reservations.recordPayment(
                    envelope.eventId(), envelope.eventType(), envelope.producer(), envelope.correlationId(),
                    payload.reservationId(), payload.amount(), payload.currency());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Payment event envelope is incomplete");
        }
        if (envelope.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported payment event schema version");
        }
        if (!"payment-service".equals(envelope.producer())) {
            throw new IllegalArgumentException("Unexpected payment event producer");
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

    record PaymentPayload(
            UUID paymentId,
            UUID reservationId,
            BigDecimal amount,
            String currency,
            String status,
            String providerReference) {}
}
