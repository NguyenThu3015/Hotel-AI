package com.hotel.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdWebFilterTest {

  private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

  @Test
  void preservesSafeCorrelationIdAndPropagatesItToResponse() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/me")
                .header(CorrelationIdWebFilter.HEADER_NAME, "client-request-42"));
    var forwarded = new AtomicReference<ServerWebExchange>();

    StepVerifier.create(
            filter.filter(
                exchange,
                filteredExchange -> {
                  forwarded.set(filteredExchange);
                  return filteredExchange.getResponse().setComplete();
                }))
        .verifyComplete();

    assertThat(
            forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
        .isEqualTo("client-request-42");
    assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
        .isEqualTo("client-request-42");
  }

  @Test
  void replacesUnsafeCorrelationId() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/me")
                .header(CorrelationIdWebFilter.HEADER_NAME, "unsafe value\r\n"));
    var forwarded = new AtomicReference<ServerWebExchange>();

    StepVerifier.create(
            filter.filter(
                exchange,
                filteredExchange -> {
                  forwarded.set(filteredExchange);
                  return Mono.empty();
                }))
        .verifyComplete();

    String generated =
        forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME);
    assertThat(generated).isNotBlank().doesNotContain("unsafe");
  }
}
