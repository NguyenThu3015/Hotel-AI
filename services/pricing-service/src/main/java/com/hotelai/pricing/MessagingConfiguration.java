package com.hotelai.pricing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Configuration
class PricingMessagingConfiguration {
  @Bean
  TopicExchange pricingEventExchange(@Value("${hotel.messaging.exchange}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  TopicExchange pricingDeadLetterExchange(
      @Value("${hotel.messaging.dead-letter-exchange}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  Queue pricingInventoryQueue(
      @Value("${hotel.messaging.inventory-queue}") String name,
      @Value("${hotel.messaging.dead-letter-exchange}") String deadLetterExchange,
      @Value("${hotel.messaging.dead-letter-routing-key}") String deadLetterRoutingKey) {
    return QueueBuilder.durable(name)
        .deadLetterExchange(deadLetterExchange)
        .deadLetterRoutingKey(deadLetterRoutingKey)
        .build();
  }

  @Bean
  Queue pricingDeadLetterQueue(@Value("${hotel.messaging.dead-letter-queue}") String name) {
    return QueueBuilder.durable(name).build();
  }

  @Bean
  Binding pricingInventoryBinding(
      Queue pricingInventoryQueue,
      TopicExchange pricingEventExchange,
      @Value("${hotel.messaging.inventory-routing-key}") String routingKey) {
    return BindingBuilder.bind(pricingInventoryQueue).to(pricingEventExchange).with(routingKey);
  }

  @Bean
  Binding pricingDeadLetterBinding(
      Queue pricingDeadLetterQueue,
      TopicExchange pricingDeadLetterExchange,
      @Value("${hotel.messaging.dead-letter-routing-key}") String routingKey) {
    return BindingBuilder.bind(pricingDeadLetterQueue)
        .to(pricingDeadLetterExchange)
        .with(routingKey);
  }
}

@Component
class PricingOutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(PricingOutboxPublisher.class);

  private final OutboxEventRepository events;
  private final RabbitTemplate rabbit;
  private final ObjectMapper objectMapper;
  private final String exchange;

  PricingOutboxPublisher(
      OutboxEventRepository events,
      RabbitTemplate rabbit,
      ObjectMapper objectMapper,
      @Value("${hotel.messaging.exchange}") String exchange) {
    this.events = events;
    this.rabbit = rabbit;
    this.objectMapper = objectMapper;
    this.exchange = exchange;
  }

  @Scheduled(fixedDelayString = "${hotel.messaging.outbox-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    events.findPending(PageRequest.of(0, 50)).forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", event.getId());
      envelope.put("eventType", event.getEventType());
      envelope.put("occurredAt", event.getOccurredAt());
      envelope.put("producer", "pricing-service");
      envelope.put("correlationId", event.getCorrelationId());
      envelope.put("aggregateType", event.getAggregateType());
      envelope.put("aggregateId", event.getAggregateId());
      envelope.put("schemaVersion", event.getSchemaVersion());
      envelope.put("payload", objectMapper.readTree(event.getPayload()));
      CorrelationData confirmation = new CorrelationData(event.getId().toString());
      rabbit.convertAndSend(
          exchange,
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
          confirmation);
      CorrelationData.Confirm result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
      if (!result.ack()) {
        throw new IllegalStateException("RabbitMQ rejected event: " + result.reason());
      }
      if (confirmation.getReturned() != null) {
        throw new IllegalStateException(
            "RabbitMQ returned unroutable event "
                + event.getEventType()
                + ": "
                + confirmation.getReturned().getReplyText());
      }
      event.markPublished();
    } catch (Exception exception) {
      event.markFailed(exception.getMessage());
      log.warn("Cannot publish pricing outbox event {}", event.getId(), exception);
    }
  }
}

@Component
class InventoryRoomTypeEventConsumer {
  private final ObjectMapper objectMapper;
  private final InboxEventRepository inbox;
  private final RoomTypeReferenceRepository roomTypes;

  InventoryRoomTypeEventConsumer(
      ObjectMapper objectMapper,
      InboxEventRepository inbox,
      RoomTypeReferenceRepository roomTypes) {
    this.objectMapper = objectMapper;
    this.inbox = inbox;
    this.roomTypes = roomTypes;
  }

  @RabbitListener(queues = "${hotel.messaging.inventory-queue}")
  @Transactional
  public void consume(String body) throws Exception {
    EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
    if (envelope.eventId() == null || envelope.eventId().isBlank()) {
      throw new IllegalArgumentException("Inventory eventId is required");
    }
    if (inbox.existsById(envelope.eventId())) {
      return;
    }
    if (envelope.correlationId() != null) {
      MDC.put(CorrelationContext.MDC_KEY, envelope.correlationId());
    }
    try {
      if (isSupported(envelope.eventType())) {
        project(envelope);
      }
      inbox.save(new InboxEvent(envelope.eventId(), envelope.eventType()));
    } finally {
      MDC.remove(CorrelationContext.MDC_KEY);
    }
  }

  private void project(EventEnvelope envelope) throws Exception {
    RoomTypePayload payload = objectMapper.treeToValue(envelope.payload(), RoomTypePayload.class);
    UUID roomTypeId =
        payload.roomTypeId() != null
            ? payload.roomTypeId()
            : UUID.fromString(envelope.aggregateId());
    RoomTypeReference existing = roomTypes.findById(roomTypeId).orElse(null);
    String code =
        value(
            payload.code(),
            existing == null
                ? "ROOM-" + roomTypeId.toString().substring(0, 8)
                : existing.getCode());
    String name = value(payload.name(), existing == null ? code : existing.getName());
    boolean active =
        envelope.eventType().contains("deactivated")
            ? false
            : payload.active() != null ? payload.active() : existing == null || existing.isActive();
    long sourceVersion =
        payload.version() == null
            ? (envelope.occurredAt() == null ? 0L : envelope.occurredAt().toEpochMilli())
            : payload.version();
    if (existing == null) {
      roomTypes.save(new RoomTypeReference(roomTypeId, code, name, active, sourceVersion));
    } else {
      existing.synchronize(code, name, active, sourceVersion);
    }
  }

  private static boolean isSupported(String eventType) {
    return "inventory.room-type.created.v1".equals(eventType)
        || "inventory.room-type.updated.v1".equals(eventType)
        || "inventory.room-type.status-changed.v1".equals(eventType)
        || "inventory.room-type.deactivated.v1".equals(eventType);
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record EventEnvelope(
      String eventId,
      String eventType,
      Instant occurredAt,
      String producer,
      String correlationId,
      String aggregateType,
      String aggregateId,
      int schemaVersion,
      JsonNode payload) {}

  private record RoomTypePayload(
      UUID roomTypeId, String code, String name, Boolean active, Long version) {}
}
