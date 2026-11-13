package com.hotelai.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class JwtService {
  private final JwtEncoder encoder;
  private final IdentityProperties properties;

  String issue(Account account, AuthSession session) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(properties.security().accessTtl());
    List<String> roles = account.getRoles().stream().map(Role::getCode).sorted().toList();
    List<String> permissions = account.permissionCodes().stream().sorted().toList();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.security().issuer())
            .audience(List.of(properties.security().audience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(account.getEmail())
            .id(UUID.randomUUID().toString())
            .claim("uid", account.getId().toString())
            .claim("sid", session.getId().toString())
            .claim("sessionVersion", session.getTokenVersion())
            .claim("roles", roles)
            .claim("permissions", permissions)
            .claim("hotelId", properties.hotelId())
            .claim("accountType", account.getType().name())
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId(properties.security().keyId())
            .type("JWT")
            .build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
