package com.hotelai.identity;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class JwksController {
  private final JwtKeyMaterial material;

  @GetMapping("/.well-known/jwks.json")
  Map<String, Object> jwks() {
    return material.publicJwkSet();
  }
}
