package com.hotelai.stay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.stay.config.StayProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SensitiveDocumentCipherTest {
    @Test
    void encryptsWithRandomIvAndOnlyDerivesLastFourCharacters() {
        SensitiveDocumentCipher cipher = new SensitiveDocumentCipher(properties());

        String first = cipher.encrypt("079203001234");
        String second = cipher.encrypt("079203001234");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("079203001234");
        assertThat(SensitiveDocumentCipher.last4("0792 0300 1234")).isEqualTo("1234");
    }

    static StayProperties properties() {
        return new StayProperties(
                new StayProperties.Security(
                        "internal",
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
                new StayProperties.Clients(
                        "http://inventory", Duration.ofSeconds(2), Duration.ofSeconds(5)),
                new StayProperties.Stay("Asia/Ho_Chi_Minh", 14, 11, 18, 30),
                new StayProperties.Messaging(
                        "hotel.events",
                        "stay.reservation",
                        "reservation.*.v1",
                        "stay.inventory",
                        "inventory.#",
                        "stay.service-usage",
                        "hotel-service.usage.*.v1",
                        "hotel.events.dlx",
                        "stay.dlq",
                        "stay.dead",
                        1000,
                        5000));
    }
}
