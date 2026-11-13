package com.hotelai.notification.messaging;

import com.hotelai.notification.api.NotificationDtos;
import com.hotelai.notification.domain.NotificationApplicationService;
import com.hotelai.notification.domain.NotificationChannel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationEventConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationApplicationService service;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationApplicationService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = "${hotel.notification.queue}")
    public void consume(String body) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
        if (envelope.correlationId() != null) MDC.put("correlationId", envelope.correlationId());
        try {
            NotificationDtos.EnqueueRequest request;
            if ("notification.requested.v1".equals(envelope.eventType())) {
                NotificationPayload payload = objectMapper.treeToValue(envelope.payload(), NotificationPayload.class);
                request = new NotificationDtos.EnqueueRequest(
                        envelope.eventId(), envelope.eventType(), envelope.correlationId(), payload.recipientIdentityId(),
                        payload.templateCode(), payload.channel(), payload.recipient(), value(payload.locale(), "vi"),
                        payload.variables(), payload.scheduledAt(), payload.maxAttempts(),
                        payload.deduplicationKey(), payload.consentRequired());
            } else if ("notification.otp.requested.v1".equals(envelope.eventType())
                    || "identity.password.reset-requested.v1".equals(envelope.eventType())) {
                OtpPayload payload = objectMapper.treeToValue(envelope.payload(), OtpPayload.class);
                NotificationChannel channel = payload.recipient().contains("@")
                        ? NotificationChannel.EMAIL : NotificationChannel.SMS;
                request = new NotificationDtos.EnqueueRequest(
                        envelope.eventId(), envelope.eventType(), envelope.correlationId(), null,
                        value(payload.templateCode(), defaultOtpTemplate(payload.purpose())), channel,
                        payload.recipient(), "vi", payload.variables(), null, 5,
                        envelope.eventId(), false);
            } else {
                throw new IllegalArgumentException("Unsupported notification event: " + envelope.eventType());
            }
            service.enqueue(request);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static String defaultOtpTemplate(String purpose) {
        return switch (purpose == null ? "" : purpose) {
            case "PASSWORD_RESET" -> "OTP_PASSWORD_RESET";
            case "LOGIN_MFA" -> "OTP_LOGIN_MFA";
            default -> "OTP_ACCOUNT_ACTIVATION";
        };
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record EventEnvelope(
            String eventId, String eventType, Instant occurredAt, String producer, String correlationId,
            String aggregateType, String aggregateId, int schemaVersion, JsonNode payload) {}

    private record NotificationPayload(
            UUID notificationId, UUID recipientIdentityId, NotificationChannel channel, String recipient,
            String templateCode, String locale, Map<String, Object> variables, Instant scheduledAt,
            Integer maxAttempts, String deduplicationKey, Boolean consentRequired) {}

    private record OtpPayload(
            UUID challengeId, String purpose, String recipient, String templateCode,
            Map<String, Object> variables, Instant expiresAt) {}
}
