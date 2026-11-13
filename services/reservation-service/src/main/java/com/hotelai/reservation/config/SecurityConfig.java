package com.hotelai.reservation.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    SecurityFilterChain reservationSecurity(
            HttpSecurity http,
            InternalServiceCredentialFilter internalFilter,
            SecurityProblemWriter problemWriter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority(
                                "ROLE_RECEPTIONIST", "ROLE_MANAGER", "ROLE_ADMIN")
                        .requestMatchers("/api/v1/reservations/**", "/api/v1/bookings/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problemWriter.write(request, response, 401, "unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, error) ->
                                problemWriter.write(request, response, 403, "forbidden", "Access is denied")))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
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
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf);
        if (value instanceof String text) return Stream.of(text.split(",")).map(String::trim);
        return Stream.empty();
    }
}

@Component
class InternalServiceCredentialFilter extends OncePerRequestFilter {
    private final String internalKey;
    private final SecurityProblemWriter problemWriter;

    InternalServiceCredentialFilter(ReservationProperties properties, SecurityProblemWriter problemWriter) {
        this.internalKey = properties.security().internalKey();
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!constantTimeEquals(internalKey, request.getHeader("X-Internal-Key"))) {
            problemWriter.write(
                    request, response, 401, "unauthorized", "Invalid internal service credential");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
