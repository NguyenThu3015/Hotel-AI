package com.hotelai.inventory.common;

import com.hotelai.inventory.config.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class RequestContext {
    private RequestContext() {}

    public static UUID correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (value == null || value.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static UUID actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
