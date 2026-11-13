package com.hotelai.billing.api;

import com.hotelai.billing.domain.Invoice;
import com.hotelai.billing.domain.InvoiceApplicationService.InvoiceDetails;
import com.hotelai.billing.domain.InvoiceCorrection;
import com.hotelai.billing.domain.InvoiceCorrectionType;
import com.hotelai.billing.domain.InvoiceLine;
import com.hotelai.billing.domain.InvoiceLineCategory;
import com.hotelai.billing.domain.InvoiceStatus;
import com.hotelai.billing.domain.StayBillingProjection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record DraftInvoiceRequest(
            @NotNull UUID stayId,
            @NotBlank @Size(max = 200) String buyerName,
            @NotBlank @Email @Size(max = 320) String buyerEmail,
            @NotBlank @Size(max = 500) String buyerAddress,
            @Pattern(regexp = "^$|\\d{10}(-\\d{3})?$")
            @Size(max = 20) String buyerTaxCode) {}

    public record CorrectionRequest(
            @NotNull InvoiceCorrectionType type,
            BigDecimal amountDelta,
            @NotBlank @Size(max = 500) String reason) {}

    public record EmailInvoiceRequest(
            @NotBlank @Email @Size(max = 320) String recipient) {}

    public record InvoiceStayView(
            UUID stayId,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String contactName,
            String contactEmail,
            String contactPhone,
            String status,
            LocalDate scheduledCheckIn,
            LocalDate scheduledCheckOut,
            Instant actualCheckIn,
            Instant actualCheckOut,
            List<String> roomNumbers,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            BigDecimal balance) {
        static InvoiceStayView from(StayBillingProjection value) {
            return new InvoiceStayView(
                    value.getStayId(), value.getReservationId(), value.getCustomerId(),
                    value.getReservationReference(), value.getContactName(),
                    value.getContactEmail(), value.getContactPhone(), value.getStatus(),
                    value.getScheduledCheckIn(), value.getScheduledCheckOut(),
                    value.getActualCheckIn(), value.getActualCheckOut(),
                    value.roomNumberList(), value.getCurrency(), value.getChargesTotal(),
                    value.getPaidTotal(), value.getBalance());
        }
    }

    public record InvoiceLineView(
            UUID id,
            InvoiceLineCategory category,
            UUID sourceId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            int displayOrder) {
        static InvoiceLineView from(InvoiceLine value) {
            return new InvoiceLineView(
                    value.getId(), value.getCategory(), value.getSourceId(),
                    value.getDescription(), value.getQuantity(), value.getUnitPrice(),
                    value.getAmount(), value.getDisplayOrder());
        }
    }

    public record InvoiceCorrectionView(
            UUID id,
            String number,
            InvoiceCorrectionType type,
            BigDecimal amountDelta,
            String reason,
            Instant issuedAt) {
        static InvoiceCorrectionView from(InvoiceCorrection value) {
            return new InvoiceCorrectionView(
                    value.getId(), value.getCorrectionNumber(), value.getType(),
                    value.getAmountDelta(), value.getReason(), value.getIssuedAt());
        }
    }

    public record InvoiceView(
            UUID id,
            UUID stayId,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String invoiceNumber,
            InvoiceStatus status,
            String buyerName,
            String buyerEmail,
            String buyerAddress,
            String buyerTaxCode,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal folioAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            UUID createdBy,
            Instant createdAt,
            UUID issuedBy,
            Instant issuedAt,
            Instant cancelledAt,
            long version,
            List<InvoiceLineView> lines,
            List<InvoiceCorrectionView> corrections) {
        static InvoiceView from(InvoiceDetails details) {
            Invoice value = details.invoice();
            return new InvoiceView(
                    value.getId(), value.getStayId(), value.getReservationId(),
                    value.getCustomerId(), value.getReservationReference(),
                    value.getInvoiceNumber(), value.getStatus(), value.getBuyerName(),
                    value.getBuyerEmail(), value.getBuyerAddress(), value.getBuyerTaxCode(),
                    value.getCurrency(), value.getSubtotal(), value.getDiscountAmount(),
                    value.getTaxAmount(), value.getFolioAmount(), value.getTotalAmount(),
                    value.getPaidAmount(), value.getBalance(), value.getCreatedBy(),
                    value.getCreatedAt(), value.getIssuedBy(), value.getIssuedAt(),
                    value.getCancelledAt(), value.getVersion(),
                    details.lines().stream().map(InvoiceLineView::from).toList(),
                    details.corrections().stream().map(InvoiceCorrectionView::from).toList());
        }
    }
}
