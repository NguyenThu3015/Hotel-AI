package com.hotel.gateway.config;

import com.hotel.gateway.error.ProblemAccessDeniedHandler;
import com.hotel.gateway.error.ProblemAuthenticationEntryPoint;
import com.hotel.gateway.error.ProblemResponseWriter;
import com.hotel.gateway.filter.RevokedSessionWebFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import reactor.core.publisher.Flux;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  private static final String[] PUBLIC_AUTH_ENDPOINTS = {
    "/api/v1/auth/register",
    "/api/v1/auth/register/resend",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/otp",
    "/api/v1/auth/verify-email",
    "/api/v1/auth/reset-password",
    "/api/v1/auth/forgot-password",
    "/api/v1/auth/register/verify",
    "/api/v1/auth/password/forgot",
    "/api/v1/auth/password/reset"
  };

  @Bean
  SecurityWebFilterChain gatewaySecurityWebFilterChain(
      ServerHttpSecurity http,
      CorsConfigurationSource gatewayCorsConfigurationSource,
      ProblemAuthenticationEntryPoint authenticationEntryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler,
      ProblemResponseWriter problemResponseWriter,
      ReactiveStringRedisTemplate redisTemplate) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> cors.configurationSource(gatewayCorsConfigurationSource))
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers(PUBLIC_AUTH_ENDPOINTS)
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .pathMatchers("/api/v1/ping")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.GET,
                        "/api/v1/catalog/**",
                        "/api/v1/availability/**",
                        "/api/v1/hotel-services",
                        "/api/v1/hotel-services/**",
                        "/api/v1/reviews",
                        "/api/v1/reviews/*")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.POST,
                        "/api/v1/ai/chat",
                        "/api/v1/ai/recommendations",
                        "/api/v1/payments/webhook/**",
                        "/api/v1/payments/webhooks/**")
                    .permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/v1/ai/knowledge")
                    .hasAnyRole("MANAGER", "ADMIN")
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterAfter(
            new RevokedSessionWebFilter(redisTemplate, problemResponseWriter),
            SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE)
        .build();
  }

  static ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
    ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
    converter.setPrincipalClaimName("uid");
    converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(authorities(jwt)));
    return converter;
  }

  private static Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<GrantedAuthority> result = new ArrayList<>();
    stringClaim(jwt.getClaims(), "roles")
        .map(value -> "ROLE_" + value.toUpperCase(Locale.ROOT))
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .forEach(result::add);
    stringClaim(jwt.getClaims(), "permissions")
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .forEach(result::add);
    return result;
  }

  private static Stream<String> stringClaim(Map<String, Object> claims, String name) {
    Object value = claims.get(name);
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf);
    }
    if (value instanceof String text) {
      return Stream.of(text.split(",")).map(String::trim);
    }
    return Stream.empty();
  }
}
