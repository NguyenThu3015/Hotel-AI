package com.hotelai.stay.messaging;

import com.hotelai.stay.config.CorrelationIdFilter;
import com.hotelai.stay.domain.ProjectionService;
import com.hotelai.stay.domain.ProjectionService.ServiceUsageFinancialEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ServiceUsageEventConsumer {
    private final ObjectMapper objectMapper;
    private final ProjectionService projections;

    public ServiceUsageEventConsumer(
            ObjectMapper objectMapper, ProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @RabbitListener(queues = "${hotel.messaging.service-usage-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        validate(envelope);
        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            ServiceUsageFinancialEvent value =
                    objectMapper.treeToValue(
                            envelope.payload(), ServiceUsageFinancialEvent.class);
            validate(value);
            projections.serviceUsage(
                    envelope.eventId(), envelope.eventType(), envelope.producer(), value);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void validate(EventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.payload() == null
                || envelope.schemaVersion() != 1
                || !"hotel-service-catalog".equals(envelope.producer())
                || envelope.eventType() == null
                || !envelope.eventType().startsWith("hotel-service.usage-")
                || !envelope.eventType().endsWith(".v1")) {
            throw new IllegalArgumentException("Invalid service usage event envelope");
        }
    }

    private static void validate(ServiceUsageFinancialEvent value) {
        if (value.usageId() == null
                || value.stayId() == null
                || value.serviceId() == null
                || value.serviceName() == null
                || value.quantity() == null
                || value.deltaAmount() == null
                || value.currency() == null
                || value.action() == null
                || value.changedBy() == null) {
            throw new IllegalArgumentException("Service usage event is incomplete");
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
