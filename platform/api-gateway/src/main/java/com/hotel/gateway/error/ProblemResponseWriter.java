package com.hotel.gateway.error;

import com.hotel.gateway.filter.CorrelationIdWebFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ProblemResponseWriter {

  public Mono<Void> write(
      ServerWebExchange exchange,
      HttpStatusCode status,
      String title,
      String detail,
      String problemType) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.empty();
    }

    String correlationId =
        exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME);
    String path = exchange.getRequest().getURI().getRawPath();
    String body =
        "{"
            + "\"type\":\""
            + escape(problemType)
            + "\","
            + "\"title\":\""
            + escape(title)
            + "\","
            + "\"status\":"
            + status.value()
            + ","
            + "\"detail\":\""
            + escape(detail)
            + "\","
            + "\"instance\":\""
            + escape(path)
            + "\","
            + "\"timestamp\":\""
            + Instant.now()
            + "\","
            + "\"correlationId\":\""
            + escape(correlationId)
            + "\"}";

    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    exchange.getResponse().getHeaders().setContentLength(bytes.length);
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
