package com.hotelai.billing.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class BillingEventService {
    private final BillingOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    BillingEventService(BillingOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    void invoiceIssued(Invoice invoice) {
        Map<String, Object> payload = invoicePayload(invoice);
        append("Invoice", invoice.getId(), "billing.invoice-issued.v1", payload);
        appendInvoiceEmail(
                invoice,
                "INVOICE_ISSUED",
                invoiceVariables(invoice),
                "invoice-auto-email:" + invoice.getId());
    }

    void correctionIssued(Invoice invoice, InvoiceCorrection correction) {
        Map<String, Object> payload = invoicePayload(invoice);
        payload.put("correctionId", correction.getId());
        payload.put("correctionNumber", correction.getCorrectionNumber());
        payload.put("correctionType", correction.getType().name());
        payload.put("amountDelta", correction.getAmountDelta());
        payload.put("reason", correction.getReason());
        payload.put("correctionIssuedAt", correction.getIssuedAt());
        append("InvoiceCorrection", correction.getId(),
                "billing.invoice-correction-issued.v1", payload);

        Map<String, Object> variables = invoiceVariables(invoice);
        variables.put("correctionNumber", correction.getCorrectionNumber());
        variables.put("correctionType", correction.getType().name());
        variables.put("amountDelta", correction.getAmountDelta());
        variables.put("reason", correction.getReason());
        variables.put("correctionIssuedAt", correction.getIssuedAt());
        appendInvoiceEmail(
                invoice,
                "INVOICE_CORRECTION",
                variables,
                "invoice-correction-auto-email:" + correction.getId());
    }

    void emailRequested(
            Invoice invoice, InvoiceDeliveryRequest delivery, UUID actorId) {
        Map<String, Object> variables = invoiceVariables(invoice);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", UUID.randomUUID());
        payload.put("recipientIdentityId", invoice.getCustomerId());
        payload.put("channel", "EMAIL");
        payload.put("recipient", delivery.getRecipient());
        payload.put("templateCode", "INVOICE_ISSUED");
        payload.put("locale", "vi");
        payload.put("variables", variables);
        payload.put("maxAttempts", 5);
        payload.put("deduplicationKey", "invoice-email:" + delivery.getId() + ":" + actorId);
        append("Invoice", invoice.getId(), "notification.requested.v1", payload);
    }

    private void appendInvoiceEmail(
            Invoice invoice,
            String templateCode,
            Map<String, Object> variables,
            String deduplicationKey) {
        if (!validEmail(invoice.getBuyerEmail())) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", UUID.randomUUID());
        payload.put("recipientIdentityId", invoice.getCustomerId());
        payload.put("channel", "EMAIL");
        payload.put("recipient", invoice.getBuyerEmail().trim());
        payload.put("templateCode", templateCode);
        payload.put("locale", "vi");
        payload.put("variables", variables);
        payload.put("maxAttempts", 5);
        payload.put("consentRequired", false);
        payload.put("deduplicationKey", deduplicationKey);
        append("Invoice", invoice.getId(), "notification.requested.v1", payload);
    }

    private static Map<String, Object> invoiceVariables(Invoice invoice) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("buyerName", invoice.getBuyerName());
        variables.put("invoiceId", invoice.getId());
        variables.put("invoiceNumber", invoice.getInvoiceNumber());
        variables.put("reservationReference", invoice.getReservationReference());
        variables.put("totalAmount", invoice.getTotalAmount());
        variables.put("currency", invoice.getCurrency());
        variables.put("issuedAt", invoice.getIssuedAt());
        variables.put(
                "downloadPath",
                "/api/v1/admin/invoices/" + invoice.getId() + "/download");
        return variables;
    }

    private static boolean validEmail(String value) {
        return value != null
                && value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private static Map<String, Object> invoicePayload(Invoice invoice) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceId", invoice.getId());
        payload.put("invoiceNumber", invoice.getInvoiceNumber());
        payload.put("stayId", invoice.getStayId());
        payload.put("reservationId", invoice.getReservationId());
        payload.put("customerId", invoice.getCustomerId());
        payload.put("status", invoice.getStatus().name());
        payload.put("currency", invoice.getCurrency());
        payload.put("totalAmount", invoice.getTotalAmount());
        payload.put("paidAmount", invoice.getPaidAmount());
        payload.put("balance", invoice.getBalance());
        payload.put("issuedAt", invoice.getIssuedAt());
        return payload;
    }

    private void append(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload) {
        try {
            outbox.save(new BillingOutboxEvent(
                    aggregateType,
                    aggregateId,
                    eventType,
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize billing event", error);
        }
    }
}
