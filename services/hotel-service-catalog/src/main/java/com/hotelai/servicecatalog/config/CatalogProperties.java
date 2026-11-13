package com.hotelai.servicecatalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel")
public record CatalogProperties(
        String timeZone,
        Messaging messaging) {

    public record Messaging(
            String exchange,
            String stayQueue,
            String stayRoutingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey,
            long outboxDelayMs) {}
}
