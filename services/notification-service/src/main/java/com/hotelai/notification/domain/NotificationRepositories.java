package com.hotelai.notification.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByCodeAndChannelAndLocaleAndActiveTrue(
            String code, NotificationChannel channel, String locale);
    Optional<NotificationTemplate> findByCodeAndChannelAndLocale(String code, NotificationChannel channel, String locale);
}

interface InboxEventRepository extends JpaRepository<InboxEvent, String> {}

interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, NotificationPreference.Key> {
    java.util.List<NotificationPreference> findByIdentityIdOrderByChannel(UUID identityId);
    Optional<NotificationPreference> findByIdentityIdAndChannel(
            UUID identityId, NotificationChannel channel);
}
