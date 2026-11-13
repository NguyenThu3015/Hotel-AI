package com.hotelai.reservation.domain;

import com.hotelai.reservation.config.CorrelationIdFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReservationEventService {
    private final OutboxEventRepository outbox;
    private final InboxEventRepository inbox;
    private final ObjectMapper objectMapper;

    public ReservationEventService(
            OutboxEventRepository outbox, InboxEventRepository inbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
    }

    void append(
            Reservation reservation,
            String eventType,
            boolean existingAggregateMutation,
            Map<String, ?> additions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservation.getId());
        payload.put("reference", reservation.getReference());
        payload.put("customerId", reservation.getCustomerId());
        payload.put("createdByIdentityId", reservation.getCreatedByIdentityId());
        payload.put("channel", reservation.getChannel().name());
        payload.put("contactName", reservation.getContactName());
        payload.put("contactEmail", reservation.getContactEmail());
        payload.put("contactPhone", reservation.getContactPhone());
        payload.put("status", reservation.getStatus().name());
        payload.put("version", reservation.getVersion() + (existingAggregateMutation ? 1 : 0));
        payload.put("roomTypeId", reservation.getRoomTypeId());
        payload.put("checkIn", reservation.getCheckIn());
        payload.put("checkOut", reservation.getCheckOut());
        payload.put("roomCount", reservation.getRoomCount());
        payload.put("adults", reservation.getAdults());
        payload.put("children", reservation.getChildren());
        payload.put("currency", reservation.getCurrency());
        payload.put("subtotal", reservation.getSubtotal());
        payload.put("discountAmount", reservation.getDiscountAmount());
        payload.put("taxAmount", reservation.getTaxAmount());
        payload.put("totalAmount", reservation.getTotalAmount());
        payload.put("depositRequired", reservation.getDepositRequired());
        payload.put("paidAmount", reservation.getPaidAmount());
        payload.put("pricingQuoteId", reservation.getPricingQuoteId());
        payload.put("pricingVersion", reservation.getPricingVersion());
        payload.put("priceBreakdown", readTree(reservation.getPriceBreakdown()));
        payload.put("inventoryHoldId", reservation.getInventoryHoldId());
        payload.put("cancellationPolicy", readTree(reservation.getCancellationPolicy()));
        payload.put("createdAt", reservation.getCreatedAt());
        payload.put("updatedAt", reservation.getUpdatedAt());
        payload.put("confirmedAt", reservation.getConfirmedAt());
        payload.put("cancelledAt", reservation.getCancelledAt());
        payload.putAll(additions);
        outbox.save(new OutboxEvent(eventType, reservation.getId(), correlation(), write(payload)));
        appendTransactionalEmail(reservation, eventType, payload, additions);
    }

    private void appendTransactionalEmail(
            Reservation reservation,
            String eventType,
            Map<String, Object> snapshot,
            Map<String, ?> additions) {
        String templateCode = switch (eventType) {
            case "reservation.created.v1" ->
                    reservation.getStatus() == ReservationStatus.CONFIRMED
                            ? null : "BOOKING_RECEIVED";
            case "reservation.confirmed.v1" -> "BOOKING_CONFIRMED";
            case "reservation.changed.v1" ->
                    additions.containsKey("reason") ? null : "BOOKING_CHANGED";
            case "reservation.cancelled.v1" -> "BOOKING_CANCELLED";
            case "reservation.rejected.v1" -> "BOOKING_REJECTED";
            case "reservation.hold-expired.v1" -> "BOOKING_EXPIRED";
            case "reservation.payment-failed.v1" -> "PAYMENT_FAILED";
            default -> null;
        };
        if (templateCode == null || !validEmail(reservation.getContactEmail())) return;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", reservation.getContactName());
        variables.put("bookingCode", reservation.getReference());
        variables.put("status", reservation.getStatus().name());
        variables.put("checkIn", reservation.getCheckIn());
        variables.put("checkOut", reservation.getCheckOut());
        variables.put("roomCount", reservation.getRoomCount());
        variables.put("adults", reservation.getAdults());
        variables.put("children", reservation.getChildren());
        variables.put("totalAmount", reservation.getTotalAmount());
        variables.put("paidAmount", reservation.getPaidAmount());
        variables.put("currency", reservation.getCurrency());
        variables.putAll(additions);

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("notificationId", UUID.randomUUID());
        notification.put("recipientIdentityId", reservation.getCustomerId());
        notification.put("channel", "EMAIL");
        notification.put("recipient", reservation.getContactEmail().trim());
        notification.put("templateCode", templateCode);
        notification.put("locale", "vi");
        notification.put("variables", variables);
        notification.put("maxAttempts", 5);
        notification.put("consentRequired", false);
        notification.put(
                "deduplicationKey",
                "reservation-email:"
                        + eventType + ":" + reservation.getId() + ":" + snapshot.get("version"));
        outbox.save(new OutboxEvent(
                "notification.requested.v1",
                reservation.getId(),
                correlation(),
                write(notification)));
    }

    private static boolean validEmail(String value) {
        return value != null
                && value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public boolean inboxContains(UUID eventId) {
        return inbox.existsById(eventId);
    }

    public void startInbox(
            UUID eventId,
            String eventType,
            String producer,
            UUID aggregateId,
            String correlationId) {
        inbox.save(new InboxEvent(eventId, eventType, producer, aggregateId, correlationId));
    }

    public void finishInbox(UUID eventId) {
        inbox.findById(eventId).orElseThrow().processed();
    }

    private String correlation() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        return current == null ? UUID.randomUUID().toString() : current;
    }

    private Object readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot deserialize event snapshot", error);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize outbox event", error);
        }
    }
}
