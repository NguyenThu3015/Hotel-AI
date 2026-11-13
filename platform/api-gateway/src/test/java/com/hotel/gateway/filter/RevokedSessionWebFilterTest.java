package com.hotel.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotel.gateway.error.ProblemResponseWriter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RevokedSessionWebFilterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final RevokedSessionWebFilter filter =
      new RevokedSessionWebFilter(redis, new ProblemResponseWriter());

  @Test
  void rejectsRevokedSessionWithoutCallingDownstream() {
    when(redis.hasKey(RevokedSessionWebFilter.REVOKED_SESSION_KEY_PREFIX + "session-1"))
        .thenReturn(Mono.just(true));
    var exchange = authenticatedExchange(MockServerHttpRequest.get("/api/v1/me"), "session-1");
    var downstreamCalled = new AtomicBoolean();

    StepVerifier.create(
            filter.filter(
                exchange,
                ignored -> {
                  downstreamCalled.set(true);
                  return Mono.empty();
                }))
        .verifyComplete();

    assertThat(downstreamCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block())
        .contains("session-revoked");
  }

  @Test
  void failsClosedForSensitiveRouteWhenRedisIsUnavailable() {
    when(redis.hasKey(RevokedSessionWebFilter.REVOKED_SESSION_KEY_PREFIX + "session-2"))
        .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
    var exchange =
        authenticatedExchange(MockServerHttpRequest.post("/api/v1/bookings"), "session-2");
    var downstreamCalled = new AtomicBoolean();

    StepVerifier.create(
            filter.filter(
                exchange,
                ignored -> {
                  downstreamCalled.set(true);
                  return Mono.empty();
                }))
        .verifyComplete();

    assertThat(downstreamCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void failsOpenForReadOnlyPublicRouteWhenRedisIsUnavailable() {
    when(redis.hasKey(RevokedSessionWebFilter.REVOKED_SESSION_KEY_PREFIX + "session-3"))
        .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
    var exchange =
        authenticatedExchange(MockServerHttpRequest.get("/api/v1/catalog/hotels"), "session-3");
    var downstreamCalled = new AtomicBoolean();

    StepVerifier.create(
            filter.filter(
                exchange,
                ignored -> {
                  downstreamCalled.set(true);
                  return Mono.empty();
                }))
        .verifyComplete();

    assertThat(downstreamCalled).isTrue();
  }

  @Test
  void doesNotMisclassifyDownstreamFailureAsRedisFailure() {
    when(redis.hasKey(RevokedSessionWebFilter.REVOKED_SESSION_KEY_PREFIX + "session-4"))
        .thenReturn(Mono.just(false));
    var exchange = authenticatedExchange(MockServerHttpRequest.post("/api/v1/bookings"), "session-4");
    var downstreamError = new IllegalArgumentException("downstream failed");

    StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(downstreamError)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(downstreamError))
        .verify();
  }

  private ServerWebExchange authenticatedExchange(
      MockServerHttpRequest.BaseBuilder<?> request, String sessionId) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("user-1")
            .claim("sid", sessionId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    var authentication = new JwtAuthenticationToken(jwt);
    return MockServerWebExchange.from(request.build())
        .mutate()
        .principal(Mono.just(authentication))
        .build();
  }
}
