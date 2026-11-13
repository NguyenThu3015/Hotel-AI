package com.hotelai.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {
  @Test
  void issuesRsaTokenWithStableGatewayClaimsAndKid() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var publicKey = (RSAPublicKey) pair.getPublic();
    var privateKey = (RSAPrivateKey) pair.getPrivate();
    RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("test-key").build();
    NimbusJwtEncoder encoder =
        new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    IdentityProperties properties =
        new IdentityProperties(
            "HOTEL-1",
            new IdentityProperties.Security(
                "https://identity.test", "hotel-api", "test-key", null, null, 15, 7, 10, 15),
            null);
    JwtService jwtService = new JwtService(encoder, properties);
    Role receptionist = new Role("RECEPTIONIST", "Receptionist", null, Set.of());
    Account account =
        Account.staff(
            "staff@example.com", "encoded", "Staff", null, "HOTEL-1", Set.of(receptionist));
    AuthSession session =
        new AuthSession(
            account, Hashing.sha256("refresh"), Instant.now().plusSeconds(3600), null, null);

    String token = jwtService.issue(account, session);
    var decoded = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);

    assertThat(decoded.getHeaders().get("kid")).isEqualTo("test-key");
    assertThat(decoded.getSubject()).isEqualTo("staff@example.com");
    assertThat(decoded.getClaimAsString("uid")).isEqualTo(account.getId().toString());
    assertThat(decoded.getClaimAsString("sid")).isEqualTo(session.getId().toString());
    assertThat(((Number) decoded.getClaim("sessionVersion")).longValue()).isZero();
    assertThat(decoded.getClaimAsStringList("roles")).containsExactly("RECEPTIONIST");
    assertThat(decoded.getAudience()).containsExactly("hotel-api");
    assertThat(decoded.getIssuer().toString()).isEqualTo("https://identity.test");
    assertThat(decoded.getId()).isNotBlank();
  }
}
