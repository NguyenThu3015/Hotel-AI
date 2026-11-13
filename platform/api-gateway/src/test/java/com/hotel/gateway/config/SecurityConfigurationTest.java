package com.hotel.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {
  @Test
  void mapsAdminRoleAndFineGrainedPermissionsFromIdentityToken() {
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("admin@hotelai.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("uid", UUID.randomUUID().toString())
            .claim("roles", List.of("ADMIN"))
            .claim("permissions", List.of("staff:read", "roles:write"))
            .build();

    var authentication =
        SecurityConfiguration.jwtAuthenticationConverter().convert(jwt).block();

    assertThat(authentication).isNotNull();
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .contains("ROLE_ADMIN", "staff:read", "roles:write");
  }

  @Test
  void mapsCommaSeparatedRolesForBackwardCompatibleTokens() {
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("manager@hotelai.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("uid", UUID.randomUUID().toString())
            .claim("roles", "MANAGER,RECEPTIONIST")
            .build();

    var authentication =
        SecurityConfiguration.jwtAuthenticationConverter().convert(jwt).block();

    assertThat(authentication).isNotNull();
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .contains("ROLE_MANAGER", "ROLE_RECEPTIONIST");
  }
}
