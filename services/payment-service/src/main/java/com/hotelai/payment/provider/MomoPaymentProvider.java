package com.hotelai.payment.provider;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.config.PaymentProperties;
import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.RefundRequest;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MomoPaymentProvider implements PaymentProvider {
    private final PaymentProperties.Momo properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MomoPaymentProvider(PaymentProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties.providers().momo();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.MOMO;
    }

    @Override
    public CheckoutSession createCheckout(PaymentIntent intent, String extraData) {
        requireConfigured();
        requireMomoMoney(intent.getAmount(), intent.getCurrency());
        String requestId = intent.getId().toString();
        String requestType = "captureWallet";
        String orderInfo = "Hotel reservation " + intent.getReservationId();
        String amount = integerAmount(intent.getAmount());
        String canonical = "accessKey=" + properties.accessKey()
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + properties.ipnUrl()
                + "&orderId=" + intent.getProviderOrderId()
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + properties.partnerCode()
                + "&redirectUrl=" + properties.redirectUrl()
                + "&requestId=" + requestId
                + "&requestType=" + requestType;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.partnerCode());
        body.put("partnerName", "Hotel AI");
        body.put("storeId", "HotelAI");
        body.put("requestId", requestId);
        body.put("amount", Long.parseLong(amount));
        body.put("orderId", intent.getProviderOrderId());
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", properties.redirectUrl().toString());
        body.put("ipnUrl", properties.ipnUrl().toString());
        body.put("lang", "vi");
        body.put("requestType", requestType);
        body.put("autoCapture", true);
        body.put("extraData", extraData);
        body.put("signature", MomoSignature.hmacSha256(canonical, properties.secretKey()));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException error) {
            throw uncertain(requestId, "MoMo checkout result is unknown after a network timeout", error);
        } catch (RestClientResponseException error) {
            JsonNode failure = readResponse(error.getResponseBodyAsString());
            long resultCode = failure == null ? -1 : failure.path("resultCode").asLong(-1);
            if (resultCode == 7000 || resultCode == 7002) {
                throw uncertain(requestId, error.getResponseBodyAsString(), error);
            }
            String message = failure == null
                    ? "MoMo rejected checkout"
                    : failure.path("message").asText("MoMo rejected checkout");
            throw ApiException.unavailable("momo-checkout-failed", message);
        }
        if (response == null || response.path("resultCode").asLong(-1) != 0
                || response.path("payUrl").asText("").isBlank()) {
            String message = response == null ? "Empty MoMo response" : response.path("message").asText("MoMo rejected checkout");
            throw ApiException.unavailable("momo-checkout-failed", message);
        }
        return new CheckoutSession(
                response.path("requestId").asText(requestId), extraData, response.path("payUrl").asText());
    }

    @Override
    public PaymentInquiry queryPayment(PaymentIntent payment) {
        requireConfigured();
        if (properties.queryEndpoint() == null) {
            throw ApiException.unavailable(
                    "momo-query-not-configured", "MoMo payment query endpoint is not configured");
        }
        String requestId = UUID.randomUUID().toString();
        String orderId = payment.getProviderOrderId();
        String canonical = "accessKey=" + properties.accessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.partnerCode()
                + "&requestId=" + requestId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.partnerCode());
        body.put("requestId", requestId);
        body.put("orderId", orderId);
        body.put("lang", "vi");
        body.put("signature", MomoSignature.hmacSha256(canonical, properties.secretKey()));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.queryEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException error) {
            throw ApiException.unavailable(
                    "momo-query-failed", "Cannot query MoMo payment status");
        }
        if (response == null) {
            throw ApiException.unavailable("momo-query-failed", "Empty MoMo payment query response");
        }
        long resultCode = response.path("resultCode").asLong(-1);
        boolean succeeded = resultCode == 0 || resultCode == 9000;
        boolean terminal = succeeded || (resultCode != 1000 && resultCode != 7000 && resultCode != 7002);
        BigDecimal amount = response.path("amount").isMissingNode()
                ? payment.getAmount()
                : response.path("amount").decimalValue();
        String raw;
        try {
            raw = objectMapper.writeValueAsString(response);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot audit MoMo payment query response", error);
        }
        return new PaymentInquiry(
                terminal,
                succeeded,
                amount,
                "VND",
                response.path("extraData").asText(payment.getProviderExtraData()),
                response.path("transId").asText(null),
                response.path("responseTime").asLong(0) > 0
                        ? java.time.Instant.ofEpochMilli(response.path("responseTime").asLong())
                        : java.time.Instant.now(),
                succeeded ? null : "MOMO_" + resultCode,
                raw);
    }

    @Override
    public RefundSubmission submitRefund(PaymentIntent payment, RefundRequest refund) {
        requireConfigured();
        requireMomoMoney(refund.getAmount(), refund.getCurrency());
        String requestId = refund.getId().toString();
        String amount = integerAmount(refund.getAmount());
        String transaction = payment.getProviderTransactionId();
        if (transaction == null || transaction.isBlank()) {
            throw ApiException.conflict("provider-reference-missing", "MoMo transaction reference is missing");
        }
        String description = "Refund payment " + payment.getId();
        String canonical = "accessKey=" + properties.accessKey()
                + "&amount=" + amount
                + "&description=" + description
                + "&orderId=" + refund.getId()
                + "&partnerCode=" + properties.partnerCode()
                + "&requestId=" + requestId
                + "&transId=" + transaction;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.partnerCode());
        body.put("orderId", refund.getId().toString());
        body.put("requestId", requestId);
        body.put("amount", Long.parseLong(amount));
        try {
            body.put("transId", Long.parseLong(transaction));
        } catch (NumberFormatException error) {
            throw ApiException.conflict(
                    "provider-reference-invalid", "MoMo transaction reference must be numeric");
        }
        body.put("lang", "vi");
        body.put("description", description);
        body.put("signature", MomoSignature.hmacSha256(canonical, properties.secretKey()));
        JsonNode response = restClient.post()
                .uri(properties.refundEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        long resultCode = response == null ? -1 : response.path("resultCode").asLong(-1);
        if (response == null || (resultCode != 0 && resultCode != 7000 && resultCode != 7002)) {
            String message = response == null ? "Empty MoMo response" : response.path("message").asText("MoMo rejected refund");
            throw ApiException.unavailable("momo-refund-failed", message);
        }
        try {
            return new RefundSubmission(
                    response.path("transId").asText(requestId),
                    objectMapper.writeValueAsString(response),
                    resultCode == 0);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot audit MoMo refund response", error);
        }
    }

    @Override
    public RefundInquiry queryRefund(PaymentIntent payment, RefundRequest refund) {
        requireConfigured();
        if (properties.refundQueryEndpoint() == null) {
            throw ApiException.unavailable(
                    "momo-refund-query-not-configured", "MoMo refund query endpoint is not configured");
        }
        String requestId = "Q-" + refund.getId();
        String orderId = refund.getId().toString();
        String canonical = "accessKey=" + properties.accessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.partnerCode()
                + "&requestId=" + requestId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.partnerCode());
        body.put("requestId", requestId);
        body.put("orderId", orderId);
        body.put("lang", "vi");
        body.put("signature", MomoSignature.hmacSha256(canonical, properties.secretKey()));
        JsonNode response = restClient.post()
                .uri(properties.refundQueryEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw ApiException.unavailable("momo-refund-query-failed", "Empty MoMo refund query response");
        }
        String raw;
        try {
            raw = objectMapper.writeValueAsString(response);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot audit MoMo refund query response", error);
        }
        long queryCode = response.path("resultCode").asLong(-1);
        if (queryCode == 7000 || queryCode == 7002) {
            return new RefundInquiry(false, false, refund.getProviderRefundId(), raw);
        }
        if (queryCode != 0) {
            throw ApiException.unavailable(
                    "momo-refund-query-failed",
                    response.path("message").asText("MoMo rejected refund query"));
        }
        for (JsonNode transaction : response.path("refundTrans")) {
            if (!orderId.equals(transaction.path("orderId").asText())) continue;
            long resultCode = transaction.path("resultCode").asLong(-1);
            String providerReference =
                    transaction.path("transId").asText(refund.getProviderRefundId());
            if (resultCode == 0) {
                return new RefundInquiry(true, true, providerReference, raw);
            }
            if (resultCode == 7000 || resultCode == 7002) {
                return new RefundInquiry(false, false, providerReference, raw);
            }
            return new RefundInquiry(true, false, providerReference, raw);
        }
        return new RefundInquiry(false, false, refund.getProviderRefundId(), raw);
    }

    public MomoWebhook parseWebhook(String rawPayload) {
        try {
            JsonNode json = objectMapper.readTree(rawPayload);
            return new MomoWebhook(
                    text(json, "partnerCode"),
                    text(json, "orderId"),
                    text(json, "requestId"),
                    text(json, "transId"),
                    json.path("amount").isMissingNode() ? null : json.path("amount").decimalValue(),
                    json.path("resultCode").asLong(-1),
                    text(json, "message"),
                    text(json, "orderInfo"),
                    text(json, "orderType"),
                    text(json, "payType"),
                    text(json, "extraData"),
                    text(json, "signature"),
                    json.path("responseTime").asLong(0));
        } catch (Exception error) {
            throw ApiException.validation("Malformed MoMo webhook payload");
        }
    }

    public boolean verifyWebhook(MomoWebhook webhook) {
        if (!configuredForWebhook()) return false;
        if (!properties.partnerCode().equals(webhook.partnerCode())) return false;
        return MomoSignature.matches(
                webhook.canonical(properties.accessKey()), webhook.signature(), properties.secretKey());
    }

    private void requireConfigured() {
        if (!properties.enabled() || !configuredForWebhook()
                || properties.endpoint() == null || properties.refundEndpoint() == null
                || properties.redirectUrl() == null || properties.ipnUrl() == null) {
            throw ApiException.unavailable(
                    "momo-not-configured",
                    "MoMo adapter is present but disabled until credentials and callback URLs are configured");
        }
    }

    private boolean configuredForWebhook() {
        return notBlank(properties.partnerCode()) && notBlank(properties.accessKey()) && notBlank(properties.secretKey());
    }

    private static void requireMomoMoney(BigDecimal amount, String currency) {
        if (!"VND".equals(currency)) {
            throw ApiException.validation("MoMo payments must use VND");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw ApiException.validation("MoMo amount must be a positive whole number of VND");
        }
        if (amount.compareTo(new BigDecimal("1000")) < 0
                || amount.compareTo(new BigDecimal("50000000")) > 0) {
            throw ApiException.validation("MoMo amount must be between 1,000 and 50,000,000 VND");
        }
    }

    private static String integerAmount(BigDecimal amount) {
        return amount.setScale(0).toPlainString();
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private PaymentProvider.UncertainProviderResult uncertain(
            String requestId, String detail, RuntimeException cause) {
        return new PaymentProvider.UncertainProviderResult(
                "MoMo may have accepted the request; reconciliation is required",
                requestId,
                detail,
                cause);
    }

    private JsonNode readResponse(String body) {
        try {
            return body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }
}
