package com.hotelai.notification.delivery;

import com.hotelai.notification.domain.NotificationChannel;

public interface DeliveryAdapter {
    NotificationChannel channel();
    DeliveryResult send(DeliveryRequest request);

    record DeliveryRequest(String recipient, String subject, String content, String correlationId) {}
    record DeliveryResult(String providerReference) {}
}
