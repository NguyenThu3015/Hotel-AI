package com.hotelai.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookingStateTest {
  @Test
  void followsCoreLifecycle() {
    Booking b =
        new Booking(
            "BK1",
            null,
            null,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            2,
            0,
            "Guest",
            "g@example.com",
            null,
            null,
            null,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            new BigDecimal("10"),
            new BigDecimal("5"),
            new BigDecimal("115"),
            Instant.now().plusSeconds(60));
    assertThat(b.getStatus()).isEqualTo("PENDING_PAYMENT");
    b.paid(new BigDecimal("115"));
    b.confirm();
    b.checkIn();
    b.checkOut();
    assertThat(b.getStatus()).isEqualTo("CHECKED_OUT");
    assertThat(b.getPaidAmount()).isEqualByComparingTo("115");
  }
}
