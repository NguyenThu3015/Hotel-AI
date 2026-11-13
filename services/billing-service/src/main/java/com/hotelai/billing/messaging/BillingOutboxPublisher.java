package com.hotelai.billing.messaging;

import com.hotelai.billing.config.BillingProperties;
import com.hotelai.billing.domain.BillingOutboxEvent;
import com.hotelai.billing.domain.BillingOutboxRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class BillingOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(BillingOutboxPublisher.class);
    private final BillingOutboxRepository events;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final BillingProperties properties;

    public BillingOutboxPublisher(
            BillingOutboxRepository events,
            RabbitTemplate rabbit,
            ObjectMapper objectMapper,
            BillingProperties properties) {
        this.events = events;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hotel.billing.messaging.outbox-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        events.findPending(PageRequest.of(0, 50)).forEach(this::publish);
    }

    private void publish(BillingOutboxEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", event.getId());
            envelope.put("eventType", event.getEventType());
            envelope.put("occurredAt", event.getOccurredAt());
            envelope.put("producer", "billing-service");
            envelope.put("correlationId", event.getCorrelationId());
            envelope.put("aggregateType", event.getAggregateType());
            envelope.put("aggregateId", event.getAggregateId());
            envelope.put("schemaVersion", event.getSchemaVersion());
            envelope.put("payload", objectMapper.readTree(event.getPayload()));

            CorrelationData confirmation = new CorrelationData(event.getId().toString());
            rabbit.convertAndSend(
                    properties.messaging().exchange(),
                    event.getEventType(),
                    objectMapper.writeValueAsString(envelope),
                    message -> {
                        message.getMessageProperties().setMessageId(event.getId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setHeader(
                                "eventType", event.getEventType());
                        message.getMessageProperties().setHeader(
                                "schemaVersion", event.getSchemaVersion());
                        message.getMessageProperties().setTimestamp(
                                java.util.Date.from(event.getOccurredAt()));
                        return message;
                    },
                    confirmation);
            CorrelationData.Confirm result =
                    confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.ack()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected event: " + result.reason());
            }
            if (confirmation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned unroutable event: "
                                + confirmation.getReturned().getReplyText());
            }
            event.markPublished();
        } catch (Exception error) {
            event.markFailed(error.getMessage());
            log.warn("Cannot publish billing outbox event {}", event.getId(), error);
        }
    }
}
