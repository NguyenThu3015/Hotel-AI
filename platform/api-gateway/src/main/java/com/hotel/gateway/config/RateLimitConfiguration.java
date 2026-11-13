package com.hotel.gateway.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {

  @Bean
  KeyResolver authRateLimitKeyResolver() {
    return exchange -> {
      InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
      if (remoteAddress == null || remoteAddress.getAddress() == null) {
        return Mono.just("unknown-client");
      }
      return Mono.just(remoteAddress.getAddress().getHostAddress());
    };
  }
}
