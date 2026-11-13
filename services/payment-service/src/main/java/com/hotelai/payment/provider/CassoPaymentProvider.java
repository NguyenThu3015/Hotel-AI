package com.hotelai.payment.provider;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.config.PaymentProperties;
import com.hotelai.payment.domain.PaymentIntent;
import com.hotelai.payment.domain.PaymentProviderCode;
import com.hotelai.payment.domain.RefundRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CassoPaymentProvider implements PaymentProvider {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PaymentProperties.Casso properties;
    private final ObjectMapper objectMapper;

    public CassoPaymentProvider(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties.providers().casso();
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.CASSO;
    }

    @Override
    public CheckoutSession createCheckout(PaymentIntent intent, String extraData) {
        TransferInstructions instructions = transferInstructions(intent);
        return new CheckoutSession(
                "CASSO-" + intent.getId(), extraData, instructions.qrUrl());
    }

    public TransferInstructions transferInstructions(PaymentIntent intent) {
        requireConfigured();
        if (intent.getProvider() != PaymentProviderCode.CASSO) {
            throw ApiException.conflict("wrong-provider", "Payment is not a Casso bank transfer");
        }
        requireCassoMoney(intent.getAmount(), intent.getCurrency());
        String content = transferContent(intent);
        return new TransferInstructions(
                properties.bankId().trim(),
                properties.accountNumber().trim(),
                properties.accountName().trim(),
                intent.getAmount(),
                intent.getCurrency(),
                content,
                qrUrl(intent.getAmount(), content),
                intent.getExpiresAt());
    }

    public CassoWebhook parseWebhook(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root == null || root.path("error").asInt(-1) != 0) {
                throw new IllegalArgumentException("Casso webhook contains an error");
            }
            JsonNode data = root.path("data");
            if (data.isArray()) {
                if (data.size() != 1) {
                    throw new IllegalArgumentException("Casso Webhook V2 must contain one transaction");
                }
                data = data.get(0);
            }
            if (data == null || !data.isObject()) {
                throw new IllegalArgumentException("Casso webhook data is missing");
            }
            String eventId = data.path("id").asText(null);
            BigDecimal amount = data.path("amount").isMissingNode()
                    ? null
                    : data.path("amount").decimalValue();
            if (eventId == null || eventId.isBlank() || amount == null) {
                throw new IllegalArgumentException("Casso transaction identity or amount is missing");
            }
            return new CassoWebhook(
                    eventId,
                    text(data, "reference"),
                    text(data, "description"),
                    amount,
                    occurredAt(text(data, "transactionDateTime")),
                    text(data, "accountNumber"),
                    text(data, "bankName"),
                    text(data, "bankAbbreviation"),
                    text(data, "virtualAccountNumber"));
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw ApiException.validation("Malformed Casso Webhook V2 payload");
        }
    }

    public boolean verifyWebhook(String rawPayload, String signatureHeader) {
        return properties.enabled() && CassoSignature.verify(
                objectMapper, rawPayload, signatureHeader, properties.webhookSecret());
    }

    public boolean matchesReceivingAccount(CassoWebhook webhook) {
        if (webhook.accountNumber() == null || webhook.accountNumber().isBlank()) return true;
        return normalized(properties.accountNumber()).equals(normalized(webhook.accountNumber()));
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
                "{\"mode\":\"casso-bank-transfer\",\"pendingWebhook\":true}");
    }

    @Override
    public RefundSubmission submitRefund(PaymentIntent payment, RefundRequest refund) {
        return new RefundSubmission(
                "CASSO-MANUAL-" + refund.getId(),
                "{\"mode\":\"manual-bank-transfer\",\"pendingStaff\":true}",
                false);
    }

    @Override
    public RefundInquiry queryRefund(PaymentIntent payment, RefundRequest refund) {
        return new RefundInquiry(
                false,
                false,
                refund.getProviderRefundId(),
                "{\"mode\":\"manual-bank-transfer\",\"pendingStaff\":true}");
    }

    private String qrUrl(BigDecimal amount, String transferContent) {
        URI base = properties.qrBaseUrl();
        if (base == null || !"https".equalsIgnoreCase(base.getScheme())) {
            throw ApiException.unavailable("casso-qr-not-configured", "Casso VietQR base URL must use HTTPS");
        }
        String bank = validatedPathPart(properties.bankId(), "bank id", 2, 20);
        String account = validatedPathPart(properties.accountNumber(), "account number", 4, 32);
        String template = validatedPathPart(properties.qrTemplate(), "QR template", 2, 40);
        String root = base.toString().replaceAll("/+$", "");
        return root + "/" + bank + "-" + account + "-" + template + ".png"
                + "?amount=" + amount.setScale(0).toPlainString()
                + "&addInfo=" + encode(transferContent)
                + "&accountName=" + encode(properties.accountName().trim());
    }

    private String transferContent(PaymentIntent intent) {
        String prefix = properties.transferPrefix() == null || properties.transferPrefix().isBlank()
                ? "HOTELAI"
                : properties.transferPrefix().trim().toUpperCase(java.util.Locale.ROOT);
        if (!prefix.matches("[A-Z0-9_-]{2,20}")) {
            throw ApiException.unavailable(
                    "casso-transfer-prefix-invalid", "Casso transfer prefix contains unsupported characters");
        }
        return prefix + " " + intent.getProviderOrderId();
    }

    private void requireConfigured() {
        if (!properties.enabled()
                || blank(properties.webhookSecret())
                || blank(properties.bankId())
                || blank(properties.accountNumber())
                || blank(properties.accountName())) {
            throw ApiException.unavailable(
                    "casso-not-configured",
                    "Casso adapter is disabled until bank details and the Webhook V2 secret are configured");
        }
    }

    private static void requireCassoMoney(BigDecimal amount, String currency) {
        if (!"VND".equals(currency)) {
            throw ApiException.validation("Casso bank transfers must use VND");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw ApiException.validation("Casso amount must be a positive whole number of VND");
        }
    }

    private Instant occurredAt(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        ZoneId zone = ZoneId.of(blank(properties.timeZone()) ? "Asia/Ho_Chi_Minh" : properties.timeZone());
        try {
            return LocalDateTime.parse(value, DATE_TIME).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay(zone).toInstant();
            } catch (DateTimeParseException invalid) {
                return Instant.now();
            }
        }
    }

    private static String validatedPathPart(String value, String name, int min, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < min || normalized.length() > max
                || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw ApiException.unavailable("casso-config-invalid", "Invalid Casso " + name);
        }
        return normalized;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record TransferInstructions(
            String bankId,
            String accountNumber,
            String accountName,
            BigDecimal amount,
            String currency,
            String transferContent,
            String qrUrl,
            Instant expiresAt) {}
}
