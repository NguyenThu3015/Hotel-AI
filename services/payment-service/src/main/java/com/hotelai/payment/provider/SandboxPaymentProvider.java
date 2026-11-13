package com.hotelai.payment.provider;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.config.PaymentProperties;
import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.RefundRequest;
import org.springframework.stereotype.Component;

@Component
class SandboxPaymentProvider implements PaymentProvider {
    private final PaymentProperties.Sandbox properties;

    SandboxPaymentProvider(PaymentProperties properties) {
        this.properties = properties.providers().sandbox();
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.SANDBOX;
    }

    @Override
    public CheckoutSession createCheckout(PaymentIntent intent, String extraData) {
        if (!properties.enabled()) {
            throw ApiException.unavailable("provider-disabled", "Sandbox payment provider is disabled");
        }
        String requestId = "SBX-" + intent.getId();
        String checkoutUrl = properties.checkoutBaseUrl().toString().replaceAll("/$", "")
                + "/" + intent.getId();
        return new CheckoutSession(requestId, extraData, checkoutUrl);
    }

    @Override
    public PaymentInquiry queryPayment(PaymentIntent payment) {
        return new PaymentInquiry(
                false,
                false,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProviderExtraData(),
                payment.getProviderTransactionId(),
                null,
                null,
                "{\"mode\":\"sandbox\",\"pending\":true}");
    }

    @Override
    public RefundSubmission submitRefund(PaymentIntent payment, RefundRequest refund) {
        if (!properties.enabled()) {
            throw ApiException.unavailable("provider-disabled", "Sandbox payment provider is disabled");
        }
        return new RefundSubmission(
                "SBX-REF-" + refund.getId(), "{\"accepted\":true,\"mode\":\"sandbox\"}", true);
    }

    @Override
    public RefundInquiry queryRefund(PaymentIntent payment, RefundRequest refund) {
        return new RefundInquiry(
                true,
                true,
                refund.getProviderRefundId(),
                "{\"accepted\":true,\"mode\":\"sandbox\"}");
    }
}
