package com.hotel.gateway.filter;

import com.hotel.gateway.config.GatewayLimitProperties;
import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeWebFilter implements WebFilter {

  private final long maximumBytes;

  public RequestBodySizeWebFilter(GatewayLimitProperties properties) {
    this.maximumBytes = properties.maxRequestSize().toBytes();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    long declaredLength = exchange.getRequest().getHeaders().getContentLength();
    if (declaredLength > maximumBytes) {
      return Mono.error(new PayloadTooLargeException(maximumBytes));
    }

    AtomicLong receivedBytes = new AtomicLong();
    var decoratedRequest =
        new ServerHttpRequestDecorator(exchange.getRequest()) {
          @Override
          public Flux<DataBuffer> getBody() {
            Publisher<DataBuffer> limitedBody =
                super.getBody()
                    .handle(
                        (buffer, sink) -> {
                          long total = receivedBytes.addAndGet(buffer.readableByteCount());
                          if (total > maximumBytes) {
                            DataBufferUtils.release(buffer);
                            sink.error(new PayloadTooLargeException(maximumBytes));
                          } else {
                            sink.next(buffer);
                          }
                        });
            return Flux.from(limitedBody);
          }
        };

    return chain.filter(exchange.mutate().request(decoratedRequest).build());
  }
}
