package com.hotel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class CorsConfiguration {

  @Bean
  CorsConfigurationSource gatewayCorsConfigurationSource(GatewayCorsProperties properties) {
    var configuration = new org.springframework.web.cors.CorsConfiguration();
    configuration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
    configuration.setAllowedMethods(properties.getAllowedMethods());
    configuration.setAllowedHeaders(properties.getAllowedHeaders());
    configuration.setExposedHeaders(properties.getExposedHeaders());
    configuration.setAllowCredentials(properties.isAllowCredentials());
    configuration.setMaxAge(properties.getMaxAge());

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
