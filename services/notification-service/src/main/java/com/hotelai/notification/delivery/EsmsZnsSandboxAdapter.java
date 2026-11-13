package com.hotelai.notification.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotelai.notification.domain.NotificationChannel;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * eSMS ZNS adapter restricted to the provider sandbox.
 *
 * <p>The sandbox accepts and validates requests but does not deliver a message or charge the
 * account. A separate production adapter/configuration must be introduced before real ZNS is
 * enabled.
 */
@Component
@ConditionalOnProperty(
        name = "hotel.notification.zalo.provider",
        havingValue = "esms-zns-sandbox")
public class EsmsZnsSandboxAdapter implements DeliveryAdapter {
    private static final String SUCCESS_CODE = "100";

    private final RestClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String secretKey;
    private final String oaId;
    private final String templateId;
    private final String templateDataKey;

    @Autowired
    public EsmsZnsSandboxAdapter(
            @Value("${hotel.notification.zalo.api-url}") String apiUrl,
            @Value("${hotel.notification.zalo.api-key}") String apiKey,
            @Value("${hotel.notification.zalo.secret-key}") String secretKey,
            @Value("${hotel.notification.zalo.oa-id}") String oaId,
            @Value("${hotel.notification.zalo.template-id}") String templateId,
            @Value("${hotel.notification.zalo.template-data-key:otp}") String templateDataKey,
            @Value("${hotel.notification.zalo.sandbox:true}") boolean sandbox) {
        this(RestClient.builder(), apiUrl, apiKey, secretKey, oaId, templateId, templateDataKey, sandbox);
    }

    EsmsZnsSandboxAdapter(
            RestClient.Builder builder,
            String apiUrl,
            String apiKey,
            String secretKey,
            String oaId,
            String templateId,
            String templateDataKey,
            boolean sandbox) {
        this.client = Objects.requireNonNull(builder, "RestClient builder is required").build();
        this.endpoint = validEndpoint(apiUrl);
        this.apiKey = required("ZALO_API_KEY", apiKey);
        this.secretKey = required("ZALO_SECRET_KEY", secretKey);
        this.oaId = required("ZALO_OA_ID", oaId);
        this.templateId = required("ZALO_TEMPLATE_OTP_ID", templateId);
        this.templateDataKey = required("ZALO_TEMPLATE_DATA_KEY", templateDataKey);
        if (!sandbox) {
            throw new IllegalArgumentException(
                    "ZALO_SANDBOX must be true when ZALO_PROVIDER=esms-zns-sandbox");
        }
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.ZALO;
    }

    @Override
    public DeliveryResult send(DeliveryRequest request) {
        String value = required("ZNS template value", request.content());
        EsmsZnsRequest payload = new EsmsZnsRequest(
                apiKey,
                secretKey,
                oaId,
                normalizeVietnamesePhone(request.recipient()),
                List.of(new TemplateDatum(templateDataKey, value)),
                templateId,
                "1");
        try {
            EsmsZnsResponse response = client.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(EsmsZnsResponse.class);
            if (response == null) {
                throw new IllegalStateException("eSMS ZNS returned an empty response");
            }
            if (!SUCCESS_CODE.equals(response.codeResult())) {
                throw new IllegalStateException("eSMS ZNS rejected request (code "
                        + safeCode(response.codeResult()) + "): " + safeMessage(response.errorMessage()));
            }
            return new DeliveryResult(required("eSMS ZNS SMSID", response.smsId()));
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "eSMS ZNS HTTP request failed with status " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("eSMS ZNS request failed", ex);
        }
    }

    static String normalizeVietnamesePhone(String input) {
        String phone = required("ZNS recipient", input)
                .replace(" ", "")
                .replace(".", "")
                .replace("-", "");
        if (phone.startsWith("+84")) phone = phone.substring(1);
        if (phone.startsWith("0")) phone = "84" + phone.substring(1);
        if (!phone.matches("84(?:3|5|7|8|9)\\d{8}")) {
            throw new IllegalArgumentException("ZNS recipient must be a valid Vietnamese mobile number");
        }
        return phone;
    }

    private static URI validEndpoint(String value) {
        String endpoint = required("ZALO_API_URL", value);
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("ZALO_API_URL is invalid", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("ZALO_API_URL must be an absolute HTTPS URL");
        }
        return uri;
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String safeCode(String value) {
        return value == null || !value.matches("[A-Za-z0-9_-]{1,30}") ? "UNKNOWN" : value;
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "No error message";
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.substring(0, Math.min(sanitized.length(), 300));
    }

    private record EsmsZnsRequest(
            @JsonProperty("ApiKey") String apiKey,
            @JsonProperty("SecretKey") String secretKey,
            @JsonProperty("OAID") String oaId,
            @JsonProperty("Phone") String phone,
            @JsonProperty("TempData") List<TemplateDatum> templateData,
            @JsonProperty("TempID") String templateId,
            @JsonProperty("Sandbox") String sandbox) {}

    private record TemplateDatum(
            @JsonProperty("key") String key,
            @JsonProperty("value") String value) {}

    private record EsmsZnsResponse(
            @JsonProperty("CodeResult") String codeResult,
            @JsonProperty("SMSID") String smsId,
            @JsonProperty("ErrorMessage") String errorMessage) {}
}
