package com.hotel.gateway.error;

import com.hotel.gateway.filter.CorrelationIdWebFilter;
import com.hotel.gateway.filter.PayloadTooLargeException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemErrorWebExceptionHandler implements WebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemErrorWebExceptionHandler.class);
  private final ProblemResponseWriter writer;

  public ProblemErrorWebExceptionHandler(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(throwable);
    }

    ErrorDescriptor descriptor = describe(throwable);
    if (descriptor.status().is5xxServerError()) {
      String correlationId =
          exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME);
      log.error("Gateway request failed; correlationId={}", correlationId, throwable);
    }
    return writer.write(
        exchange,
        descriptor.status(),
        descriptor.title(),
        descriptor.detail(),
        "https://hotel.example/problems/" + descriptor.type());
  }

  private ErrorDescriptor describe(Throwable throwable) {
    if (throwable instanceof PayloadTooLargeException payloadTooLarge) {
      return new ErrorDescriptor(
          HttpStatus.CONTENT_TOO_LARGE,
          "Content Too Large",
          "Request body must not exceed " + payloadTooLarge.getMaximumBytes() + " bytes",
          "payload-too-large");
    }
    if (throwable instanceof DataBufferLimitException) {
      return new ErrorDescriptor(
          HttpStatus.CONTENT_TOO_LARGE,
          "Content Too Large",
          "Request body exceeds the configured in-memory limit",
          "payload-too-large");
    }
    if (throwable instanceof ServerWebInputException) {
      return new ErrorDescriptor(
          HttpStatus.BAD_REQUEST, "Bad Request", "The request is malformed", "bad-request");
    }
    if (throwable instanceof HttpStatusCodeException statusException) {
      HttpStatusCode status = statusException.getStatusCode();
      return new ErrorDescriptor(
          status,
          reason(status),
          status.value() == 429 ? "Request rate limit exceeded" : "The request was rejected",
          status.value() == 429 ? "rate-limit-exceeded" : "request-rejected");
    }
    if (throwable instanceof ErrorResponse errorResponse) {
      HttpStatusCode status = errorResponse.getStatusCode();
      String detail = errorResponse.getBody().getDetail();
      return new ErrorDescriptor(
          status,
          reason(status),
          detail == null || detail.isBlank() ? "The request could not be processed" : detail,
          "request-error");
    }
    if (hasCause(throwable, ConnectException.class)
        || hasCause(throwable, UnknownHostException.class)
        || hasCauseNamed(throwable, "ConnectTimeoutException")) {
      return new ErrorDescriptor(
          HttpStatus.BAD_GATEWAY,
          "Bad Gateway",
          "The downstream service is unavailable",
          "downstream-unavailable");
    }
    return new ErrorDescriptor(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal Server Error",
        "The gateway could not process the request",
        "internal-error");
  }

  private String reason(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved == null ? "HTTP " + status.value() : resolved.getReasonPhrase();
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
    Throwable current = throwable;
    while (current != null) {
      if (expectedType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean hasCauseNamed(Throwable throwable, String simpleName) {
    Throwable current = throwable;
    while (current != null) {
      if (current.getClass().getSimpleName().equals(simpleName)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record ErrorDescriptor(
      HttpStatusCode status, String title, String detail, String type) {}
}
