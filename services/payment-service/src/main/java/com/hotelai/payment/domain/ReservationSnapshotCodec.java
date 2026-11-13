package com.hotelai.payment.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReservationSnapshotCodec {
    private final ObjectMapper objectMapper;

    public ReservationSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(ReservationPaymentProjection snapshot) {
        String canonical = snapshot.getReservationId() + "|" + snapshot.getCustomerId() + "|"
                + snapshot.getCurrency() + "|" + snapshot.getTotalAmount().toPlainString() + "|"
                + snapshot.getDepositRequired().toPlainString() + "|" + snapshot.getPaidAmount().toPlainString()
                + "|" + snapshot.getSourceVersion();
        return sha256(canonical);
    }

    public String encode(PaymentIntent payment) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("paymentId", payment.getId());
            value.put("reservationId", payment.getReservationId());
            value.put("currency", payment.getCurrency());
            value.put("reservationVersion", payment.getReservationVersionSnapshot());
            value.put("snapshotHash", payment.getReservationSnapshotHash());
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot encode reservation payment snapshot", error);
        }
    }

    public SnapshotExtra decode(String extraData) {
        try {
            JsonNode value = objectMapper.readTree(Base64.getDecoder().decode(extraData));
            return new SnapshotExtra(
                    UUID.fromString(value.path("paymentId").asText()),
                    UUID.fromString(value.path("reservationId").asText()),
                    value.path("currency").asText(),
                    value.path("reservationVersion").asLong(),
                    value.path("snapshotHash").asText());
        } catch (Exception error) {
            throw new IllegalArgumentException("Provider extraData does not contain a valid reservation snapshot", error);
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash payment data", error);
        }
    }

    public record SnapshotExtra(
            UUID paymentId, UUID reservationId, String currency, long reservationVersion, String snapshotHash) {}
}
