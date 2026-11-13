package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreference.Key.class)
public class NotificationPreference {
    @Id
    @Column(name = "identity_id")
    private UUID identityId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {}

    public NotificationPreference(
            UUID identityId, NotificationChannel channel, boolean enabled) {
        this.identityId = identityId;
        this.channel = channel;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void update(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getIdentityId() { return identityId; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Key implements Serializable {
        private UUID identityId;
        private NotificationChannel channel;

        public Key() {}

        public Key(UUID identityId, NotificationChannel channel) {
            this.identityId = identityId;
            this.channel = channel;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key value
                    && java.util.Objects.equals(identityId, value.identityId)
                    && channel == value.channel;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(identityId, channel);
        }
    }
}
