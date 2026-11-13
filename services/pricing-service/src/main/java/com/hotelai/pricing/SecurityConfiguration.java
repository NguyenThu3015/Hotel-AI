package com.hotelai.pricing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, InternalServiceFilter internalFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/pricing/**")
                    .hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
        .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static JwtAuthenticationConverter jwtConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("uid");
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfiguration::authorities);
    return converter;
  }

  private static Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    stringClaim(jwt.getClaims(), "roles")
        .map(value -> "ROLE_" + value.toUpperCase(java.util.Locale.ROOT))
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
    stringClaim(jwt.getClaims(), "permissions")
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
    return authorities;
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

@Component
class InternalServiceFilter extends OncePerRequestFilter {
  private final String internalKey;

  InternalServiceFilter(@Value("${hotel.security.internal-key}") String internalKey) {
    this.internalKey = internalKey;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!constantTimeEquals(internalKey, request.getHeader("X-Internal-Key"))) {
      response.setStatus(401);
      response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"type\":\"https://hotel-ai.local/problems/invalid_internal_credential\","
                  + "\"title\":\"Unauthorized\",\"status\":401,"
                  + "\"detail\":\"Invalid internal service credential\","
                  + "\"correlationId\":\""
                  + CorrelationContext.current()
                  + "\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }
}
