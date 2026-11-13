package com.hotelai.reservation.integration;

import com.hotelai.reservation.config.ReservationProperties;
import com.hotelai.reservation.domain.PricingGateway;
import com.hotelai.reservation.domain.PricingSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class HttpPricingGateway implements PricingGateway {
    private final RestClient client;
    private final String internalKey;
    private final ZoneId hotelZone;

    public HttpPricingGateway(
            @Qualifier("pricingRestClient") RestClient client,
            ReservationProperties properties,
            @Value("${hotel.time-zone:Asia/Ho_Chi_Minh}") String hotelTimeZone) {
        this.client = client;
        this.internalKey = properties.security().internalKey();
        this.hotelZone = ZoneId.of(hotelTimeZone);
    }

    @Override
    public PricingSnapshot quote(QuoteCommand command) {
        try {
            JsonNode response = client.post()
                    .uri("/internal/v1/pricing/quotes")
                    .header("X-Internal-Key", internalKey)
                    .header("Idempotency-Key", quoteIdempotencyKey(command))
                    .body(new PricingQuoteRequest(
                            command.roomTypeId(), command.checkIn(), command.checkOut(),
                            command.roomCount(), command.adults(), command.children(),
                            command.promotionCode(), command.customerId()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("pricing", result.getStatusCode().value());
                    })
                    .body(JsonNode.class);
            if (response == null
                    || response.path("quoteId").asText().isBlank()
                    || response.path("totalAmount").isMissingNode()
                    || response.path("validUntil").asText().isBlank()) {
                throw new IntegrationException("Pricing service returned an invalid quote");
            }
            UUID quoteId = UUID.fromString(response.path("quoteId").asText());
            BigDecimal total = decimal(response, "totalAmount");
            BigDecimal taxAndServiceFee = decimal(response.path("fees"), "taxAmount")
                    .add(decimal(response.path("fees"), "serviceFeeAmount"));
            JsonNode cancellationPolicy = cancellationPolicy(response.path("policies"));
            return new PricingSnapshot(
                    quoteId,
                    "quote:" + quoteId,
                    response.path("currency").asText(),
                    decimal(response, "roomSubtotal"),
                    decimal(response, "discountAmount"),
                    taxAndServiceFee,
                    total,
                    decimal(response.path("deposit"), "amount"),
                    !"ONLINE".equalsIgnoreCase(command.channel()),
                    Instant.parse(response.path("validUntil").asText()),
                    json(response),
                    json(cancellationPolicy),
                    freeCancellationUntil(response, cancellationPolicy),
                    cancellationFee(response, cancellationPolicy, total));
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Pricing service is unavailable", error);
        } catch (RuntimeException error) {
            throw new IntegrationException("Pricing service returned a malformed quote", error);
        }
    }

    @Override
    public void synchronizePromotion(
            UUID reservationId,
            UUID quoteId,
            UUID customerId,
            boolean compensation,
            String idempotencyKey) {
        try {
            client.put()
                    .uri("/internal/v1/pricing/promotion-redemptions/{reservationId}", reservationId)
                    .header("X-Internal-Key", internalKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new PromotionSyncRequest(quoteId, customerId, compensation))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("pricing", result.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Pricing service is unavailable", error);
        }
    }

    @Override
    public void releasePromotion(UUID reservationId, String idempotencyKey) {
        try {
            client.post()
                    .uri("/internal/v1/pricing/promotion-redemptions/{reservationId}/release", reservationId)
                    .header("X-Internal-Key", internalKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("pricing", result.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Pricing service is unavailable", error);
        }
    }

    private String json(JsonNode node) {
        return node == null || node.isNull() ? "{}" : node.toString();
    }

    private static String quoteIdempotencyKey(QuoteCommand command) {
        return "reservation-quote:" + UUID.randomUUID();
    }

    private static JsonNode cancellationPolicy(JsonNode policies) {
        if (policies != null && policies.isArray()) {
            for (JsonNode policy : policies) {
                if ("CANCELLATION".equalsIgnoreCase(policy.path("type").asText())) return policy;
            }
        }
        return null;
    }

    private Instant freeCancellationUntil(JsonNode response, JsonNode policy) {
        if (policy == null || policy.isNull()) return null;
        JsonNode hours = policy.path("parameters").path("freeCancellationHours");
        if (!hours.isNumber()) return null;
        LocalDate checkIn = LocalDate.parse(response.path("checkIn").asText());
        return checkIn.atStartOfDay(hotelZone).toInstant()
                .minus(hours.asLong(), java.time.temporal.ChronoUnit.HOURS);
    }

    private static BigDecimal cancellationFee(
            JsonNode response, JsonNode policy, BigDecimal total) {
        if (policy == null || policy.isNull()) return BigDecimal.ZERO.setScale(2);
        JsonNode parameters = policy.path("parameters");
        BigDecimal value = decimal(parameters, "latePenaltyValue");
        BigDecimal fee = switch (parameters.path("latePenaltyType").asText("").toUpperCase(java.util.Locale.ROOT)) {
            case "PERCENT" -> total.multiply(value)
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            case "FIXED" -> value;
            case "NIGHTS" -> nightlyPenalty(response.path("nightlyPrices"), value.intValue());
            default -> BigDecimal.ZERO;
        };
        return fee.max(BigDecimal.ZERO).min(total).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nightlyPenalty(JsonNode nightlyPrices, int nights) {
        BigDecimal result = BigDecimal.ZERO;
        if (nightlyPrices != null && nightlyPrices.isArray()) {
            int limit = Math.min(Math.max(nights, 0), nightlyPrices.size());
            for (int index = 0; index < limit; index++) {
                result = result.add(decimal(nightlyPrices.get(index), "lineTotal"));
            }
        }
        return result;
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        if (parent == null || parent.isNull() || !parent.path(field).isNumber()) return BigDecimal.ZERO;
        return parent.path(field).decimalValue();
    }

    private record PricingQuoteRequest(
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int rooms,
            int adults,
            int children,
            String promotionCode,
            UUID customerId) {}

    private record PromotionSyncRequest(
            UUID quoteId, UUID customerId, boolean compensation) {}
}
