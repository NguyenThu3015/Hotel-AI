package com.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "JWT_ISSUER_URI=https://identity.test",
      "JWT_JWK_SET_URI=https://identity.test/.well-known/jwks.json",
      "REVIEW_SERVICE_URL=http://127.0.0.1:1",
      "debug=false"
    })
class SecurityProblemResponseTest {

  @Value("${local.server.port}") int port;

  @Test
  void protectedRouteReturnsRfc7807ResponseWithCorrelationId() {
    WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

    client
        .get()
        .uri("/api/v1/me")
        .header("X-Correlation-Id", "security-test-1")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .valueEquals("X-Correlation-Id", "security-test-1")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.correlationId")
        .isEqualTo("security-test-1");
  }

  @Test
  void corsPreflightAllowsConfiguredFrontend() {
    WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

    client
        .options()
        .uri("/api/v1/me")
        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

    assertThat(port).isPositive();
  }

  @Test
  void publishedReviewsDoNotRequireABearerToken() {
    WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

    client
        .get()
        .uri("/api/v1/reviews/summary")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
  }
}
