package com.hotelai.reporting.audit;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AuditRedactor {
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken",
            "authorization", "secret", "signature", "otp", "cvv", "pin");
    private static final Pattern EMAIL =
            Pattern.compile("([A-Za-z0-9._%+-])[^@\\s]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(?:\\+?84|0)[0-9 .()-]{7,14}(?!\\d)");
    private static final Pattern CARD =
            Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private final ObjectMapper objectMapper;

    public AuditRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode redact(JsonNode input) {
        if (input == null || input.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (input.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = input.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = normalize(field.getKey());
                if (isSecret(normalized)) {
                    result.put(field.getKey(), "[REDACTED]");
                } else if (isDocumentNumber(normalized)) {
                    result.put(field.getKey(), maskIdentifier(field.getValue().asText("")));
                } else {
                    result.set(field.getKey(), redact(field.getValue()));
                }
            }
            return result;
        }
        if (input.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            input.forEach(value -> result.add(redact(value)));
            return result;
        }
        if (input.isTextual()) {
            return objectMapper.getNodeFactory().textNode(maskText(input.asText()));
        }
        return input.deepCopy();
    }

    public String maskText(String value) {
        if (value == null || value.isBlank()) return value;
        String masked = EMAIL.matcher(value).replaceAll("$1***$2");
        masked = replace(masked, PHONE, "***$LAST4");
        masked = replace(masked, CARD, "****$LAST4");
        return masked;
    }

    private static String replace(String value, Pattern pattern, String token) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            String last4 = digits.length() <= 4
                    ? "****" : digits.substring(digits.length() - 4);
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(token.replace("$LAST4", last4)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isSecret(String normalized) {
        return SECRET_KEYS.stream().anyMatch(normalized::contains);
    }

    private static boolean isDocumentNumber(String normalized) {
        return normalized.contains("documentnumber")
                || normalized.contains("cardnumber")
                || normalized.contains("identitynumber");
    }

    private static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) return value;
        return value.length() <= 4
                ? "****"
                : "*".repeat(Math.min(12, value.length() - 4))
                    + value.substring(value.length() - 4);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
