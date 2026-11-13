package com.hotelai.reporting.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuditRedactorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditRedactor redactor = new AuditRedactor(objectMapper);

    @Test
    void removesSecretsRecursively() throws Exception {
        var input = objectMapper.readTree("""
                {
                  "email": "guest@example.com",
                  "password": "NeverStoreMe",
                  "nested": {
                    "accessToken": "jwt-value",
                    "otp": "123456"
                  }
                }
                """);

        var result = redactor.redact(input);

        assertThat(result.path("email").asText()).isEqualTo("g***@example.com");
        assertThat(result.path("password").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("nested").path("accessToken").asText())
                .isEqualTo("[REDACTED]");
        assertThat(result.path("nested").path("otp").asText())
                .isEqualTo("[REDACTED]");
    }

    @Test
    void masksPhoneAndCardLikeNumbersInFreeText() {
        String result = redactor.maskText(
                "Liên hệ 0901234567, thẻ 4111 1111 1111 1111.");

        assertThat(result).doesNotContain("0901234567");
        assertThat(result).doesNotContain("4111 1111 1111 1111");
        assertThat(result).contains("4567", "1111");
    }

    @Test
    void sha256IsStableAndSensitiveToInput() {
        assertThat(AuditLedgerService.sha256("same"))
                .isEqualTo(AuditLedgerService.sha256("same"))
                .isNotEqualTo(AuditLedgerService.sha256("different"))
                .hasSize(64);
    }

    @Test
    void auditJsonCanonicalizationIgnoresObjectPropertyOrder() throws Exception {
        var first = objectMapper.readTree(
                "{\"z\":{\"second\":2,\"first\":1},\"a\":[{\"y\":2,\"x\":1}]}" );
        var second = objectMapper.readTree(
                "{\"a\":[{\"x\":1,\"y\":2}],\"z\":{\"first\":1,\"second\":2}}" );

        assertThat(objectMapper.writeValueAsString(
                AuditLedgerService.canonicalJson(first, objectMapper)))
                .isEqualTo(objectMapper.writeValueAsString(
                        AuditLedgerService.canonicalJson(second, objectMapper)));
    }
}
