package com.hotelai.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
class OutboxService {
  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;

  void append(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload) {
    try {
      events.save(
          new OutboxEvent(
              aggregateType,
              aggregateId,
              eventType,
              CorrelationContext.current(),
              objectMapper.writeValueAsString(payload)));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot serialize outbox event", exception);
    }
  }
}

@Configuration
class IdentityMessagingConfiguration {
  @Bean
  TopicExchange identityEventExchange(IdentityProperties properties) {
    return new TopicExchange(properties.outbox().exchange(), true, false);
  }
}

@Component
@RequiredArgsConstructor
@Slf4j
class OutboxPublisher {
  private final OutboxEventRepository events;
  private final OutboxDeliveryRepository deliveries;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final IdentityProperties properties;

  @Scheduled(fixedDelayString = "${identity.outbox.fixed-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    events.findPending(PageRequest.of(0, properties.outbox().batchSize())).forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    OutboxDelivery delivery =
        deliveries.findById(event.getId()).orElseGet(() -> new OutboxDelivery(event.getId()));
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", event.getId());
      envelope.put("eventType", event.getEventType());
      envelope.put("occurredAt", event.getOccurredAt());
      envelope.put("producer", "identity-service");
      envelope.put("correlationId", event.getCorrelationId());
      envelope.put("aggregateType", event.getAggregateType());
      envelope.put("aggregateId", event.getAggregateId());
      envelope.put("schemaVersion", event.getSchemaVersion());
      envelope.put("payload", objectMapper.readTree(event.getPayload()));
      CorrelationData brokerConfirmation = new CorrelationData(event.getId().toString());
      rabbitTemplate.convertAndSend(
          properties.outbox().exchange(),
          event.getEventType(),
          objectMapper.writeValueAsString(envelope),
          message -> {
            message.getMessageProperties().setMessageId(event.getId().toString());
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setHeader("eventType", event.getEventType());
            message.getMessageProperties().setHeader("schemaVersion", event.getSchemaVersion());
            message.getMessageProperties().setTimestamp(java.util.Date.from(event.getOccurredAt()));
            return message;
          },
          brokerConfirmation);
      CorrelationData.Confirm confirmation =
          brokerConfirmation.getFuture().get(5, TimeUnit.SECONDS);
      if (!confirmation.ack()) {
        throw new IllegalStateException("RabbitMQ rejected event: " + confirmation.reason());
      }
      if (brokerConfirmation.getReturned() != null) {
        throw new IllegalStateException(
            "RabbitMQ returned unroutable event "
                + event.getEventType()
                + ": "
                + brokerConfirmation.getReturned().getReplyText());
      }
      delivery.succeeded();
    } catch (Exception exception) {
      delivery.failed(exception.getMessage());
      log.warn("Cannot publish outbox event {}", event.getId(), exception);
    }
    deliveries.save(delivery);
  }
}
