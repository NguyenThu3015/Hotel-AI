package com.hotel.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotel.gateway.config.GatewayLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import reactor.test.StepVerifier;

class RequestBodySizeWebFilterTest {

  private final RequestBodySizeWebFilter filter =
      new RequestBodySizeWebFilter(new GatewayLimitProperties(DataSize.ofBytes(4)));

  @Test
  void rejectsDeclaredPayloadLargerThanLimitBeforeCallingDownstream() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/login")
                .header("Content-Length", "5")
                .body("12345"));

    StepVerifier.create(filter.filter(exchange, ignored -> exchange.getResponse().setComplete()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(PayloadTooLargeException.class);
              assertThat(((PayloadTooLargeException) error).getMaximumBytes()).isEqualTo(4);
            })
        .verify();
  }
}
