package com.hotelai.customer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PiiMaskingTest {
    @Test
    void masksEmailWithoutRevealingLocalPart() {
        assertEquals("a***@example.com", com.hotelai.customer.api.PiiMasking.email("an.nguyen@example.com"));
    }

    @Test
    void masksPhoneExceptLastFourDigits() {
        assertEquals("***5567", com.hotelai.customer.api.PiiMasking.phone("+84 912 345 567"));
    }
}
