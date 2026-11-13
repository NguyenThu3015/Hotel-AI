package com.hotelai.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  NimbusJwtDecoder jwtDecoder(
      JwtKeyMaterial keyMaterial, IdentityProperties properties, AuthSessionRepository sessions) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey()).build();
    OAuth2TokenValidator<Jwt> issuer =
        JwtValidators.createDefaultWithIssuer(properties.security().issuer());
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains(properties.security().audience())
                ? OAuth2TokenValidatorResult.success()
                : failure("invalid_token", "JWT audience is invalid");
    OAuth2TokenValidator<Jwt> activeSession = new ActiveSessionValidator(sessions);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, activeSession));
    return decoder;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectMapper objectMapper, NimbusJwtDecoder decoder) throws Exception {
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setPrincipalClaimName("sub");
    jwtConverter.setJwtGrantedAuthoritiesConverter(this::authorities);
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/register/verify",
                        "/api/v1/auth/register/resend",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/.well-known/jwks.json",
                        "/actuator/health/**",
                        "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(jwtConverter))
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeProblem(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "invalid_token",
                                "Access token không hợp lệ hoặc phiên đã bị thu hồi")))
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(
                    (request, response, exception) ->
                        writeProblem(
                            response,
                            objectMapper,
                            HttpServletResponse.SC_FORBIDDEN,
                            "access_denied",
                            "Không có quyền thực hiện thao tác này")))
        .headers(Customizer.withDefaults());
    return http.build();
  }

  private Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<GrantedAuthority> result = new ArrayList<>();
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles != null)
      roles.forEach(role -> result.add(new SimpleGrantedAuthority("ROLE_" + role)));
    List<String> permissions = jwt.getClaimAsStringList("permissions");
    if (permissions != null)
      permissions.forEach(value -> result.add(new SimpleGrantedAuthority(value)));
    return result;
  }

  private void writeProblem(
      HttpServletResponse response, ObjectMapper mapper, int status, String code, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(),
        Map.of(
            "type",
            "https://hotel-ai.local/problems/" + code,
            "title",
            status == 401 ? "Unauthorized" : "Forbidden",
            "status",
            status,
            "detail",
            detail,
            "code",
            code,
            "correlationId",
            CorrelationContext.current(),
            "timestamp",
            Instant.now()));
  }

  private static OAuth2TokenValidatorResult failure(String code, String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
  }

  private record ActiveSessionValidator(AuthSessionRepository sessions)
      implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
      try {
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        UUID accountId = UUID.fromString(jwt.getClaimAsString("uid"));
        Number version = jwt.getClaim("sessionVersion");
        AuthSession session = sessions.findById(sessionId).orElse(null);
        boolean valid =
            session != null
                && session.usable()
                && session.getTokenVersion() == version.longValue()
                && session.getAccount().getId().equals(accountId)
                && session.getAccount().getEmail().equalsIgnoreCase(jwt.getSubject());
        return valid
            ? OAuth2TokenValidatorResult.success()
            : failure("invalid_session", "Session is no longer active");
      } catch (Exception exception) {
        return failure("invalid_session", "Session claims are invalid");
      }
    }
  }
}
