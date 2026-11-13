package com.hotelai.customer.config;

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
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ServiceBoundaryFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority(
                                "ROLE_STAFF", "ROLE_RECEPTIONIST", "ROLE_MANAGER", "ROLE_ADMIN")
                        .requestMatchers("/api/v1/me", "/api/v1/customers/me", "/api/v1/profile").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
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
                .filter(value -> !value.isEmpty())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .forEach(result::add);
        return result;
    }

    private static Stream<String> stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf);
        if (value instanceof String text) return Stream.of(text.split(",")).map(String::trim);
        return Stream.empty();
    }

}

@Component
class ServiceBoundaryFilter extends OncePerRequestFilter {
    private final String internalKey;

    ServiceBoundaryFilter(@Value("${hotel.security.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")
                && !constantTimeEquals(internalKey, request.getHeader("X-Internal-Key"))) {
            reject(response, 401, "Invalid internal service credential");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
    }
}
