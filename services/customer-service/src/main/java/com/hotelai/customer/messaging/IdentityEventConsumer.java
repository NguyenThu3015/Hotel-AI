package com.hotelai.customer.messaging;

import com.hotelai.customer.api.CustomerDtos;
import com.hotelai.customer.domain.CustomerApplicationService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Configuration
class IdentityEventRabbitConfig {
    @Bean
    TopicExchange customerEventsExchange(@Value("${hotel.messaging.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue accountActivatedQueue(@Value("${hotel.messaging.account-activated-queue}") String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    Queue accountActivatedDeadLetterQueue(
            @Value("${hotel.messaging.account-activated-queue}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    Binding accountActivatedBinding(
            @Qualifier("accountActivatedQueue") Queue accountActivatedQueue,
            TopicExchange customerEventsExchange,
            @Value("${hotel.messaging.account-activated-routing-key}") String routingKey) {
        return BindingBuilder.bind(accountActivatedQueue).to(customerEventsExchange).with(routingKey);
    }
}

@Component
public class IdentityEventConsumer {
    private final ObjectMapper objectMapper;
    private final CustomerApplicationService customers;

    public IdentityEventConsumer(ObjectMapper objectMapper, CustomerApplicationService customers) {
        this.objectMapper = objectMapper;
        this.customers = customers;
    }

    @RabbitListener(queues = "${hotel.messaging.account-activated-queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        if (!"identity.account.activated.v1".equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported identity event type: " + envelope.eventType());
        }
        if (envelope.correlationId() != null) MDC.put("correlationId", envelope.correlationId());
        try {
            AccountPayload payload = objectMapper.treeToValue(envelope.payload(), AccountPayload.class);
            customers.createFromIdentity(new CustomerDtos.IdentityCreatedRequest(
                    envelope.eventId(), payload.identityId(), value(payload.fullName(), "Customer"), payload.email(),
                    payload.phone(), true, payload.status(), payload.roles()));
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record EventEnvelope(
            String eventId, String eventType, Instant occurredAt, String producer, String correlationId,
            String aggregateType, String aggregateId, int schemaVersion, JsonNode payload) {}

    private record AccountPayload(
            UUID identityId, String email, String fullName, String phone, String status, Set<String> roles) {}
}
