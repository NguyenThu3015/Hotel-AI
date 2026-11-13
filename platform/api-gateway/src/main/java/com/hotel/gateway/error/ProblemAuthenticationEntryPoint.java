package com.hotel.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private final ProblemResponseWriter writer;

  public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
    return writer.write(
        exchange,
        HttpStatus.UNAUTHORIZED,
        "Unauthorized",
        "A valid bearer access token is required",
        "https://hotel.example/problems/unauthorized");
  }
}
