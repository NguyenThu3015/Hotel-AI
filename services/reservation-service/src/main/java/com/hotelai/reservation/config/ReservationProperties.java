package com.hotelai.reservation.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hotel")
public record ReservationProperties(
        Security security,
        Clients clients,
        Reservation reservation,
        Messaging messaging) {

    public record Security(String internalKey) {}

    public record Clients(String inventoryUrl, String pricingUrl, Duration connectTimeout, Duration readTimeout) {}

    public record Reservation(Duration holdDuration, int maximumStayNights, Duration changeCutoff) {}

    public record Messaging(
            String exchange,
            String paymentQueue,
            String paymentRoutingKey,
            String stayQueue,
            List<String> stayRoutingKeys,
            long outboxDelayMs,
            long holdExpiryDelayMs) {}
}
