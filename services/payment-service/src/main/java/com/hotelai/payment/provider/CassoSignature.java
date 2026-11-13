package com.hotelai.payment.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/** Implements the canonical HMAC-SHA512 signature used by Casso Webhook V2. */
public final class CassoSignature {
    private CassoSignature() {}

    public static boolean verify(
            ObjectMapper objectMapper,
            String rawPayload,
            String signatureHeader,
            String webhookSecret) {
        if (rawPayload == null || rawPayload.isBlank()
                || signatureHeader == null || signatureHeader.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        try {
            SignatureParts parts = parse(signatureHeader);
            String expected = sign(objectMapper, rawPayload, parts.timestamp(), webhookSecret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    parts.signature().toLowerCase(java.util.Locale.ROOT)
                            .getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static String sign(
            ObjectMapper objectMapper,
            String rawPayload,
            long timestamp,
            String webhookSecret) {
        try {
            Object payload = objectMapper.readValue(rawPayload, Object.class);
            String canonical = objectMapper.writeValueAsString(sortRecursively(payload));
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(hmac.doFinal(
                    (timestamp + "." + canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot calculate Casso webhook signature", error);
        }
    }

    private static SignatureParts parse(String header) {
        Long timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) timestamp = Long.parseLong(pair[1]);
            if ("v1".equals(pair[0])) signature = pair[1];
        }
        if (timestamp == null || signature == null || !signature.matches("(?i)[0-9a-f]{128}")) {
            throw new IllegalArgumentException("Malformed X-Casso-Signature header");
        }
        return new SignatureParts(timestamp, signature);
    }

    private static Object sortRecursively(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sorted = new TreeMap<>();
            source.forEach((key, child) -> sorted.put(String.valueOf(key), sortRecursively(child)));
            return sorted;
        }
        if (value instanceof List<?> source) {
            List<Object> sorted = new ArrayList<>(source.size());
            source.forEach(child -> sorted.add(sortRecursively(child)));
            return sorted;
        }
        return value;
    }

    private record SignatureParts(long timestamp, String signature) {}
}
