package com.hotelai.backend.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  @Test
  void rejectsTamperedToken() {
    JwtService jwt =
        new JwtService(
            new ObjectMapper(),
            "0123456789012345678901234567890123456789",
            15,
            "",
            "http://identity-service:8101",
            "hotel-api");
    assertThatThrownBy(() -> jwt.verify("eyJhbGciOiJIUzI1NiJ9.e30.invalid"))
        .hasMessageContaining("không hợp lệ");
  }
}
