package com.hotelai.pricing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
class IdempotencyService {
  private final IdempotencyRecordRepository records;
  private final ObjectMapper objectMapper;

  IdempotencyService(IdempotencyRecordRepository records, ObjectMapper objectMapper) {
    this.records = records;
    this.objectMapper = objectMapper;
  }

  @Transactional
  <T> T execute(
      String key, String operation, Object request, Class<T> responseType, Supplier<T> action) {
    validateKey(key);
    String requestHash = hash(request);
    records.lockOperationKey(operation + "|" + key);
    return records
        .findByOperationAndIdempotencyKey(operation, key)
        .map(existing -> replay(existing, requestHash, responseType))
        .orElseGet(
            () -> {
              T response = action.get();
              try {
                records.save(
                    new IdempotencyRecord(
                        operation, key, requestHash, objectMapper.writeValueAsString(response)));
              } catch (Exception exception) {
                throw new IllegalStateException("Cannot persist idempotent response", exception);
              }
              return response;
            });
  }

  String requestHash(Object request) {
    return hash(request);
  }

  private <T> T replay(IdempotencyRecord existing, String requestHash, Class<T> responseType) {
    if (!MessageDigest.isEqual(
        existing.getRequestHash().getBytes(StandardCharsets.US_ASCII),
        requestHash.getBytes(StandardCharsets.US_ASCII))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "idempotency_key_reused",
          "Idempotency-Key was already used with a different request");
    }
    try {
      return objectMapper.readValue(existing.getResponseJson(), responseType);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot replay idempotent response", exception);
    }
  }

  private String hash(Object request) {
    try {
      byte[] serialized = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot hash request", exception);
    }
  }

  private static void validateKey(String key) {
    if (key == null || key.isBlank() || key.length() > 160) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_idempotency_key",
          "Idempotency-Key must contain between 1 and 160 characters");
    }
  }
}

@Service
class OutboxService {
  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;

  OutboxService(OutboxEventRepository events, ObjectMapper objectMapper) {
    this.events = events;
    this.objectMapper = objectMapper;
  }

  void append(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload) {
    try {
      events.save(
          new OutboxEvent(
              aggregateType,
              aggregateId,
              eventType,
              objectMapper.writeValueAsString(new LinkedHashMap<>(payload))));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot serialize outbox event", exception);
    }
  }
}
