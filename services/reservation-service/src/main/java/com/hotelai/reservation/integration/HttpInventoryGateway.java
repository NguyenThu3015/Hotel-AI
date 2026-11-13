package com.hotelai.reservation.integration;

import com.hotelai.reservation.config.ReservationProperties;
import com.hotelai.reservation.domain.InventoryGateway;
import com.hotelai.reservation.domain.InventoryHold;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpInventoryGateway implements InventoryGateway {
    private final RestClient client;
    private final String internalKey;

    public HttpInventoryGateway(
            @Qualifier("inventoryRestClient") RestClient client,
            ReservationProperties properties) {
        this.client = client;
        this.internalKey = properties.security().internalKey();
    }

    @Override
    public InventoryHold acquire(HoldCommand command, String idempotencyKey) {
        try {
            HoldResponse response = client.post()
                    .uri("/internal/v1/inventory/holds")
                    .header("X-Internal-Key", internalKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(HoldRequest.from(command))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("inventory", result.getStatusCode().value());
                    })
                    .body(HoldResponse.class);
            return require(response);
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Inventory service is unavailable", error);
        }
    }

    @Override
    public void confirm(UUID holdId, UUID reservationId) {
        try {
            client.post()
                    .uri("/internal/v1/inventory/holds/{holdId}/confirm", holdId)
                    .header("X-Internal-Key", internalKey)
                    .body(new ConfirmHoldRequest(reservationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("inventory", result.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Inventory service is unavailable", error);
        }
    }

    @Override
    public void replace(UUID previousHoldId, UUID replacementHoldId, UUID reservationId) {
        try {
            client.post()
                    .uri("/internal/v1/inventory/holds/{holdId}/replace", replacementHoldId)
                    .header("X-Internal-Key", internalKey)
                    .body(new ReplaceHoldRequest(previousHoldId, reservationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw IntegrationException.from("inventory", result.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Inventory service is unavailable", error);
        }
    }

    @Override
    public void release(UUID holdId, String reason) {
        try {
            client.delete()
                    .uri("/internal/v1/inventory/holds/{holdId}", holdId)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .onStatus(status -> status.value() != 404 && status.isError(), (request, result) -> {
                        throw IntegrationException.from("inventory", result.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (IntegrationException error) {
            throw error;
        } catch (RestClientException error) {
            throw new IntegrationException("Inventory service is unavailable", error);
        }
    }

    private static InventoryHold require(HoldResponse response) {
        if (response == null || response.id() == null || response.expiresAt() == null
                || !"HELD".equals(response.status())) {
            throw new IntegrationException("Inventory service returned an invalid hold");
        }
        return new InventoryHold(response.id(), response.expiresAt(), response.version());
    }

    record HoldRequest(
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int quantity,
            String externalReference,
            int ttlMinutes) {
        static HoldRequest from(HoldCommand command) {
            long seconds = Math.max(60, java.time.Duration.between(Instant.now(), command.requestedExpiresAt()).toSeconds());
            int ttl = (int) Math.min(30, Math.max(1, (seconds + 59) / 60));
            return new HoldRequest(
                    command.roomTypeId(), command.checkIn(), command.checkOut(), command.roomCount(),
                    command.reservationId().toString(), ttl);
        }
    }

    record ConfirmHoldRequest(UUID reservationId) {}

    record ReplaceHoldRequest(UUID previousHoldId, UUID reservationId) {}

    record HoldResponse(
            UUID id,
            UUID roomTypeId,
            UUID reservationId,
            String externalReference,
            LocalDate checkIn,
            LocalDate checkOut,
            int quantity,
            String status,
            Instant expiresAt,
            long version) {}
}
