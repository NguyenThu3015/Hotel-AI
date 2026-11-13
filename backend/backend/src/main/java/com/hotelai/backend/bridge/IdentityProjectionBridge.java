package com.hotelai.backend.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelai.backend.account.Role;
import com.hotelai.backend.account.RoleRepository;
import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Configuration
class IdentityProjectionMessagingConfiguration {
  @Bean
  TopicExchange legacyIdentityExchange(@Value("${hotel.migration.identity-exchange}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  Queue legacyIdentityQueue(@Value("${hotel.migration.identity-queue}") String name) {
    return QueueBuilder.durable(name)
        .deadLetterExchange("")
        .deadLetterRoutingKey(name + ".dlq")
        .build();
  }

  @Bean
  Queue legacyIdentityDeadLetterQueue(@Value("${hotel.migration.identity-queue}") String name) {
    return QueueBuilder.durable(name + ".dlq").build();
  }

  @Bean
  Binding legacyIdentityBinding(
      @Qualifier("legacyIdentityQueue") Queue legacyIdentityQueue,
      TopicExchange legacyIdentityExchange) {
    return BindingBuilder.bind(legacyIdentityQueue).to(legacyIdentityExchange).with("identity.#");
  }
}

@Component
@RequiredArgsConstructor
class IdentityProjectionEventConsumer {
  private final ObjectMapper objectMapper;
  private final IdentityProjectionService projections;

  @RabbitListener(queues = "${hotel.migration.identity-queue}")
  void consume(String body) throws Exception {
    EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
    String correlationId =
        envelope.correlationId() == null
            ? envelope.eventId().toString()
            : envelope.correlationId().toString();
    try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
      projections.apply(envelope);
    }
  }
}

@Service
@RequiredArgsConstructor
class IdentityProjectionService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final IdentityProjectionInboxRepository inbox;

  @Transactional
  void apply(EventEnvelope event) {
    if (inbox.existsById(event.eventId())) return;
    switch (event.eventType()) {
      case "identity.account.activated.v1",
              "identity.staff.created.v1",
              "identity.staff.updated.v1",
              "identity.staff.status-changed.v1",
              "identity.staff.roles-changed.v1" ->
          upsert(event.payload());
      default -> {
        // Other identity/audit events are intentionally ignored by the legacy projection.
      }
    }
    inbox.save(new IdentityProjectionInbox(event.eventId(), event.eventType()));
  }

  private void upsert(JsonNode payload) {
    UUID identityId = identityId(payload);
    String email = requiredText(payload, "email");
    User user =
        users
            .findByIdentityId(identityId)
            .or(() -> users.findByEmailIgnoreCase(email))
            .orElseGet(
                () ->
                    new User(
                        email,
                        "!IDENTITY-SERVICE-MANAGED!",
                        text(payload, "fullName", "Hotel user"),
                        nullableText(payload, "phone"),
                        role("CUSTOMER")));
    Set<Role> assigned = roleSet(payload);
    user.synchronizeIdentity(
        identityId,
        email,
        text(payload, "fullName", user.getFullName()),
        nullableText(payload, "phone") == null ? user.getPhone() : nullableText(payload, "phone"),
        text(payload, "status", "ACTIVE"),
        payload.path("emailVerified").asBoolean(true),
        assigned);
    users.save(user);
  }

  private Set<Role> roleSet(JsonNode payload) {
    Set<Role> result = new HashSet<>();
    JsonNode values = payload.path("roles");
    if (values.isArray()) {
      values.forEach(value -> roles.findByName(value.asText()).ifPresent(result::add));
    }
    if (result.isEmpty() && "CUSTOMER".equals(text(payload, "accountType", "CUSTOMER"))) {
      result.add(role("CUSTOMER"));
    }
    return result;
  }

  private Role role(String code) {
    return roles
        .findByName(code)
        .orElseThrow(() -> new IllegalStateException("Legacy role is not seeded: " + code));
  }

  private UUID identityId(JsonNode payload) {
    String value = nullableText(payload, "identityId");
    if (value == null) value = requiredText(payload, "accountId");
    return UUID.fromString(value);
  }

  private String requiredText(JsonNode payload, String name) {
    String value = nullableText(payload, name);
    if (value == null) throw new IllegalArgumentException("Missing identity event field: " + name);
    return value;
  }

  private String text(JsonNode payload, String name, String fallback) {
    String value = nullableText(payload, name);
    return value == null ? fallback : value;
  }

  private String nullableText(JsonNode payload, String name) {
    JsonNode value = payload.get(name);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }
}

record EventEnvelope(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    String aggregateType,
    UUID aggregateId,
    int schemaVersion,
    JsonNode payload) {}

@Entity
@Table(name = "identity_projection_inbox")
@Getter
@NoArgsConstructor
class IdentityProjectionInbox {
  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 150)
  private String eventType;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  IdentityProjectionInbox(UUID eventId, String eventType) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = Instant.now();
  }
}

interface IdentityProjectionInboxRepository extends JpaRepository<IdentityProjectionInbox, UUID> {}
