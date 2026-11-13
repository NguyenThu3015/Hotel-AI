package com.hotelai.stay.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class StayEventService {
    private final StayOutboxRepository outbox;
    private final StayFolioEntryRepository folio;
    private final ObjectMapper objectMapper;

    StayEventService(
            StayOutboxRepository outbox,
            StayFolioEntryRepository folio,
            ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.folio = folio;
        this.objectMapper = objectMapper;
    }

    void append(Stay stay, String eventType, List<RoomAssignment> assignments, StayOperation operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stayId", stay.getId());
        payload.put("reservationId", stay.getReservationId());
        payload.put("customerId", stay.getCustomerId());
        payload.put("reservationReference", stay.getReservationReference());
        payload.put("contactName", stay.getContactName());
        payload.put("contactEmail", stay.getContactEmail());
        payload.put("contactPhone", stay.getContactPhone());
        payload.put("status", stay.getStatus().name());
        payload.put("scheduledCheckIn", stay.getScheduledCheckIn());
        payload.put("scheduledCheckOut", stay.getScheduledCheckOut());
        payload.put("actualCheckIn", stay.getActualCheckIn());
        payload.put("actualCheckOut", stay.getActualCheckOut());
        payload.put("roomIds", assignments.stream().map(RoomAssignment::getRoomId).toList());
        payload.put("roomNumbers", assignments.stream().map(RoomAssignment::getRoomNumber).toList());
        payload.put("currency", stay.getCurrency());
        payload.put("chargesTotal", stay.getChargesTotal());
        payload.put("paidTotal", stay.getPaidTotal());
        payload.put("balance", stay.balance());
        payload.put("folioEntries", folio.findByStayIdOrderByRecordedAtAsc(stay.getId()).stream()
                .map(entry -> Map.of(
                        "entryId", entry.getId(),
                        "sourceId", entry.getOperationId(),
                        "type", entry.getEntryType(),
                        "description", entry.getDescription(),
                        "amount", entry.getAmount(),
                        "currency", entry.getCurrency(),
                        "recordedBy", entry.getRecordedBy(),
                        "recordedAt", entry.getRecordedAt()))
                .toList());
        if (operation != null) payload.put("operationId", operation.getId());
        try {
            outbox.save(new StayOutboxEvent(
                    "Stay", stay.getId(), eventType, objectMapper.writeValueAsString(payload)));
            appendTransactionalEmail(stay, eventType, payload, operation);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize stay event", error);
        }
    }

    private void appendTransactionalEmail(
            Stay stay,
            String eventType,
            Map<String, Object> snapshot,
            StayOperation operation) throws Exception {
        String templateCode = switch (eventType) {
            case "stay.checked-in.v1" -> "CHECK_IN_COMPLETED";
            case "stay.room-changed.v1" -> "ROOM_CHANGED";
            case "stay.extended.v1" -> "STAY_EXTENDED";
            case "stay.checkout-payment-pending.v1" -> "CHECKOUT_PAYMENT_PENDING";
            case "stay.checked-out.v1" -> "CHECK_OUT_COMPLETED";
            default -> null;
        };
        if (templateCode == null || !validEmail(stay.getContactEmail())) return;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", stay.getContactName());
        variables.put("bookingCode", stay.getReservationReference());
        variables.put("status", stay.getStatus().name());
        variables.put("scheduledCheckIn", stay.getScheduledCheckIn());
        variables.put("scheduledCheckOut", stay.getScheduledCheckOut());
        variables.put("actualCheckIn", stay.getActualCheckIn());
        variables.put("actualCheckOut", stay.getActualCheckOut());
        variables.put("roomNumbers", snapshot.get("roomNumbers"));
        variables.put("chargesTotal", stay.getChargesTotal());
        variables.put("paidTotal", stay.getPaidTotal());
        variables.put("balance", stay.balance());
        variables.put("currency", stay.getCurrency());

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("notificationId", UUID.randomUUID());
        notification.put("recipientIdentityId", stay.getCustomerId());
        notification.put("channel", "EMAIL");
        notification.put("recipient", stay.getContactEmail().trim());
        notification.put("templateCode", templateCode);
        notification.put("locale", "vi");
        notification.put("variables", variables);
        notification.put("maxAttempts", 5);
        notification.put("consentRequired", false);
        notification.put(
                "deduplicationKey",
                "stay-email:" + eventType + ":" + stay.getId() + ":"
                        + (operation == null ? stay.getVersion() : operation.getId()));
        outbox.save(new StayOutboxEvent(
                "Stay", stay.getId(), "notification.requested.v1",
                objectMapper.writeValueAsString(notification)));
    }

    private static boolean validEmail(String value) {
        return value != null
                && value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
