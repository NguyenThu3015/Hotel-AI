package com.hotelai.review.domain;

import com.hotelai.review.common.ApiException;
import com.hotelai.review.config.ReviewProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class ReviewContentPolicy {
    private static final Pattern EMAIL =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:[ .-]?\\d){9,10}(?!\\d)");
    private static final Pattern PAYMENT_CARD =
            Pattern.compile("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)");
    private static final Pattern SCRIPT =
            Pattern.compile("(?i)<\\s*script|javascript:|onerror\\s*=");
    private static final Pattern EXCESSIVE_REPEAT =
            Pattern.compile("(?iu)(.)\\1{11,}");

    private final ReviewRepository reviews;
    private final ReviewProperties properties;
    private final ObjectMapper objectMapper;

    ReviewContentPolicy(
            ReviewRepository reviews,
            ReviewProperties properties,
            ObjectMapper objectMapper) {
        this.reviews = reviews;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    Assessment assess(UUID customerId, String input) {
        if (input == null) throw ApiException.validation("Review comment is required");
        String trimmed = input.strip();
        if (trimmed.length() < properties.minimumCommentLength()
                || trimmed.length() > properties.maximumCommentLength()) {
            throw ApiException.validation(
                    "Review comment must contain "
                            + properties.minimumCommentLength() + "-"
                            + properties.maximumCommentLength() + " characters");
        }

        Set<String> flags = new LinkedHashSet<>();
        String sanitized = trimmed;
        if (EMAIL.matcher(sanitized).find()) {
            flags.add("PII_EMAIL");
            sanitized = EMAIL.matcher(sanitized).replaceAll("[REDACTED_EMAIL]");
        }
        if (PHONE.matcher(sanitized).find()) {
            flags.add("PII_PHONE");
            sanitized = PHONE.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
        }
        if (PAYMENT_CARD.matcher(sanitized).find()) {
            flags.add("PII_PAYMENT_CARD");
            sanitized = PAYMENT_CARD.matcher(sanitized).replaceAll("[REDACTED_PAYMENT_CARD]");
        }
        if (SCRIPT.matcher(sanitized).find()) flags.add("SUSPICIOUS_MARKUP");
        if (EXCESSIVE_REPEAT.matcher(sanitized).find()) flags.add("EXCESSIVE_REPEAT");

        String normalized = normalize(sanitized);
        List<String> matchedTerms = new ArrayList<>();
        for (String term : properties.prohibitedTerms()) {
            String value = normalize(term);
            if (!value.isBlank() && normalized.contains(value)) matchedTerms.add(term);
        }
        if (!matchedTerms.isEmpty()) flags.add("PROHIBITED_TERM");

        String hash = sha256(normalized);
        if (reviews.existsByCustomerIdAndCommentHashAndCreatedAtAfter(
                customerId,
                hash,
                Instant.now().minus(properties.spamWindow()))) {
            flags.add("DUPLICATE_CONTENT");
        }
        ReviewStatus status = flags.isEmpty()
                ? ReviewStatus.PUBLISHED
                : ReviewStatus.PENDING_MODERATION;
        try {
            return new Assessment(
                    sanitized,
                    hash,
                    objectMapper.writeValueAsString(flags),
                    List.copyOf(flags),
                    status);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize moderation flags", error);
        }
    }

    static String normalize(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash review content", error);
        }
    }

    record Assessment(
            String sanitizedComment,
            String commentHash,
            String flagsJson,
            List<String> flags,
            ReviewStatus status) {}
}
