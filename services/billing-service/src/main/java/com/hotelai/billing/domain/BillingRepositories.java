package com.hotelai.billing.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationBillingProjectionRepository
        extends JpaRepository<ReservationBillingProjection, UUID> {}

interface StayBillingProjectionRepository
        extends JpaRepository<StayBillingProjection, UUID> {
    List<StayBillingProjection> findTop100ByOrderBySourceUpdatedAtDesc();
}

interface StayFolioLineProjectionRepository
        extends JpaRepository<StayFolioLineProjection, UUID> {
    List<StayFolioLineProjection> findByStayIdOrderByRecordedAtAsc(UUID stayId);
}

interface PaymentLedgerEntryRepository
        extends JpaRepository<PaymentLedgerEntry, UUID> {
    List<PaymentLedgerEntry> findByReservationIdOrderByOccurredAtAsc(UUID reservationId);
}

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}

interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByDraftIdempotencyKey(String key);
    Optional<Invoice> findByIssueIdempotencyKey(String key);
    List<Invoice> findByStayIdOrderByCreatedAtDesc(UUID stayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findLockedById(@Param("id") UUID id);
}

interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findByInvoiceIdOrderByDisplayOrderAsc(UUID invoiceId);
}

interface InvoiceCorrectionRepository extends JpaRepository<InvoiceCorrection, UUID> {
    Optional<InvoiceCorrection> findByIdempotencyKey(String key);
    List<InvoiceCorrection> findByInvoiceIdOrderByIssuedAtAsc(UUID invoiceId);
}

interface InvoiceNumberSequenceRepository
        extends JpaRepository<InvoiceNumberSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceNumberSequence s where s.seriesYear = :id")
    Optional<InvoiceNumberSequence> findLockedById(@Param("id") String id);
}

interface InvoiceDeliveryRequestRepository
        extends JpaRepository<InvoiceDeliveryRequest, UUID> {
    Optional<InvoiceDeliveryRequest> findByIdempotencyKey(String key);
}
