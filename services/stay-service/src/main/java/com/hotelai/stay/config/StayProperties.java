package com.hotelai.stay.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel")
public record StayProperties(
        Security security,
        Clients clients,
        Stay stay,
        Messaging messaging) {

    public record Security(String internalKey, String documentEncryptionKey) {}

    public record Clients(
            String inventoryUrl,
            Duration connectTimeout,
            Duration readTimeout) {}

    public record Stay(
            String timeZone,
            int standardCheckInHour,
            int childMaximumAge,
            int primaryGuestMinimumAge,
            int maximumExtensionDays) {}

    public record Messaging(
            String exchange,
            String reservationQueue,
            String reservationRoutingKey,
            String inventoryQueue,
            String inventoryRoutingKey,
            String serviceUsageQueue,
            String serviceUsageRoutingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey,
            long outboxDelayMs,
            long sagaDelayMs) {}
}
