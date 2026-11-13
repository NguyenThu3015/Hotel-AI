package com.hotelai.notification.api;

import com.hotelai.notification.domain.NotificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record EnqueueRequest(
            @NotBlank @Size(max = 120) String eventId,
            @Size(max = 120) String eventType,
            @Size(max = 120) String correlationId,
            UUID recipientIdentityId,
            @NotBlank @Size(max = 100) String templateCode,
            @NotNull NotificationChannel channel,
            @NotBlank @Size(max = 500) String recipient,
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String locale,
            Map<String, Object> variables,
            @FutureOrPresent Instant scheduledAt,
            @Min(1) @Max(10) Integer maxAttempts,
            @Size(max = 200) String deduplicationKey,
            Boolean consentRequired) {}

    public record TemplateRequest(
            @NotBlank @Size(max = 100) String code,
            @NotNull NotificationChannel channel,
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String locale,
            @Size(max = 300) String subject,
            @NotBlank @Size(max = 50000) String body,
            boolean active) {}

    public record TemplateResponse(
            UUID id, String code, NotificationChannel channel, String locale, String subject, String body,
            boolean active, long version, Instant updatedAt) {}

    public record PreferenceRequest(
            @NotNull NotificationChannel channel,
            boolean enabled) {}

    public record PreferenceResponse(
            NotificationChannel channel,
            boolean enabled,
            Instant updatedAt) {}

    public record JobResponse(
            UUID id,
            String sourceEventId,
            String correlationId,
            UUID recipientIdentityId,
            NotificationChannel channel,
            String deduplicationKey,
            String recipient,
            String subject,
            String content,
            String status,
            int attemptCount,
            int maxAttempts,
            Instant nextAttemptAt,
            String lastError,
            String externalReference,
            Instant readAt,
            Instant createdAt,
            Instant updatedAt) {}
}
