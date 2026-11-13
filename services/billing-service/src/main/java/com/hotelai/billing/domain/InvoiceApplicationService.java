package com.hotelai.billing.domain;

import com.hotelai.billing.common.ApiException;
import com.hotelai.billing.domain.Invoice.Buyer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class InvoiceApplicationService {
    private final StayBillingProjectionRepository stays;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final InvoiceCorrectionRepository corrections;
    private final InvoiceNumberSequenceRepository sequences;
    private final InvoiceDeliveryRequestRepository deliveries;
    private final InvoiceSourceBuilder sourceBuilder;
    private final BillingEventService events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvoiceApplicationService(
            StayBillingProjectionRepository stays,
            InvoiceRepository invoices,
            InvoiceLineRepository lines,
            InvoiceCorrectionRepository corrections,
            InvoiceNumberSequenceRepository sequences,
            InvoiceDeliveryRequestRepository deliveries,
            InvoiceSourceBuilder sourceBuilder,
            BillingEventService events,
            ObjectMapper objectMapper,
            Clock clock) {
        this.stays = stays;
        this.invoices = invoices;
        this.lines = lines;
        this.corrections = corrections;
        this.sequences = sequences;
        this.deliveries = deliveries;
        this.sourceBuilder = sourceBuilder;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StayBillingProjection> searchStays(String query) {
        return stays.findTop100ByOrderBySourceUpdatedAtDesc().stream()
                .filter(value -> List.of(
                                "CHECKOUT_PAYMENT_PENDING", "CHECKOUT_PENDING", "CLOSED")
                        .contains(value.getStatus()))
                .filter(value -> value.matches(query))
                .limit(50)
                .toList();
    }

    @Transactional
    public InvoiceDetails createDraft(
            UUID actorId, String idempotencyKey, DraftCommand command) {
        requireKey(idempotencyKey);
        String requestHash = hash("DRAFT", null, command);
        Invoice replay = invoices.findByDraftIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            try {
                replay.replayDraft(requestHash);
            } catch (IllegalStateException error) {
                throw ApiException.conflict("idempotency-key-reused", error.getMessage());
            }
            return details(replay.getId());
        }
        StayBillingProjection stay = requiredStay(command.stayId());
        InvoiceSourceSnapshot source = sourceBuilder.build(stay);
        Invoice invoice = invoices.save(new Invoice(
                source,
                new Buyer(
                        command.buyerName(), command.buyerEmail(),
                        command.buyerAddress(), command.buyerTaxCode()),
                idempotencyKey,
                requestHash,
                actorId));
        for (InvoiceLineSpec line : source.lines()) {
            lines.save(new InvoiceLine(invoice.getId(), line));
        }
        return details(invoice.getId());
    }

    @Transactional
    public InvoiceDetails issue(
            UUID actorId, UUID invoiceId, String idempotencyKey) {
        requireKey(idempotencyKey);
        Invoice invoice = invoices.findLockedById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            if (invoice.replayIssue(idempotencyKey)) return details(invoiceId);
            throw ApiException.conflict(
                    "invoice-already-issued",
                    "Invoice was already issued by another command");
        }
        StayBillingProjection stay = requiredStay(invoice.getStayId());
        if (!"CLOSED".equals(stay.getStatus())) {
            throw ApiException.conflict(
                    "stay-not-closed",
                    "Invoice can only be issued after checkout is complete");
        }
        InvoiceSourceSnapshot current = sourceBuilder.build(stay);
        if (!invoice.getSourceHash().equals(current.sourceHash())) {
            throw ApiException.conflict(
                    "invoice-source-changed",
                    "Financial data changed after preview; create a new draft");
        }
        String number = allocateNumber("INV");
        invoice.issue(number, idempotencyKey, actorId, clock.instant());
        events.invoiceIssued(invoice);
        return details(invoiceId);
    }

    @Transactional
    public InvoiceDetails correct(
            UUID actorId,
            boolean manager,
            UUID invoiceId,
            String idempotencyKey,
            CorrectionCommand command) {
        requireKey(idempotencyKey);
        if (!manager) {
            throw ApiException.forbidden(
                    "Only a manager can issue invoice corrections or cancellations");
        }
        String requestHash = hash("CORRECTION", invoiceId, command);
        InvoiceCorrection replay =
                corrections.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!replay.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict(
                        "idempotency-key-reused",
                        "Idempotency-Key was already used for another correction");
            }
            return details(invoiceId);
        }
        Invoice invoice = invoices.findLockedById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.conflict(
                    "invoice-not-issued", "Draft invoice cannot be corrected");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict(
                    "invoice-already-cancelled",
                    "A cancelled invoice cannot receive another correction");
        }
        if (command.type() == InvoiceCorrectionType.ADJUSTMENT
                && (command.amountDelta() == null
                    || command.amountDelta().signum() == 0)) {
            throw ApiException.validation(
                    "Adjustment amount must be non-zero");
        }
        java.math.BigDecimal delta =
                command.type() == InvoiceCorrectionType.CANCELLATION
                        ? invoice.getTotalAmount().negate()
                        : command.amountDelta();
        String series =
                command.type() == InvoiceCorrectionType.CANCELLATION ? "CAN" : "ADJ";
        InvoiceCorrection correction = corrections.save(new InvoiceCorrection(
                invoice,
                allocateNumber(series),
                command.type(),
                delta,
                command.reason(),
                idempotencyKey,
                requestHash,
                actorId,
                clock.instant()));
        if (command.type() == InvoiceCorrectionType.CANCELLATION) {
            invoice.markCancelled(clock.instant());
        }
        events.correctionIssued(invoice, correction);
        return details(invoiceId);
    }

    @Transactional
    public InvoiceDetails requestEmail(
            UUID actorId,
            UUID invoiceId,
            String idempotencyKey,
            String recipient) {
        requireKey(idempotencyKey);
        if (recipient == null
                || !recipient.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.validation("A valid email recipient is required");
        }
        String normalized = recipient.trim().toLowerCase(java.util.Locale.ROOT);
        String requestHash = hash("EMAIL", invoiceId, Map.of("recipient", normalized));
        InvoiceDeliveryRequest replay =
                deliveries.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!replay.getRequestHash().equals(requestHash)
                    || !replay.getInvoiceId().equals(invoiceId)) {
                throw ApiException.conflict(
                        "idempotency-key-reused",
                        "Idempotency-Key was already used for another delivery");
            }
            return details(invoiceId);
        }
        Invoice invoice = requiredInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.conflict(
                    "invoice-not-issued", "Only an issued invoice can be emailed");
        }
        InvoiceDeliveryRequest delivery = deliveries.save(new InvoiceDeliveryRequest(
                invoiceId, normalized, idempotencyKey, requestHash, actorId));
        events.emailRequested(invoice, delivery, actorId);
        return details(invoiceId);
    }

    @Transactional(readOnly = true)
    public InvoiceDetails details(UUID invoiceId) {
        Invoice invoice = requiredInvoice(invoiceId);
        return new InvoiceDetails(
                invoice,
                lines.findByInvoiceIdOrderByDisplayOrderAsc(invoiceId),
                corrections.findByInvoiceIdOrderByIssuedAtAsc(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDetails> byStay(UUID stayId) {
        return invoices.findByStayIdOrderByCreatedAtDesc(stayId).stream()
                .map(invoice -> new InvoiceDetails(
                        invoice,
                        lines.findByInvoiceIdOrderByDisplayOrderAsc(invoice.getId()),
                        corrections.findByInvoiceIdOrderByIssuedAtAsc(invoice.getId())))
                .toList();
    }

    private String allocateNumber(String series) {
        int year = ZonedDateTime.now(clock).getYear();
        String id = series + "-" + year;
        InvoiceNumberSequence sequence = sequences.findLockedById(id)
                .orElseGet(() -> sequences.save(new InvoiceNumberSequence(series, year)));
        return sequence.allocate(series, year);
    }

    private StayBillingProjection requiredStay(UUID id) {
        return stays.findById(id)
                .orElseThrow(() -> ApiException.notFound("Stay not found"));
    }

    private Invoice requiredInvoice(UUID id) {
        return invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation(
                    "Idempotency-Key must contain 8-160 safe characters");
        }
    }

    private String hash(String operation, UUID invoiceId, Object command) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("operation", operation);
            value.put("invoiceId", invoiceId);
            value.put("command", command);
            byte[] canonical = objectMapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash invoice command", error);
        }
    }

    public record DraftCommand(
            UUID stayId,
            String buyerName,
            String buyerEmail,
            String buyerAddress,
            String buyerTaxCode) {}

    public record CorrectionCommand(
            InvoiceCorrectionType type,
            java.math.BigDecimal amountDelta,
            String reason) {}

    public record InvoiceDetails(
            Invoice invoice,
            List<InvoiceLine> lines,
            List<InvoiceCorrection> corrections) {}
}
