package com.hotelai.inventory.config;

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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, InternalServiceKeyFilter internalFilter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/v1/availability/**", "/api/v1/catalog/**").permitAll()
                        .requestMatchers("/api/v1/housekeeping/**", "/api/v1/maintenance/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST",
                                "ROLE_HOUSEKEEPER")
                        .requestMatchers("/api/v1/admin/rooms/*/status").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST",
                                "ROLE_HOUSEKEEPER")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/admin/destinations",
                                "/api/v1/admin/hotels/destinations",
                                "/api/v1/admin/rooms",
                                "/api/v1/admin/hotels/*/rooms",
                                "/api/v1/admin/rooms/*/status-history")
                        .hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RECEPTIONIST",
                                "ROLE_HOUSEKEEPER")
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(request, response, 401, "Chưa xác thực",
                                        "Cần access token hợp lệ để truy cập tài nguyên này."))
                        .accessDeniedHandler((request, response, error) ->
                                writeProblem(request, response, 403, "Không có quyền",
                                        "Tài khoản không có quyền thực hiện thao tác này.")))
                .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("uid");
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
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
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf);
        if (value instanceof String text) return Stream.of(text.split(",")).map(String::trim);
        return Stream.empty();
    }

    static void writeProblem(
            HttpServletRequest request, HttpServletResponse response, int status,
            String title, String detail) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.getWriter().write("{\"type\":\"https://hotel-ai.local/problems/security/" + status
                + "\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\",\"instance\":\""
                + request.getRequestURI() + "\",\"correlationId\":\""
                + (correlation == null ? "" : correlation) + "\"}");
    }
}

@Component
class InternalServiceKeyFilter extends OncePerRequestFilter {
    private final String internalKey;

    InternalServiceKeyFilter(@Value("${hotel.security.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String actual = request.getHeader("X-Internal-Key");
        if (!constantTimeEquals(internalKey, actual)) {
            SecurityConfig.writeProblem(
                    request, response, 401, "Chưa xác thực", "Thông tin xác thực nội bộ không hợp lệ.");
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
