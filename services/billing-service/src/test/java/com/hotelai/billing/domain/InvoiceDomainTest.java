package com.hotelai.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.billing.config.BillingProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceDomainTest {

    @Test
    void requiresCompleteBuyerInformation() {
        assertThatThrownBy(() -> new Invoice(
                        source("0.00"),
                        new Invoice.Buyer("Nguyen Van A", "not-an-email", "", null),
                        "draft-key-0001",
                        "request-hash",
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Buyer name");
    }

    @Test
    void blocksIssueUntilBalanceIsZero() {
        Invoice invoice = invoice(source("100000.00"));

        assertThatThrownBy(() -> invoice.issue(
                        "INV-2026-000001",
                        "issue-key-0001",
                        UUID.randomUUID(),
                        Instant.parse("2026-07-17T08:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("balance must be zero");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    void issueLocksIdentityAndCancellationChangesStatus() {
        Invoice invoice = invoice(source("0.00"));
        Instant issuedAt = Instant.parse("2026-07-17T08:00:00Z");

        invoice.issue(
                "INV-2026-000001",
                "issue-key-0001",
                UUID.randomUUID(),
                issuedAt);
        invoice.markCancelled(issuedAt.plusSeconds(60));

        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-2026-000001");
        assertThat(invoice.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThatThrownBy(() -> invoice.issue(
                        "INV-2026-000002",
                        "issue-key-0002",
                        UUID.randomUUID(),
                        issuedAt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void correctionIsSeparateNumberedDocument() {
        Invoice invoice = invoice(source("0.00"));
        invoice.issue(
                "INV-2026-000001",
                "issue-key-0001",
                UUID.randomUUID(),
                Instant.parse("2026-07-17T08:00:00Z"));

        InvoiceCorrection correction = new InvoiceCorrection(
                invoice,
                "ADJ-2026-000001",
                InvoiceCorrectionType.ADJUSTMENT,
                new BigDecimal("50000"),
                "Bổ sung phí trả phòng muộn",
                "adjust-key-0001",
                "request-hash",
                UUID.randomUUID(),
                Instant.parse("2026-07-17T09:00:00Z"));

        assertThat(correction.getCorrectionNumber()).isEqualTo("ADJ-2026-000001");
        assertThat(correction.getAmountDelta()).isEqualByComparingTo("50000.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void rendersIssuedInvoiceAsPdf() {
        Invoice invoice = invoice(source("0.00"));
        invoice.issue(
                "INV-2026-000001",
                "issue-key-0001",
                UUID.randomUUID(),
                Instant.parse("2026-07-17T08:00:00Z"));
        InvoiceLine line = new InvoiceLine(
                invoice.getId(),
                new InvoiceLineSpec(
                        InvoiceLineCategory.ROOM,
                        invoice.getReservationId(),
                        "Tiền phòng",
                        BigDecimal.ONE,
                        new BigDecimal("1000000"),
                        new BigDecimal("1000000"),
                        10));
        BillingProperties properties = new BillingProperties(
                "Asia/Ho_Chi_Minh",
                "Hotel AI",
                "Ho Chi Minh City",
                "0000000000",
                null,
                null);

        byte[] pdf = new InvoicePdfRenderer(properties).render(
                new InvoiceApplicationService.InvoiceDetails(
                        invoice, List.of(line), List.of()));

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(pdf.length).isGreaterThan(500);
    }

    private static Invoice invoice(InvoiceSourceSnapshot source) {
        return new Invoice(
                source,
                new Invoice.Buyer(
                        "Nguyen Van A",
                        "guest@example.com",
                        "01 Nguyen Hue, TP.HCM",
                        "0312345678"),
                "draft-key-0001",
                "request-hash",
                UUID.randomUUID());
    }

    private static InvoiceSourceSnapshot source(String balance) {
        UUID reservationId = UUID.randomUUID();
        return new InvoiceSourceSnapshot(
                UUID.randomUUID(),
                reservationId,
                UUID.randomUUID(),
                "BK-2026-0001",
                "VND",
                new BigDecimal("1000000.00"),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1000000.00"),
                new BigDecimal("1000000.00").subtract(new BigDecimal(balance)),
                new BigDecimal(balance),
                "source-hash",
                List.of(new InvoiceLineSpec(
                        InvoiceLineCategory.ROOM,
                        reservationId,
                        "Room charges",
                        BigDecimal.ONE,
                        new BigDecimal("1000000.00"),
                        new BigDecimal("1000000.00"),
                        10)));
    }
}
