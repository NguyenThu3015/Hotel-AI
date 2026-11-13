package com.hotelai.review.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    SecurityFilterChain reviewSecurity(
            HttpSecurity http, SecurityProblemWriter problems) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/prometheus").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/reviews/**")
                            .hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews")
                            .hasRole("CUSTOMER")
                        .requestMatchers(
                                "/api/v1/reviews/mine",
                                "/api/v1/review-stays",
                                "/api/v1/review-stays/**")
                            .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews", "/api/v1/reviews/*")
                            .permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problems.write(
                                        request, response, 401,
                                        "unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, error) ->
                                problems.write(
                                        request, response, 403,
                                        "forbidden", "Access is denied")))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtConverter())))
                .build();
    }

    private static JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("uid");
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
        return converter;
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> result = new ArrayList<>();
        stringClaim(jwt.getClaims(), "roles")
                .map(value -> "ROLE_" + value.toUpperCase(java.util.Locale.ROOT))
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

@Component
class SecurityProblemWriter {
    private final ObjectMapper objectMapper;

    SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "type", "https://hotel-ai.local/problems/" + code,
                "title", code,
                "status", status,
                "detail", detail,
                "instance", request.getRequestURI()));
    }
}

@Component
class CorrelationIdFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String value = request.getHeader(HEADER);
        String correlationId = value == null || value.isBlank()
                ? UUID.randomUUID().toString() : value.trim();
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
