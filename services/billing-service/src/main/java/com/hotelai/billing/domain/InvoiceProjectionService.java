package com.hotelai.billing.domain;

import com.hotelai.billing.domain.ReservationBillingProjection.ReservationSnapshot;
import com.hotelai.billing.domain.StayBillingProjection.StaySnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class InvoiceProjectionService {
    private final ReservationBillingProjectionRepository reservations;
    private final StayBillingProjectionRepository stays;
    private final StayFolioLineProjectionRepository folio;
    private final PaymentLedgerEntryRepository payments;
    private final ProcessedEventRepository processed;

    public InvoiceProjectionService(
            ReservationBillingProjectionRepository reservations,
            StayBillingProjectionRepository stays,
            StayFolioLineProjectionRepository folio,
            PaymentLedgerEntryRepository payments,
            ProcessedEventRepository processed) {
        this.reservations = reservations;
        this.stays = stays;
        this.folio = folio;
        this.payments = payments;
        this.processed = processed;
    }

    @Transactional
    public void reservation(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            ReservationSnapshot snapshot,
            JsonNode priceBreakdown) {
        if (processed.existsById(eventId)) return;
        ReservationBillingProjection projection =
                reservations.findById(snapshot.reservationId())
                        .orElseGet(ReservationBillingProjection::new);
        projection.synchronize(
                snapshot,
                occurredAt,
                priceBreakdown == null ? "{}" : priceBreakdown.toString());
        reservations.save(projection);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void stay(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            StaySnapshot snapshot) {
        if (processed.existsById(eventId)) return;
        StayBillingProjection projection =
                stays.findById(snapshot.stayId()).orElseGet(StayBillingProjection::new);
        projection.synchronize(snapshot, occurredAt);
        stays.save(projection);
        List<StayBillingProjection.FolioLineSnapshot> entries =
                snapshot.folioEntries() == null ? List.of() : snapshot.folioEntries();
        for (StayBillingProjection.FolioLineSnapshot entry : entries) {
            if (!folio.existsById(entry.entryId())) {
                folio.save(new StayFolioLineProjection(snapshot.stayId(), entry));
            }
        }
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void payment(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            PaymentSnapshot value) {
        if (processed.existsById(eventId)) return;
        if ("payment.succeeded.v1".equals(eventType)
                || "payment.refund-completed.v1".equals(eventType)) {
            String type = "payment.succeeded.v1".equals(eventType) ? "PAYMENT" : "REFUND";
            payments.save(new PaymentLedgerEntry(
                    eventId,
                    value.paymentId(),
                    value.reservationId(),
                    type,
                    value.amount(),
                    value.currency(),
                    value.provider(),
                    value.providerReference(),
                    occurredAt));
        }
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    public record PaymentSnapshot(
            UUID paymentId,
            UUID refundId,
            UUID reservationId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            String status,
            String provider,
            String providerReference) {}
}
