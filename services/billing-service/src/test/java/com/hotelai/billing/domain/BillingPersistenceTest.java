package com.hotelai.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.billing.domain.ReservationBillingProjection.ReservationSnapshot;
import com.hotelai.billing.domain.StayBillingProjection.FolioLineSnapshot;
import com.hotelai.billing.domain.StayBillingProjection.StaySnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:billing-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BillingPersistenceTest {
    @Autowired ReservationBillingProjectionRepository reservations;
    @Autowired StayBillingProjectionRepository stays;
    @Autowired StayFolioLineProjectionRepository folio;
    @Autowired PaymentLedgerEntryRepository payments;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceLineRepository lines;
    @Autowired InvoiceCorrectionRepository corrections;
    @Autowired InvoiceNumberSequenceRepository sequences;
    @Autowired InvoiceDeliveryRequestRepository deliveries;
    @Autowired BillingOutboxRepository outbox;
    @Autowired EntityManager entityManager;

    @Test
    void persistsFinancialProjectionsAndImmutableInvoiceDocuments() {
        UUID reservationId = UUID.randomUUID();
        UUID stayId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");

        ReservationBillingProjection reservation = new ReservationBillingProjection();
        reservation.synchronize(
                new ReservationSnapshot(
                        reservationId, "BK-PERSIST", customerId,
                        "Nguyen Van A", "guest@example.com", "0900000000",
                        "CHECKED_IN", UUID.randomUUID(),
                        LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17),
                        1, 2, 0, "VND",
                        new BigDecimal("1000000"), BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("1000000"),
                        new BigDecimal("500000"), new BigDecimal("1000000"), 4),
                now.minusSeconds(120),
                "{}");
        reservations.save(reservation);

        FolioLineSnapshot folioSnapshot = new FolioLineSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "HOTEL_SERVICE",
                "Dịch vụ giặt ủi", new BigDecimal("100000"), "VND",
                actorId, now.minusSeconds(90));
        StayBillingProjection stay = new StayBillingProjection();
        stay.synchronize(
                new StaySnapshot(
                        stayId, reservationId, customerId, "BK-PERSIST",
                        "Nguyen Van A", "guest@example.com", "0900000000",
                        "CLOSED", LocalDate.of(2026, 7, 16),
                        LocalDate.of(2026, 7, 17), now.minusSeconds(86400), now,
                        List.of(UUID.randomUUID()), List.of("101"), "VND",
                        new BigDecimal("1100000"), new BigDecimal("1100000"),
                        BigDecimal.ZERO, List.of(folioSnapshot)),
                now);
        stays.save(stay);
        folio.save(new StayFolioLineProjection(stayId, folioSnapshot));
        payments.save(new PaymentLedgerEntry(
                UUID.randomUUID(), UUID.randomUUID(), reservationId, "PAYMENT",
                new BigDecimal("1100000"), "VND", "MOMO", "momo-persist", now));

        InvoiceSourceSnapshot source = new InvoiceSourceSnapshot(
                stayId, reservationId, customerId, "BK-PERSIST", "VND",
                new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100000"), new BigDecimal("1100000"),
                new BigDecimal("1100000"), BigDecimal.ZERO,
                "a".repeat(64), List.of());
        Invoice invoice = new Invoice(
                source,
                new Invoice.Buyer(
                        "Nguyen Van A", "guest@example.com",
                        "01 Nguyen Hue, TP.HCM", "0312345678"),
                "draft-persist-001", "b".repeat(64), actorId);
        invoice.issue("INV-2026-000001", "issue-persist-001", actorId, now);
        invoices.save(invoice);
        lines.save(new InvoiceLine(
                invoice.getId(),
                new InvoiceLineSpec(
                        InvoiceLineCategory.ROOM, reservationId, "Tiền phòng",
                        BigDecimal.ONE, new BigDecimal("1000000"),
                        new BigDecimal("1000000"), 10)));
        corrections.save(new InvoiceCorrection(
                invoice, "ADJ-2026-000001", InvoiceCorrectionType.ADJUSTMENT,
                new BigDecimal("50000"), "Bổ sung phí", "adjust-persist-001",
                "c".repeat(64), actorId, now.plusSeconds(60)));
        deliveries.save(new InvoiceDeliveryRequest(
                invoice.getId(), "guest@example.com", "email-persist-001",
                "d".repeat(64), actorId));
        InvoiceNumberSequence sequence =
                sequences.save(new InvoiceNumberSequence("INV", 2026));
        assertThat(sequence.allocate("INV", 2026)).isEqualTo("INV-2026-000001");
        outbox.save(new BillingOutboxEvent(
                "Invoice", invoice.getId(), "billing.invoice-issued.v1", "{}"));

        entityManager.flush();
        entityManager.clear();

        Invoice restored = invoices.findById(invoice.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(restored.getTotalAmount()).isEqualByComparingTo("1100000.00");
        assertThat(lines.findByInvoiceIdOrderByDisplayOrderAsc(invoice.getId())).hasSize(1);
        assertThat(corrections.findByInvoiceIdOrderByIssuedAtAsc(invoice.getId())).hasSize(1);
        assertThat(deliveries.findByIdempotencyKey("email-persist-001")).isPresent();
        assertThat(outbox.findAll()).hasSize(1);
    }
}
