package com.hotel.gateway.filter;

import com.hotel.gateway.error.ProblemResponseWriter;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public final class RevokedSessionWebFilter implements WebFilter {

  static final String REVOKED_SESSION_KEY_PREFIX = "identity:revoked-session:";
  private static final Logger log = LoggerFactory.getLogger(RevokedSessionWebFilter.class);
  private static final Set<HttpMethod> SAFE_METHODS =
      Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
  private static final Set<String> SENSITIVE_PREFIXES =
      Set.of(
          "/api/v1/admin",
          "/api/v1/me",
          "/api/v1/profile",
          "/api/v1/customers",
          "/api/v1/bookings",
          "/api/v1/payments",
          "/api/v1/operations",
          "/api/v1/notifications");

  private final ReactiveStringRedisTemplate redisTemplate;
  private final ProblemResponseWriter problemWriter;

  public RevokedSessionWebFilter(
      ReactiveStringRedisTemplate redisTemplate, ProblemResponseWriter problemWriter) {
    this.redisTemplate = redisTemplate;
    this.problemWriter = problemWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange
        .getPrincipal()
        .ofType(JwtAuthenticationToken.class)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(
            authentication ->
                authentication
                    .map(token -> verifySession(token, exchange, chain))
                    .orElseGet(() -> chain.filter(exchange)));
  }

  private Mono<Void> verifySession(
      JwtAuthenticationToken authentication,
      ServerWebExchange exchange,
      WebFilterChain chain) {
    String sessionId = authentication.getToken().getClaimAsString("sid");
    if (sessionId == null || sessionId.isBlank()) {
      return problemWriter.write(
          exchange,
          HttpStatus.UNAUTHORIZED,
          "Unauthorized",
          "The access token is missing the required session identifier",
          "https://hotel.example/problems/missing-session-id");
    }

    return redisTemplate
        .hasKey(REVOKED_SESSION_KEY_PREFIX + sessionId)
        .switchIfEmpty(Mono.error(new IllegalStateException("Redis returned no revocation result")))
        .map(revoked -> new RevocationLookup(revoked, null))
        .onErrorResume(error -> Mono.just(new RevocationLookup(false, error)))
        .flatMap(
            lookup -> {
              if (lookup.error() != null) {
                return handleRedisFailure(exchange, chain, lookup.error());
              }
              if (lookup.revoked()) {
                return problemWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "The login session has been revoked",
                    "https://hotel.example/problems/session-revoked");
              }
              return chain.filter(exchange);
            });
  }

  private Mono<Void> handleRedisFailure(
      ServerWebExchange exchange, WebFilterChain chain, Throwable error) {
    String path = exchange.getRequest().getURI().getPath();
    String correlationId =
        exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME);
    if (isSensitive(exchange)) {
      log.error(
          "Session revocation check unavailable for sensitive route {}; correlationId={}; cause={}",
          path,
          correlationId,
          error.toString());
      return problemWriter.write(
          exchange,
          HttpStatus.SERVICE_UNAVAILABLE,
          "Service Unavailable",
          "Session revocation status cannot be verified; retry later",
          "https://hotel.example/problems/session-verification-unavailable");
    }

    log.warn(
        "Session revocation check unavailable for non-sensitive route {}; correlationId={}; cause={}",
        path,
        correlationId,
        error.toString());
    return chain.filter(exchange);
  }

  private boolean isSensitive(ServerWebExchange exchange) {
    HttpMethod method = exchange.getRequest().getMethod();
    if (method == null || !SAFE_METHODS.contains(method)) {
      return true;
    }
    String path = exchange.getRequest().getURI().getPath();
    return SENSITIVE_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private record RevocationLookup(boolean revoked, Throwable error) {}
}
