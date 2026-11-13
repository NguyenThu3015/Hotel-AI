package com.hotelai.payment.provider;

import java.math.BigDecimal;
import java.time.Instant;

public record MomoWebhook(
        String partnerCode,
        String orderId,
        String requestId,
        String transId,
        BigDecimal amount,
        long resultCode,
        String message,
        String orderInfo,
        String orderType,
        String payType,
        String extraData,
        String signature,
        long responseTime) {

    public boolean succeeded() { return resultCode == 0 || resultCode == 9000; }

    public Instant occurredAt() {
        return responseTime > 0 ? Instant.ofEpochMilli(responseTime) : Instant.now();
    }

    public String eventKey() {
        String request = requestId == null ? "missing-request" : requestId;
        String transaction = transId == null || transId.isBlank() ? "no-transaction" : transId;
        return request + ":" + transaction + ":" + resultCode;
    }

    public String canonical(String accessKey) {
        return "accessKey=" + value(accessKey)
                + "&amount=" + amountText(amount)
                + "&extraData=" + value(extraData)
                + "&message=" + value(message)
                + "&orderId=" + value(orderId)
                + "&orderInfo=" + value(orderInfo)
                + "&orderType=" + value(orderType)
                + "&partnerCode=" + value(partnerCode)
                + "&payType=" + value(payType)
                + "&requestId=" + value(requestId)
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + value(transId);
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static String amountText(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }
}
