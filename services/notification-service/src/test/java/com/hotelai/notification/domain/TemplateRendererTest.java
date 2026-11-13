package com.hotelai.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersAllNamedVariables() {
        assertEquals("Booking B-123 for An", renderer.render(
                "Booking {{bookingCode}} for {{ customerName }}",
                Map.of("bookingCode", "B-123", "customerName", "An")));
    }

    @Test
    void refusesIncompleteTemplateData() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render("Hello {{name}}", Map.of()));
    }
}
