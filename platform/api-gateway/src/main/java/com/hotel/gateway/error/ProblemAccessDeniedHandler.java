package com.hotel.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ProblemAccessDeniedHandler implements ServerAccessDeniedHandler {

  private final ProblemResponseWriter writer;

  public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
    return writer.write(
        exchange,
        HttpStatus.FORBIDDEN,
        "Forbidden",
        "The authenticated principal is not allowed to access this resource",
        "https://hotel.example/problems/forbidden");
  }
}
