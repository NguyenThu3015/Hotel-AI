package com.hotelai.billing.messaging;

import com.hotelai.billing.config.CorrelationIdFilter;
import com.hotelai.billing.domain.InvoiceProjectionService;
import com.hotelai.billing.domain.InvoiceProjectionService.PaymentSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventConsumer {
    private final ObjectMapper objectMapper;
    private final InvoiceProjectionService projections;

    public PaymentEventConsumer(
            ObjectMapper objectMapper, InvoiceProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.billing.messaging.payment-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            PaymentSnapshot snapshot =
                    objectMapper.treeToValue(envelope.payload(), PaymentSnapshot.class);
            projections.payment(
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
                || !"payment-service".equals(envelope.producer())
                || envelope.eventType() == null
                || !envelope.eventType().startsWith("payment.")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Invalid payment event envelope");
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
