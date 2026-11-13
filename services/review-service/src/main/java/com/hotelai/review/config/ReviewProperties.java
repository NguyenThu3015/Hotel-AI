package com.hotelai.review.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel.review")
public record ReviewProperties(
        int minimumCommentLength,
        int maximumCommentLength,
        Duration spamWindow,
        List<String> prohibitedTerms,
        Messaging messaging) {

    public record Messaging(
            String exchange,
            String stayQueue,
            String stayRoutingKey,
            String customerQueue,
            String customerRoutingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey,
            long outboxDelayMs) {}
}
