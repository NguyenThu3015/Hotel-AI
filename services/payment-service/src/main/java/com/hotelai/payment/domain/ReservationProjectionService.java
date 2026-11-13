package com.hotelai.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationProjectionService {
    private final ReservationPaymentProjectionRepository reservations;
    private final PendingStayFolioProjectionRepository pendingStayFolios;
    private final PaymentInboxRepository inbox;

    public ReservationProjectionService(
            ReservationPaymentProjectionRepository reservations,
            PendingStayFolioProjectionRepository pendingStayFolios,
            PaymentInboxRepository inbox) {
        this.reservations = reservations;
        this.pendingStayFolios = pendingStayFolios;
        this.inbox = inbox;
    }

    @Transactional
    public void synchronize(
            UUID eventId,
            String eventType,
            String producer,
            String correlationId,
            Instant occurredAt,
            ReservationPaymentSnapshot snapshot) {
        if (inbox.existsById(eventId)) return;
        PaymentInboxEvent inboxEvent =
                new PaymentInboxEvent(eventId, eventType, producer, snapshot.reservationId(), correlationId);
        inbox.save(inboxEvent);

        ReservationPaymentProjection existing =
                reservations.findById(snapshot.reservationId()).orElse(null);
        ReservationPaymentProjection projection;
        if (existing == null) {
            projection = reservations.save(new ReservationPaymentProjection(
                    snapshot.reservationId(),
                    snapshot.customerId(),
                    snapshot.contactEmail(),
                    snapshot.reference(),
                    snapshot.status(),
                    snapshot.currency(),
                    snapshot.totalAmount(),
                    snapshot.depositRequired(),
                    snapshot.paidAmount(),
                    snapshot.version(),
                    occurredAt));
        } else {
            projection = existing;
            existing.synchronize(
                    snapshot.customerId(),
                    snapshot.contactEmail(),
                    snapshot.reference(),
                    snapshot.status(),
                    snapshot.currency(),
                    snapshot.totalAmount(),
                    snapshot.depositRequired(),
                    snapshot.paidAmount(),
                    snapshot.version(),
                    occurredAt);
        }
        pendingStayFolios.findById(snapshot.reservationId()).ifPresent(pending ->
                projection.synchronizeStay(
                        pending.getCurrency(),
                        pending.getChargesTotal(),
                        pending.getPaidTotal(),
                        pending.getSourceUpdatedAt()));
        inboxEvent.processed();
    }

    @Transactional
    public void synchronizeStay(
            UUID eventId,
            String eventType,
            String producer,
            String correlationId,
            Instant occurredAt,
            StayFolioSnapshot snapshot) {
        if (inbox.existsById(eventId)) return;
        PaymentInboxEvent inboxEvent =
                new PaymentInboxEvent(eventId, eventType, producer, snapshot.reservationId(), correlationId);
        inbox.save(inboxEvent);
        ReservationPaymentProjection reservation =
                reservations.findById(snapshot.reservationId()).orElse(null);
        if (reservation == null) {
            PendingStayFolioProjection pending = pendingStayFolios
                    .findById(snapshot.reservationId())
                    .orElseGet(() -> new PendingStayFolioProjection(
                            snapshot.reservationId(),
                            snapshot.currency(),
                            snapshot.chargesTotal(),
                            snapshot.paidTotal(),
                            occurredAt));
            pending.synchronize(
                    snapshot.currency(), snapshot.chargesTotal(), snapshot.paidTotal(), occurredAt);
            pendingStayFolios.save(pending);
        } else {
            reservation.synchronizeStay(
                    snapshot.currency(), snapshot.chargesTotal(), snapshot.paidTotal(), occurredAt);
        }
        inboxEvent.processed();
    }

    public record ReservationPaymentSnapshot(
            UUID reservationId,
            UUID customerId,
            String contactEmail,
            String reference,
            String status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            long version) {}

    public record StayFolioSnapshot(
            UUID stayId,
            UUID reservationId,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            String status) {}
}
