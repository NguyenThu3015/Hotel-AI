package com.hotelai.payment.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class PaymentEventService {
    private final PaymentOutboxRepository outbox;
    private final ReservationPaymentProjectionRepository reservations;
    private final ObjectMapper objectMapper;

    PaymentEventService(
            PaymentOutboxRepository outbox,
            ReservationPaymentProjectionRepository reservations,
            ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.reservations = reservations;
        this.objectMapper = objectMapper;
    }

    void payment(PaymentIntent payment, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("reservationId", payment.getReservationId());
        payload.put("customerId", payment.getCustomerId());
        payload.put("purpose", payment.getPurpose().name());
        payload.put("provider", payment.getProvider().name());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("status", payment.getStatus().name());
        payload.put("providerReference", payment.getProviderTransactionId());
        payload.put("completedAt", payment.getCompletedAt());
        payload.put("reservationVersion", payment.getReservationVersionSnapshot());
        append("Payment", payment.getId(), eventType, payload);
        reservations.findById(payment.getReservationId())
                .ifPresent(reservation -> appendAutomaticPaymentEmail(payment, reservation, eventType));
    }

    void refund(PaymentIntent payment, RefundRequest refund, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refund.getId());
        payload.put("paymentId", payment.getId());
        payload.put("reservationId", payment.getReservationId());
        payload.put("customerId", payment.getCustomerId());
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrency());
        payload.put("status", refund.getStatus().name());
        payload.put("provider", payment.getProvider().name());
        payload.put("providerReference", refund.getProviderRefundId());
        append("Refund", refund.getId(), eventType, payload);
        if ("payment.refund-completed.v1".equals(eventType)) {
            reservations.findById(payment.getReservationId())
                    .ifPresent(reservation -> appendRefundEmail(payment, refund, reservation));
        }
    }

    private void appendAutomaticPaymentEmail(
            PaymentIntent payment,
            ReservationPaymentProjection reservation,
            String eventType) {
        String templateCode = switch (eventType) {
            case "payment.succeeded.v1" -> "PAYMENT_RECEIPT";
            case "payment.failed.v1" -> "PAYMENT_FAILED";
            case "payment.cancelled.v1" -> "PAYMENT_CANCELLED";
            default -> null;
        };
        if (templateCode == null || !validEmail(reservation.getContactEmail())) return;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", payment.getId());
        variables.put("reservationId", payment.getReservationId());
        variables.put("bookingCode", reservation.getReference());
        variables.put("amount", payment.getAmount());
        variables.put("currency", payment.getCurrency());
        variables.put("provider", payment.getProvider().name());
        variables.put("providerReference", value(payment.getProviderTransactionId()));
        variables.put("paidAt", value(payment.getCompletedAt()));
        variables.put("status", payment.getStatus().name());
        variables.put("receiptPath", "/api/v1/payments/" + payment.getId() + "/receipt/download");
        appendNotification(
                payment.getId(),
                payment.getCustomerId(),
                reservation.getContactEmail(),
                templateCode,
                variables,
                "payment-auto-email:" + eventType + ":" + payment.getId());
    }

    private void appendRefundEmail(
            PaymentIntent payment,
            RefundRequest refund,
            ReservationPaymentProjection reservation) {
        if (!validEmail(reservation.getContactEmail())) return;
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("refundId", refund.getId());
        variables.put("paymentId", payment.getId());
        variables.put("bookingCode", reservation.getReference());
        variables.put("amount", refund.getAmount());
        variables.put("currency", refund.getCurrency());
        variables.put("providerReference", value(refund.getProviderRefundId()));
        variables.put("refundedAt", value(refund.getCompletedAt()));
        appendNotification(
                refund.getId(),
                payment.getCustomerId(),
                reservation.getContactEmail(),
                "PAYMENT_REFUNDED",
                variables,
                "refund-auto-email:" + refund.getId());
    }

    void requestReceiptEmail(
            PaymentIntent payment, ReservationPaymentProjection reservation, UUID actorId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", payment.getId());
        variables.put("reservationId", payment.getReservationId());
        variables.put("bookingCode", reservation.getReference());
        variables.put("amount", payment.getAmount());
        variables.put("currency", payment.getCurrency());
        variables.put("provider", payment.getProvider().name());
        variables.put("providerReference", payment.getProviderTransactionId());
        variables.put("paidAt", payment.getCompletedAt());
        variables.put("receiptPath", "/api/v1/payments/" + payment.getId() + "/receipt/download");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", UUID.randomUUID());
        payload.put("recipientIdentityId", payment.getCustomerId());
        payload.put("channel", "EMAIL");
        payload.put("recipient", reservation.getContactEmail());
        payload.put("templateCode", "PAYMENT_RECEIPT");
        payload.put("locale", "vi");
        payload.put("variables", variables);
        payload.put("maxAttempts", 5);
        payload.put("deduplicationKey", "payment-receipt:" + payment.getId() + ":" + actorId);
        append("Payment", payment.getId(), "notification.requested.v1", payload);
    }

    private void appendNotification(
            UUID aggregateId,
            UUID customerId,
            String recipient,
            String templateCode,
            Map<String, Object> variables,
            String deduplicationKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", UUID.randomUUID());
        payload.put("recipientIdentityId", customerId);
        payload.put("channel", "EMAIL");
        payload.put("recipient", recipient.trim());
        payload.put("templateCode", templateCode);
        payload.put("locale", "vi");
        payload.put("variables", variables);
        payload.put("maxAttempts", 5);
        payload.put("consentRequired", false);
        payload.put("deduplicationKey", deduplicationKey);
        append("Payment", aggregateId, "notification.requested.v1", payload);
    }

    private static boolean validEmail(String value) {
        return value != null
                && value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private static String value(Object value) {
        return value == null ? "Chưa có" : value.toString();
    }

    private void append(String aggregateType, java.util.UUID aggregateId, String eventType, Map<String, ?> payload) {
        try {
            outbox.save(new PaymentOutboxEvent(
                    aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize payment event", error);
        }
    }
}
