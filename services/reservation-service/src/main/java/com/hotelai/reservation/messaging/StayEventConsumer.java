package com.hotelai.reservation.messaging;

import com.hotelai.reservation.config.CorrelationIdFilter;
import com.hotelai.reservation.domain.ReservationApplicationService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class StayEventConsumer {
    private static final Set<String> SUPPORTED =
            Set.of("stay.checked-in.v1", "stay.checked-out.v1", "stay.no-show.v1");

    private final ObjectMapper objectMapper;
    private final ReservationApplicationService reservations;

    public StayEventConsumer(ObjectMapper objectMapper, ReservationApplicationService reservations) {
        this.objectMapper = objectMapper;
        this.reservations = reservations;
    }

    @RabbitListener(queues = "${hotel.messaging.stay-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        if (envelope.eventId() == null || envelope.payload() == null || envelope.schemaVersion() != 1
                || !SUPPORTED.contains(envelope.eventType()) || !"stay-service".equals(envelope.producer())) {
            throw new IllegalArgumentException("Invalid stay event envelope");
        }
        StayPayload payload = objectMapper.treeToValue(envelope.payload(), StayPayload.class);
        if (payload.reservationId() == null) throw new IllegalArgumentException("Stay event lacks reservationId");
        if (envelope.correlationId() != null) MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        try {
            reservations.recordStayTransition(
                    envelope.eventId(), envelope.eventType(), envelope.producer(), envelope.correlationId(),
                    payload.reservationId());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
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

    record StayPayload(UUID stayId, UUID reservationId, UUID roomId, String status) {}
}
