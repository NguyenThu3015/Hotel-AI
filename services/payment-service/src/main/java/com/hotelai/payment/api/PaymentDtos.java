package com.hotelai.payment.api;

import com.hotelai.payment.domain.PaymentApplicationService.PaymentReceipt;
import com.hotelai.payment.domain.PaymentCommands;
import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.PaymentPurpose;
import com.hotelai.payment.domain.PaymentStatus;
import com.hotelai.payment.domain.RefundRequest;
import com.hotelai.payment.domain.RefundStatus;
import com.hotelai.payment.provider.CassoPaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CreateIntentRequest(
            @NotNull UUID reservationId,
            @NotNull PaymentPurpose purpose,
            @NotNull PaymentProviderCode provider,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
        PaymentCommands.CreateIntent toCommand() {
            return new PaymentCommands.CreateIntent(
                    reservationId, purpose, provider, amount, currency);
        }
    }

    public record RecordCashRequest(
            @NotNull UUID reservationId,
            @NotNull PaymentPurpose purpose,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Size(max = 500) String note) {
        PaymentCommands.RecordCash toCommand() {
            return new PaymentCommands.RecordCash(
                    reservationId, purpose, amount, currency, note);
        }
    }

    public record RefundRequestBody(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(max = 500) String reason) {}

    public record RefundRejectionRequest(@NotBlank @Size(max = 500) String reason) {}

    public record RefundCompletionRequest(
            @NotBlank @Size(max = 160) String providerReference,
            @Size(max = 500) String note) {}

    public record SandboxResultRequest(boolean succeeded, @Size(max = 80) String failureCode) {}

    public record BankTransferInstructionsResponse(
            String bankId,
            String accountNumber,
            String accountName,
            BigDecimal amount,
            String currency,
            String transferContent,
            String qrUrl,
            Instant expiresAt) {
        static BankTransferInstructionsResponse from(
                CassoPaymentProvider.TransferInstructions value) {
            return new BankTransferInstructionsResponse(
                    value.bankId(),
                    value.accountNumber(),
                    value.accountName(),
                    value.amount(),
                    value.currency(),
                    value.transferContent(),
                    value.qrUrl(),
                    value.expiresAt());
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID reservationId,
            UUID customerId,
            UUID createdByIdentityId,
            PaymentPurpose purpose,
            PaymentProviderCode provider,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            String providerOrderId,
            String providerRequestId,
            String providerTransactionId,
            String checkoutUrl,
            String failureCode,
            String failureMessage,
            Instant expiresAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static PaymentResponse from(PaymentIntent value) {
            return new PaymentResponse(
                    value.getId(),
                    value.getReservationId(),
                    value.getCustomerId(),
                    value.getCreatedByIdentityId(),
                    value.getPurpose(),
                    value.getProvider(),
                    value.getStatus(),
                    value.getAmount(),
                    value.getCurrency(),
                    value.getProviderOrderId(),
                    value.getProviderRequestId(),
                    value.getProviderTransactionId(),
                    value.getCheckoutUrl(),
                    value.getFailureCode(),
                    value.getFailureMessage(),
                    value.getExpiresAt(),
                    value.getCompletedAt(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }

    public record RefundResponse(
            UUID id,
            UUID paymentId,
            BigDecimal amount,
            String currency,
            RefundStatus status,
            String reason,
            UUID requestedBy,
            UUID approvedBy,
            UUID rejectedBy,
            String decisionReason,
            String providerRefundId,
            Instant requestedAt,
            Instant approvedAt,
            Instant completedAt,
            Instant updatedAt,
            long version) {
        static RefundResponse from(RefundRequest value) {
            return new RefundResponse(
                    value.getId(),
                    value.getPaymentId(),
                    value.getAmount(),
                    value.getCurrency(),
                    value.getStatus(),
                    value.getReason(),
                    value.getRequestedBy(),
                    value.getApprovedBy(),
                    value.getRejectedBy(),
                    value.getDecisionReason(),
                    value.getProviderRefundId(),
                    value.getRequestedAt(),
                    value.getApprovedAt(),
                    value.getCompletedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }

    public record ReceiptResponse(
            UUID paymentId,
            UUID reservationId,
            String reservationReference,
            UUID customerId,
            PaymentPurpose purpose,
            PaymentProviderCode provider,
            BigDecimal amount,
            String currency,
            String providerReference,
            Instant paidAt) {
        static ReceiptResponse from(PaymentReceipt value) {
            return new ReceiptResponse(
                    value.paymentId(),
                    value.reservationId(),
                    value.reservationReference(),
                    value.customerId(),
                    value.purpose(),
                    value.provider(),
                    value.amount(),
                    value.currency(),
                    value.providerReference(),
                    value.paidAt());
        }
    }
}
