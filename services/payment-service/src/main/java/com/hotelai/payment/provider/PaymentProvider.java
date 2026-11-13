package com.hotelai.payment.provider;

import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.RefundRequest;

public interface PaymentProvider {
    PaymentProviderCode code();

    CheckoutSession createCheckout(PaymentIntent intent, String extraData);

    PaymentInquiry queryPayment(PaymentIntent payment);

    RefundSubmission submitRefund(PaymentIntent payment, RefundRequest refund);

    RefundInquiry queryRefund(PaymentIntent payment, RefundRequest refund);

    record CheckoutSession(String requestId, String extraData, String checkoutUrl) {}

    record PaymentInquiry(
            boolean terminal,
            boolean succeeded,
            java.math.BigDecimal amount,
            String currency,
            String extraData,
            String providerTransactionId,
            java.time.Instant occurredAt,
            String failureCode,
            String rawResponse) {}

    record RefundSubmission(
            String providerRefundId, String rawResponse, boolean completed) {}

    record RefundInquiry(
            boolean terminal,
            boolean succeeded,
            String providerRefundId,
            String rawResponse) {}

    final class UncertainProviderResult extends RuntimeException {
        private final String providerRequestId;
        private final String auditDetail;

        public UncertainProviderResult(
                String message, String providerRequestId, String auditDetail, Throwable cause) {
            super(message, cause);
            this.providerRequestId = providerRequestId;
            this.auditDetail = auditDetail;
        }

        public String providerRequestId() { return providerRequestId; }
        public String auditDetail() { return auditDetail; }
    }
}
