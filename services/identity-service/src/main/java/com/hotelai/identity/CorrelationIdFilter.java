package com.hotelai.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

final class CorrelationContext {
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  static UUID current() {
    UUID value = CURRENT.get();
    return value == null ? UUID.randomUUID() : value;
  }

  static void set(UUID value) {
    CURRENT.set(value);
  }

  static void clear() {
    CURRENT.remove();
  }
}

@Component
class CorrelationIdFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Correlation-ID";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    UUID correlationId = parseOrCreate(request.getHeader(HEADER));
    CorrelationContext.set(correlationId);
    response.setHeader(HEADER, correlationId.toString());
    try {
      filterChain.doFilter(request, response);
    } finally {
      CorrelationContext.clear();
    }
  }

  private UUID parseOrCreate(String value) {
    try {
      return value == null || value.isBlank() ? UUID.randomUUID() : UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return UUID.randomUUID();
    }
  }
}
