package com.hotel.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("gateway.limits")
public record GatewayLimitProperties(DataSize maxRequestSize) {

  public GatewayLimitProperties {
    if (maxRequestSize == null) {
      maxRequestSize = DataSize.ofMegabytes(2);
    }
    if (maxRequestSize.toBytes() <= 0) {
      throw new IllegalArgumentException("gateway.limits.max-request-size must be positive");
    }
  }
}
