package com.hotelai.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelai.backend.account.User;
import com.hotelai.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final ObjectMapper json;
  private final byte[] secret;
  private final long minutes;
  private final JwtDecoder identityDecoder;
  private final String identityIssuer;
  private final String identityAudience;

  public JwtService(
      ObjectMapper json,
      @Value("${hotel.security.jwt-secret}") String secret,
      @Value("${hotel.security.access-token-minutes}") long minutes,
      @Value("${hotel.security.identity-jwk-set-uri:}") String identityJwkSetUri,
      @Value("${hotel.security.identity-issuer}") String identityIssuer,
      @Value("${hotel.security.identity-audience}") String identityAudience) {
    this.json = json;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.minutes = minutes;
    this.identityDecoder =
        identityJwkSetUri == null || identityJwkSetUri.isBlank()
            ? null
            : NimbusJwtDecoder.withJwkSetUri(identityJwkSetUri).build();
    this.identityIssuer = identityIssuer;
    this.identityAudience = identityAudience;
    if (this.secret.length < 32)
      throw new IllegalStateException("JWT secret phải có ít nhất 32 bytes");
  }

  public String issue(User user) {
    try {
      Instant now = Instant.now();
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("sub", user.getEmail());
      payload.put("uid", user.getId());
      payload.put("roles", user.getRoles().stream().map(r -> r.getName()).toList());
      payload.put("iat", now.getEpochSecond());
      payload.put("exp", now.plus(minutes, ChronoUnit.MINUTES).getEpochSecond());
      String h = encode(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
      String p = encode(json.writeValueAsBytes(payload));
      return h + "." + p + "." + encode(sign(h + "." + p));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Claims verify(String token) {
    try {
      String[] part = token.split("\\.");
      if (part.length != 3) throw new Exception();
      Map<String, Object> header =
          json.readValue(Base64.getUrlDecoder().decode(part[0]), new TypeReference<>() {});
      if ("RS256".equals(header.get("alg"))) return verifyIdentityToken(token);
      if (!"HS256".equals(header.get("alg"))
          || !MessageDigest.isEqual(
              sign(part[0] + "." + part[1]), Base64.getUrlDecoder().decode(part[2]))) {
        throw new Exception();
      }
      Map<String, Object> p =
          json.readValue(Base64.getUrlDecoder().decode(part[1]), new TypeReference<>() {});
      if (((Number) p.get("exp")).longValue() <= Instant.now().getEpochSecond())
        throw new Exception();
      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) p.get("roles");
      return new Claims((String) p.get("sub"), roles);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Access token không hợp lệ hoặc đã hết hạn");
    }
  }

  private Claims verifyIdentityToken(String token) {
    if (identityDecoder == null)
      throw new IllegalStateException("Identity JWKS chưa được cấu hình");
    var decoded = identityDecoder.decode(token);
    if (!identityIssuer.equals(decoded.getIssuer().toString())
        || !decoded.getAudience().contains(identityAudience)) {
      throw new IllegalArgumentException("Issuer hoặc audience không hợp lệ");
    }
    List<String> roles = decoded.getClaimAsStringList("roles");
    return new Claims(
        decoded.getSubject(), roles == null ? Collections.emptyList() : List.copyOf(roles));
  }

  private byte[] sign(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }

  private String encode(byte[] b) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public record Claims(String subject, List<String> roles) {}
}
