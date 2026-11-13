package com.hotelai.stay.messaging;

import com.hotelai.stay.config.CorrelationIdFilter;
import com.hotelai.stay.domain.ProjectionService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryEventConsumer {
    private final ObjectMapper objectMapper;
    private final ProjectionService projections;

    public InventoryEventConsumer(ObjectMapper objectMapper, ProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.messaging.inventory-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            if (envelope.eventType().equals("inventory.room-type.created.v1")
                    || envelope.eventType().equals("inventory.room-type.updated.v1")) {
                roomType(envelope);
            } else if (envelope.eventType().equals("inventory.room.created.v1")
                    || envelope.eventType().equals("inventory.room.updated.v1")) {
                room(envelope);
            } else if (envelope.eventType().equals("inventory.room.status-changed.v1")) {
                roomStatus(envelope);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void roomType(EventEnvelope envelope) {
        JsonNode value = envelope.payload();
        projections.roomType(
                envelope.eventId(), envelope.eventType(), envelope.producer(), envelope.occurredAt(),
                uuid(value, "roomTypeId"), text(value, "code"), text(value, "name"),
                value.path("maxAdults").asInt(), value.path("maxChildren").asInt(),
                value.path("maxOccupancy").asInt(), value.path("active").asBoolean(),
                value.path("version").asLong());
    }

    private void room(EventEnvelope envelope) {
        JsonNode value = envelope.payload();
        projections.room(
                envelope.eventId(), envelope.eventType(), envelope.producer(), envelope.occurredAt(),
                uuid(value, "roomId"), text(value, "roomNumber"), uuid(value, "roomTypeId"),
                text(value, "status"), value.path("active").asBoolean(),
                optionalUuid(value, "currentStayId"), value.path("version").asLong());
    }

    private void roomStatus(EventEnvelope envelope) {
        JsonNode value = envelope.payload();
        String next = value.path("newStatus").asText(value.path("status").asText());
        UUID stayId = "OCCUPIED".equals(next) ? optionalUuid(value, "stayId") : null;
        projections.roomStatus(
                envelope.eventId(), envelope.eventType(), envelope.producer(), envelope.occurredAt(),
                uuid(value, "roomId"), value.path("roomNumber").asText(null),
                optionalUuid(value, "roomTypeId"), next, stayId, value.path("version").asLong());
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.payload() == null
                || envelope.schemaVersion() != 1
                || !"room-inventory-service".equals(envelope.producer())
                || envelope.eventType() == null
                || !envelope.eventType().startsWith("inventory.")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Invalid inventory event envelope");
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return UUID.fromString(value);
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Inventory event lacks " + field);
        }
        return value;
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
