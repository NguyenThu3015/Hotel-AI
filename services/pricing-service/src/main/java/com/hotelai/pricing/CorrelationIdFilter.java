package com.hotelai.pricing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

final class CorrelationContext {
  static final String HEADER = "X-Correlation-Id";
  static final String MDC_KEY = "correlationId";
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  static UUID current() {
    UUID value = CURRENT.get();
    return value == null ? UUID.randomUUID() : value;
  }

  static void set(UUID value) {
    CURRENT.set(value);
    MDC.put(MDC_KEY, value.toString());
  }

  static void clear() {
    CURRENT.remove();
    MDC.remove(MDC_KEY);
  }
}

@Component
class CorrelationIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID correlationId = parseOrCreate(request.getHeader(CorrelationContext.HEADER));
    CorrelationContext.set(correlationId);
    response.setHeader(CorrelationContext.HEADER, correlationId.toString());
    try {
      chain.doFilter(request, response);
    } finally {
      CorrelationContext.clear();
    }
  }

  private static UUID parseOrCreate(String value) {
    try {
      return value == null || value.isBlank() ? UUID.randomUUID() : UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return UUID.randomUUID();
    }
  }
}
