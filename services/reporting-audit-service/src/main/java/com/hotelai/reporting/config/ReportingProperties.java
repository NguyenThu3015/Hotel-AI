package com.hotelai.reporting.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel.reporting")
public record ReportingProperties(
        String currency,
        String timeZone,
        String internalKey,
        int maxRangeDays,
        int auditRetentionDays,
        Messaging messaging) {

    public ReportingProperties {
        currency = currency == null || currency.isBlank() ? "VND" : currency;
        timeZone = timeZone == null || timeZone.isBlank() ? "Asia/Ho_Chi_Minh" : timeZone;
        internalKey = internalKey == null ? "" : internalKey;
        maxRangeDays = maxRangeDays <= 0 ? 366 : maxRangeDays;
        auditRetentionDays = auditRetentionDays <= 0 ? 365 : auditRetentionDays;
        messaging = messaging == null ? Messaging.defaults() : messaging;
        ZoneId.of(timeZone);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public record Messaging(
            String exchange,
            String queue,
            String routingKey,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey) {
        static Messaging defaults() {
            return new Messaging(
                    "hotel.events",
                    "reporting-audit.domain-events.v1",
                    "#",
                    "hotel.events.dlx",
                    "reporting-audit.domain-events.dlq.v1",
                    "reporting-audit.dead-letter.v1");
        }
    }
}
