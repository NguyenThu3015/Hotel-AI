package com.hotelai.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel.billing")
public record BillingProperties(
        String timeZone,
        String legalName,
        String legalAddress,
        String taxCode,
        String pdfFontPath,
        Messaging messaging) {

    public record Messaging(
            String exchange,
            String reservationQueue,
            String reservationRoutingKey,
            String stayQueue,
            String stayRoutingKey,
            String paymentQueue,
            String paymentRoutingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey,
            long outboxDelayMs) {}
}
