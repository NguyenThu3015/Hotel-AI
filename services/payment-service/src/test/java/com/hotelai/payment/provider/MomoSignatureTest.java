package com.hotelai.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MomoSignatureTest {
    @Test
    void computesHmacSha256AndComparesInConstantTime() {
        String expected = "9946dad4e00e913fc8be8e5d3f7e110a4a9e832f83fb09c345285d78638d8a0e";

        assertThat(MomoSignature.hmacSha256("abc", "secret")).isEqualTo(expected);
        assertThat(MomoSignature.matches("abc", expected, "secret")).isTrue();
        assertThat(MomoSignature.matches("abc", "00".repeat(32), "secret")).isFalse();
    }
}
