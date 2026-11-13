package com.hotel.gateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

  public static final String HEADER_NAME = "X-Correlation-Id";
  private static final Pattern SAFE_CORRELATION_ID =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String supplied = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
    String correlationId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();

    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.remove(HEADER_NAME);
                  headers.set(HEADER_NAME, correlationId);
                })
            .build();

    exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
              return Mono.empty();
            });

    return chain.filter(exchange.mutate().request(request).build())
        .contextWrite(context -> context.put(HEADER_NAME, correlationId));
  }

  private boolean isSafe(String value) {
    return value != null && SAFE_CORRELATION_ID.matcher(value).matches();
  }
}
