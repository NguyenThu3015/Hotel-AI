package com.hotelai.identity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class IdentityEventPayloads {
  private IdentityEventPayloads() {}

  /**
   * Staff lifecycle events are also the temporary source for downstream identity read models.
   * Publishing a complete snapshot keeps each event independently consumable.
   */
  static Map<String, Object> staffSnapshot(Account account, UUID actorId, String hotelId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("identityId", account.getId());
    payload.put("accountId", account.getId());
    payload.put("email", account.getEmail());
    payload.put("fullName", account.getFullName());
    payload.put("phone", account.getPhone());
    payload.put("status", account.getStatus().name());
    payload.put("emailVerified", account.isEmailVerified());
    payload.put("accountType", account.getType().name());
    payload.put("hotelId", hotelId);
    payload.put("roles", account.getRoles().stream().map(Role::getCode).sorted().toList());
    payload.put("actorId", actorId);
    payload.put("occurredAt", Instant.now());
    return payload;
  }
}
