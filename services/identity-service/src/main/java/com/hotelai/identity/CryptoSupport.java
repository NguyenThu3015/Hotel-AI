package com.hotelai.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

final class Hashing {
  private Hashing() {}

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  static boolean constantTimeEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }
}

record JwtKeyMaterial(RSAPublicKey publicKey, RSAPrivateKey privateKey, RSAKey jwk) {
  Map<String, Object> publicJwkSet() {
    return new JWKSet(jwk.toPublicJWK()).toJSONObject();
  }
}

@Configuration
@Slf4j
class JwtKeyConfiguration {
  @Bean
  JwtKeyMaterial jwtKeyMaterial(IdentityProperties properties, ResourceLoader loader) {
    try {
      String privateLocation = properties.security().privateKeyLocation();
      String publicLocation = properties.security().publicKeyLocation();
      KeyPair pair;
      if (privateLocation != null
          && !privateLocation.isBlank()
          && publicLocation != null
          && !publicLocation.isBlank()) {
        Resource publicResource = loader.getResource(publicLocation);
        Resource privateResource = loader.getResource(privateLocation);
        if (!publicResource.exists() && !privateResource.exists()) {
          pair = generateKeyPair();
          writePem(publicResource, "PUBLIC KEY", pair.getPublic().getEncoded());
          writePem(privateResource, "PRIVATE KEY", pair.getPrivate().getEncoded());
          log.warn("Generated a persistent development JWT key pair at the configured file locations.");
        } else if (publicResource.exists() && privateResource.exists()) {
          pair = new KeyPair(readPublic(publicResource), readPrivate(privateResource));
        } else {
          throw new IllegalStateException(
              "JWT key storage is incomplete; both private and public keys are required");
        }
      } else if ((privateLocation == null || privateLocation.isBlank())
          && (publicLocation == null || publicLocation.isBlank())) {
        pair = generateKeyPair();
        log.warn(
            "JWT RSA keys were not configured; generated an ephemeral development key. Configure JWT_PRIVATE_KEY_LOCATION and JWT_PUBLIC_KEY_LOCATION for every shared or production environment.");
      } else {
        throw new IllegalStateException("Both JWT key locations must be configured together");
      }
      RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
      RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
      RSAKey jwk =
          new RSAKey.Builder(publicKey)
              .privateKey(privateKey)
              .keyID(properties.security().keyId())
              .build();
      return new JwtKeyMaterial(publicKey, privateKey, jwk);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot load JWT RSA keys", exception);
    }
  }

  @Bean
  JwtEncoder jwtEncoder(JwtKeyMaterial material) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(material.jwk())));
  }

  private RSAPublicKey readPublic(Resource resource) throws Exception {
    byte[] bytes = decodePem(resource, "PUBLIC KEY");
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
  }

  private KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private void writePem(Resource resource, String type, byte[] encoded) throws Exception {
    if (!resource.isFile()) {
      throw new IllegalStateException("Generated JWT keys require writable file: locations");
    }
    Path target = resource.getFile().toPath();
    Files.createDirectories(target.getParent());
    String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
        .encodeToString(encoded);
    String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    Files.writeString(temporary, pem, StandardCharsets.US_ASCII);
    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private RSAPrivateKey readPrivate(Resource resource) throws Exception {
    byte[] bytes = decodePem(resource, "PRIVATE KEY");
    return (RSAPrivateKey)
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
  }

  private byte[] decodePem(Resource resource, String type) throws Exception {
    try (InputStream input = resource.getInputStream()) {
      String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
      String base64 =
          pem.replace("-----BEGIN " + type + "-----", "")
              .replace("-----END " + type + "-----", "")
              .replaceAll("\\s", "");
      return Base64.getDecoder().decode(base64);
    }
  }
}
