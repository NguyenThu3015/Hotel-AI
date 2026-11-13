package com.hotelai.payment.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel")
public record PaymentProperties(
        Security security,
        Payment payment,
        Messaging messaging,
        Providers providers) {

    public record Security(String internalKey) {}

    public record Payment(Duration intentTtl, Duration idempotencyTtl) {}

    public record Messaging(
            String exchange,
            String reservationQueue,
            String reservationRoutingKey,
            String stayQueue,
            String stayRoutingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey,
            long outboxDelayMs) {}

    public record Providers(Momo momo, Casso casso, Sandbox sandbox) {}

    public record Momo(
            boolean enabled,
            URI endpoint,
            URI queryEndpoint,
            URI refundEndpoint,
            URI refundQueryEndpoint,
            String partnerCode,
            String accessKey,
            String secretKey,
            URI redirectUrl,
            URI ipnUrl) {}

    public record Casso(
            boolean enabled,
            String webhookSecret,
            String bankId,
            String accountNumber,
            String accountName,
            URI qrBaseUrl,
            String qrTemplate,
            String transferPrefix,
            String timeZone) {}

    public record Sandbox(boolean enabled, URI checkoutBaseUrl) {}
}
