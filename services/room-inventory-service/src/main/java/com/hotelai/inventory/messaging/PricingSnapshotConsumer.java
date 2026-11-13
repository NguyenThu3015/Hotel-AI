package com.hotelai.inventory.messaging;

import com.hotelai.inventory.common.ApiException;
import com.hotelai.inventory.domain.ProcessedEvent;
import com.hotelai.inventory.domain.RoomType;
import com.hotelai.inventory.domain.RoomTypePriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PricingSnapshotConsumer {
    private final ObjectMapper objectMapper;
    private final PricingRoomTypeRepository roomTypes;
    private final PricingSnapshotRepository prices;
    private final PricingProcessedEventRepository processedEvents;

    public PricingSnapshotConsumer(
            ObjectMapper objectMapper,
            PricingRoomTypeRepository roomTypes,
            PricingSnapshotRepository prices,
            PricingProcessedEventRepository processedEvents) {
        this.objectMapper = objectMapper;
        this.roomTypes = roomTypes;
        this.prices = prices;
        this.processedEvents = processedEvents;
    }

    @RabbitListener(queues = "${hotel.messaging.pricing-queue}")
    @Transactional
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        if (processedEvents.existsById(envelope.eventId())) return;
        if (envelope.correlationId() != null) MDC.put("correlationId", envelope.correlationId());
        try {
            switch (envelope.eventType()) {
                case "pricing.room-type-price-published.v1" -> applyPrice(envelope.payload());
                case "pricing.room-type-policy-published.v1" -> applyPolicy(envelope.payload());
                default -> throw new IllegalArgumentException(
                        "Unsupported pricing event type: " + envelope.eventType());
            }
            processedEvents.save(new ProcessedEvent(envelope.eventId(), envelope.eventType()));
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void applyPrice(JsonNode payload) throws Exception {
        PricePayload price = objectMapper.treeToValue(payload, PricePayload.class);
        roomType(price.roomTypeId());
        RoomTypePriceSnapshot snapshot = prices.findById(price.roomTypeId())
                .orElseGet(() -> new RoomTypePriceSnapshot(
                        price.roomTypeId(), price.nightlyPrice(), price.currency(), price.taxRate(),
                        price.serviceFee(), price.pricingVersion(), price.effectiveAt()));
        snapshot.update(
                price.nightlyPrice(), price.currency(), price.taxRate(), price.serviceFee(),
                price.pricingVersion(), price.effectiveAt());
        prices.save(snapshot);
    }

    private void applyPolicy(JsonNode payload) throws Exception {
        PolicyPayload policy = objectMapper.treeToValue(payload, PolicyPayload.class);
        roomType(policy.roomTypeId()).updatePolicySummary(policy.summary(), policy.policyVersion());
    }

    private RoomType roomType(UUID id) {
        return roomTypes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pricing event references unknown room type."));
    }

    private record EventEnvelope(
            UUID eventId, String eventType, Instant occurredAt, String producer,
            String correlationId, String aggregateType, UUID aggregateId,
            int schemaVersion, JsonNode payload) {}

    private record PricePayload(
            UUID roomTypeId, BigDecimal nightlyPrice, String currency, BigDecimal taxRate,
            BigDecimal serviceFee, long pricingVersion, Instant effectiveAt) {}

    private record PolicyPayload(UUID roomTypeId, String summary, long policyVersion) {}
}
