package com.hotelai.stay.config;

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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
class SecurityConfig {
    @Bean
    SecurityFilterChain staySecurity(
            HttpSecurity http,
            InternalServiceCredentialFilter internalFilter,
            SecurityProblemWriter problems) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/prometheus").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/stays/**", "/api/v1/admin/stay-reservations/**")
                            .hasAnyRole("RECEPTIONIST", "MANAGER", "ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problems.write(request, response, 401, "unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, error) ->
                                problems.write(request, response, 403, "forbidden", "Access is denied")))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtConverter())))
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
    private final SecurityProblemWriter problems;

    InternalServiceCredentialFilter(StayProperties properties, SecurityProblemWriter problems) {
        this.internalKey = properties.security().internalKey();
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String actual = request.getHeader("X-Internal-Key");
        if (internalKey == null || internalKey.isBlank() || actual == null
                || !MessageDigest.isEqual(
                        internalKey.getBytes(StandardCharsets.UTF_8),
                        actual.getBytes(StandardCharsets.UTF_8))) {
            problems.write(request, response, 401, "unauthorized", "Invalid internal service credential");
            return;
        }
        chain.doFilter(request, response);
    }
}
