package com.hotelai.stay.domain;

import com.hotelai.stay.common.ApiException;
import com.hotelai.stay.config.CorrelationIdFilter;
import com.hotelai.stay.config.StayProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryStayClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public InventoryStayClient(
            RestClient.Builder builder, StayProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.clients().connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.clients().readTimeout());
        String baseUrl = properties.clients().inventoryUrl().replaceAll("/$", "");
        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", properties.security().internalKey())
                .requestInterceptor((request, body, execution) -> {
                    String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlation != null) request.getHeaders().set(CorrelationIdFilter.HEADER, correlation);
                    return execution.execute(request, body);
                })
                .build();
        this.objectMapper = objectMapper;
    }

    public RoomOccupancy occupy(UUID roomId, UUID stayId, String reason) {
        return occupancy(roomId, stayId, reason, false);
    }

    public RoomOccupancy release(UUID roomId, UUID stayId, String reason) {
        return occupancy(roomId, stayId, reason, true);
    }

    private RoomOccupancy occupancy(
            UUID roomId, UUID stayId, String reason, boolean release) {
        try {
            return client.post()
                    .uri("/internal/v1/rooms/{id}/occupancy" + (release ? "/release" : ""), roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("stayId", stayId, "reason", reason))
                    .retrieve()
                    .body(RoomOccupancy.class);
        } catch (RestClientResponseException error) {
            throw inventoryProblem(error, "room-occupancy-failed");
        } catch (RuntimeException error) {
            throw ApiException.unavailable(
                    "inventory-unavailable", "Room Inventory Service is unavailable");
        }
    }

    public Hold ensureExtensionHold(
            UUID previousHoldId,
            UUID operationId,
            UUID reservationId,
            UUID roomTypeId,
            LocalDate from,
            LocalDate to,
            int quantity) {
        String key = "stay-extension-" + operationId + "-" + roomTypeId;
        try {
            Hold created = client.post()
                    .uri("/internal/v1/inventory/holds")
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "roomTypeId", roomTypeId,
                            "checkIn", from,
                            "checkOut", to,
                            "quantity", quantity,
                            "externalReference", "STAY-EXT-" + operationId,
                            "ttlMinutes", 15))
                    .retrieve()
                    .body(Hold.class);
            if (created == null) {
                throw ApiException.unavailable(
                        "inventory-empty-response", "Inventory hold response is empty");
            }
            if (previousHoldId == null) {
                return client.post()
                        .uri("/internal/v1/inventory/holds/{id}/confirm", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reservationId", reservationId))
                        .retrieve()
                        .body(Hold.class);
            }
            return client.post()
                    .uri("/internal/v1/inventory/holds/{id}/replace", created.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "previousHoldId", previousHoldId,
                            "reservationId", reservationId))
                    .retrieve()
                    .body(Hold.class);
        } catch (RestClientResponseException error) {
            throw inventoryProblem(error, "extension-not-available");
        } catch (ApiException error) {
            throw error;
        } catch (RuntimeException error) {
            throw ApiException.unavailable(
                    "inventory-unavailable", "Room Inventory Service is unavailable");
        }
    }

    private ApiException inventoryProblem(RestClientResponseException error, String fallbackCode) {
        try {
            JsonNode problem = objectMapper.readTree(error.getResponseBodyAsString());
            String detail = problem.path("detail").asText("Inventory operation was rejected");
            String type = problem.path("type").asText("");
            String code = type.contains("/") ? type.substring(type.lastIndexOf('/') + 1) : fallbackCode;
            return ApiException.conflict(code, detail);
        } catch (Exception ignored) {
            return ApiException.unavailable(fallbackCode, "Inventory operation failed");
        }
    }

    public record RoomOccupancy(
            UUID roomId,
            String roomNumber,
            UUID roomTypeId,
            String status,
            UUID currentStayId,
            long version) {}

    public record Hold(
            UUID id,
            UUID roomTypeId,
            UUID reservationId,
            String externalReference,
            LocalDate checkIn,
            LocalDate checkOut,
            int quantity,
            String status,
            java.time.Instant expiresAt,
            long version) {}
}
