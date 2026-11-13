package com.hotelai.notification.domain;

import com.hotelai.notification.api.NotificationDtos;
import com.hotelai.notification.common.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationApplicationService {
    private final NotificationTemplateRepository templates;
    private final NotificationJobRepository jobs;
    private final InboxEventRepository inbox;
    private final NotificationPreferenceRepository preferences;
    private final TemplateRenderer renderer;

    public NotificationApplicationService(
            NotificationTemplateRepository templates,
            NotificationJobRepository jobs,
            InboxEventRepository inbox,
            NotificationPreferenceRepository preferences,
            TemplateRenderer renderer) {
        this.templates = templates;
        this.jobs = jobs;
        this.inbox = inbox;
        this.preferences = preferences;
        this.renderer = renderer;
    }

    @Transactional
    public NotificationDtos.JobResponse enqueue(NotificationDtos.EnqueueRequest request) {
        if (inbox.existsById(request.eventId())) {
            InboxEvent consumed = inbox.findById(request.eventId()).orElseThrow();
            return (consumed.getJobId() == null
                    ? jobs.findBySourceEventId(request.eventId())
                    : jobs.findById(consumed.getJobId()))
                    .map(this::toResponse)
                    .orElseThrow(() -> new ApiException(409, "Event was consumed without a matching notification job"));
        }
        String deduplicationKey = request.deduplicationKey() == null
                        || request.deduplicationKey().isBlank()
                ? request.eventId()
                : request.deduplicationKey().trim();
        NotificationJob duplicate = jobs.findByDeduplicationKey(deduplicationKey)
                .orElse(null);
        if (duplicate != null) {
            inbox.save(new InboxEvent(
                    request.eventId(),
                    request.eventType() == null
                            ? "notification.requested" : request.eventType(),
                    duplicate.getId()));
            return toResponse(duplicate);
        }
        String locale = request.locale() == null || request.locale().isBlank() ? "vi" : request.locale();
        NotificationTemplate template = templates
                .findByCodeAndChannelAndLocaleAndActiveTrue(request.templateCode(), request.channel(), locale)
                .orElseThrow(() -> new ApiException(422, "Active notification template not found"));
        String subject = renderer.render(template.getSubject(), request.variables());
        String content = renderer.render(template.getBody(), request.variables());
        validateRecipient(request.channel(), request.recipient());
        NotificationJob job = jobs.save(new NotificationJob(
                request.eventId(), deduplicationKey, request.correlationId(),
                request.recipientIdentityId(), request.channel(),
                request.recipient().trim(), subject, content,
                request.scheduledAt() == null ? Instant.now() : request.scheduledAt(),
                request.maxAttempts() == null ? 5 : request.maxAttempts()));
        if (Boolean.TRUE.equals(request.consentRequired())
                && !isEnabled(request.recipientIdentityId(), request.channel())) {
            job.cancel("RECIPIENT_OPTED_OUT");
        }
        inbox.save(new InboxEvent(
                request.eventId(),
                request.eventType() == null
                        ? "notification.requested" : request.eventType(),
                job.getId()));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public java.util.List<NotificationDtos.PreferenceResponse> preferences(
            UUID identityId) {
        java.util.Map<NotificationChannel, NotificationPreference> stored =
                preferences.findByIdentityIdOrderByChannel(identityId).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                NotificationPreference::getChannel,
                                value -> value));
        return java.util.Arrays.stream(NotificationChannel.values())
                .map(channel -> {
                    NotificationPreference value = stored.get(channel);
                    return new NotificationDtos.PreferenceResponse(
                            channel,
                            value != null && value.isEnabled(),
                            value == null ? null : value.getUpdatedAt());
                })
                .toList();
    }

    @Transactional
    public NotificationDtos.PreferenceResponse updatePreference(
            UUID identityId, NotificationDtos.PreferenceRequest request) {
        NotificationPreference value = preferences
                .findByIdentityIdAndChannel(identityId, request.channel())
                .orElseGet(() -> new NotificationPreference(
                        identityId, request.channel(), request.enabled()));
        value.update(request.enabled());
        value = preferences.save(value);
        return new NotificationDtos.PreferenceResponse(
                value.getChannel(), value.isEnabled(), value.getUpdatedAt());
    }

    @Transactional
    public NotificationDtos.TemplateResponse upsertTemplate(NotificationDtos.TemplateRequest request) {
        String locale = request.locale() == null || request.locale().isBlank() ? "vi" : request.locale();
        NotificationTemplate template = templates
                .findByCodeAndChannelAndLocale(request.code(), request.channel(), locale)
                .orElseGet(() -> new NotificationTemplate(
                        request.code().trim(), request.channel(), locale, request.subject(), request.body()));
        template.update(request.subject(), request.body(), request.active());
        return toResponse(templates.save(template));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.TemplateResponse> templates(Pageable pageable) {
        return templates.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.JobResponse> jobs(Pageable pageable) {
        return jobs.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.JobResponse> userInbox(UUID identityId, Pageable pageable) {
        return jobs.findByRecipientIdentityIdOrderByCreatedAtDesc(identityId, pageable).map(this::toResponse);
    }

    @Transactional
    public NotificationDtos.JobResponse markRead(UUID jobId, UUID identityId) {
        NotificationJob job = required(jobId);
        if (!identityId.equals(job.getRecipientIdentityId())) throw new ApiException(404, "Notification not found");
        job.markRead();
        return toResponse(job);
    }

    @Transactional
    public NotificationDtos.JobResponse retry(UUID jobId) {
        NotificationJob job = required(jobId);
        try {
            job.retryNow();
        } catch (IllegalStateException ex) {
            throw new ApiException(409, ex.getMessage());
        }
        return toResponse(job);
    }

    private NotificationJob required(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new ApiException(404, "Notification job not found"));
    }

    private static void validateRecipient(NotificationChannel channel, String recipient) {
        String value = recipient.trim();
        if (channel == NotificationChannel.EMAIL && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(400, "Invalid email recipient");
        }
        if (channel != NotificationChannel.EMAIL && !value.matches("^[0-9+._-]{6,100}$")) {
            throw new ApiException(400, "Invalid SMS/Zalo recipient identifier");
        }
    }

    public NotificationDtos.JobResponse toResponse(NotificationJob j) {
        return new NotificationDtos.JobResponse(
                j.getId(), j.getSourceEventId(), j.getCorrelationId(), j.getRecipientIdentityId(), j.getChannel(),
                j.getDeduplicationKey(),
                maskRecipient(j.getChannel(), j.getRecipient()), j.getSubject(), j.getContent(), j.getStatus().name(),
                j.getAttemptCount(), j.getMaxAttempts(), j.getNextAttemptAt(), j.getLastError(),
                j.getExternalReference(), j.getReadAt(), j.getCreatedAt(), j.getUpdatedAt());
    }

    private boolean isEnabled(UUID identityId, NotificationChannel channel) {
        return identityId != null
                && preferences.findByIdentityIdAndChannel(identityId, channel)
                    .map(NotificationPreference::isEnabled)
                    .orElse(false);
    }

    private NotificationDtos.TemplateResponse toResponse(NotificationTemplate t) {
        return new NotificationDtos.TemplateResponse(
                t.getId(), t.getCode(), t.getChannel(), t.getLocale(), t.getSubject(), t.getBody(), t.isActive(),
                t.getVersion(), t.getUpdatedAt());
    }

    private static String maskRecipient(NotificationChannel channel, String value) {
        if (channel == NotificationChannel.EMAIL) {
            int at = value.indexOf('@');
            return at > 0 ? value.substring(0, 1) + "***" + value.substring(at) : "***";
        }
        return value.length() > 4 ? "***" + value.substring(value.length() - 4) : "****";
    }
}
