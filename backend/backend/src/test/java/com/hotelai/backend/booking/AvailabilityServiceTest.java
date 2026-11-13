package com.hotelai.backend.booking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AvailabilityServiceTest {
  @Test
  void acceptsHalfOpenFutureInterval() {
    AvailabilityService service = new AvailabilityService(null, null);
    LocalDate in = LocalDate.now().plusDays(1);
    assertThatCode(() -> service.validate(in, in.plusDays(2), 2, 1)).doesNotThrowAnyException();
  }

  @Test
  void rejectsCheckoutNotAfterCheckin() {
    AvailabilityService service = new AvailabilityService(null, null);
    LocalDate date = LocalDate.now().plusDays(1);
    assertThatThrownBy(() -> service.validate(date, date, 2, 1))
        .hasMessageContaining("Khoảng ngày");
  }
}
