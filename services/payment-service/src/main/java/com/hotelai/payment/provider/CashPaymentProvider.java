package com.hotelai.payment.provider;

import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.RefundRequest;
import org.springframework.stereotype.Component;

@Component
class CashPaymentProvider implements PaymentProvider {
    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.CASH;
    }

    @Override
    public CheckoutSession createCheckout(PaymentIntent intent, String extraData) {
        throw new UnsupportedOperationException("Cash payments do not use checkout sessions");
    }

    @Override
    public PaymentInquiry queryPayment(PaymentIntent payment) {
        return new PaymentInquiry(
                true,
                payment.getStatus() == com.hotelai.payment.domain.PaymentStatus.SUCCEEDED,
                payment.getAmount(),
                payment.getCurrency(),
                null,
                payment.getProviderTransactionId(),
                payment.getCompletedAt(),
                payment.getFailureCode(),
                "{\"mode\":\"cash\"}");
    }

    @Override
    public RefundSubmission submitRefund(PaymentIntent payment, RefundRequest refund) {
        return new RefundSubmission(
                "CASH-REF-" + refund.getId(), "{\"accepted\":true,\"mode\":\"cash\"}", false);
    }

    @Override
    public RefundInquiry queryRefund(PaymentIntent payment, RefundRequest refund) {
        return new RefundInquiry(
                false, false, refund.getProviderRefundId(), "{\"mode\":\"cash\",\"pendingStaff\":true}");
    }
}
