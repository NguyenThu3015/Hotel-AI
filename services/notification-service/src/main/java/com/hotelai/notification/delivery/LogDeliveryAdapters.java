package com.hotelai.notification.delivery;

import com.hotelai.notification.domain.NotificationChannel;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

abstract class LogDeliveryAdapter implements DeliveryAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public DeliveryResult send(DeliveryRequest request) {
        String reference = "mock-" + channel().name().toLowerCase() + "-" + UUID.randomUUID();
        logger.info("Mock {} delivery reference={} recipient={} correlationId={}",
                channel(), reference, masked(request.recipient()), request.correlationId());
        return new DeliveryResult(reference);
    }

    private static String masked(String value) {
        return value == null || value.length() < 4 ? "***" : "***" + value.substring(value.length() - 4);
    }
}

@Component
@ConditionalOnProperty(name = "hotel.notification.email.mode", havingValue = "log", matchIfMissing = true)
class LogEmailAdapter extends LogDeliveryAdapter {
    @Override public NotificationChannel channel() { return NotificationChannel.EMAIL; }
}

@Component
class LogSmsAdapter extends LogDeliveryAdapter {
    @Override public NotificationChannel channel() { return NotificationChannel.SMS; }
}

@Component
@ConditionalOnProperty(name = "hotel.notification.zalo.provider", havingValue = "log", matchIfMissing = true)
class LogZaloAdapter extends LogDeliveryAdapter {
    @Override public NotificationChannel channel() { return NotificationChannel.ZALO; }
}
