package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_templates", uniqueConstraints =
        @UniqueConstraint(name = "uq_template_code_channel_locale", columnNames = {"code", "channel", "locale"}))
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 100)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    @Column(nullable = false, length = 10)
    private String locale;
    @Column(length = 300)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected NotificationTemplate() {}

    public NotificationTemplate(String code, NotificationChannel channel, String locale, String subject, String body) {
        this.code = code;
        this.channel = channel;
        this.locale = locale;
        this.subject = subject;
        this.body = body;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String subject, String body, boolean active) {
        this.subject = subject;
        this.body = body;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public NotificationChannel getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
