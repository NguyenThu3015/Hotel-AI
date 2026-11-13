package com.hotelai.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PromotionTest {
  @Test
  void percentDiscountHonorsMaximum() {
    Promotion p =
        new Promotion(
            "SUMMER",
            "Summer",
            "PERCENT",
            new BigDecimal("20"),
            new BigDecimal("300000"),
            new BigDecimal("1000000"),
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60),
            10);
    assertThat(p.valid(new BigDecimal("2000000"))).isTrue();
    assertThat(p.discount(new BigDecimal("2000000"))).isEqualByComparingTo("300000");
  }

  @Test
  void minimumAmountIsEnforced() {
    Promotion p =
        new Promotion(
            "MIN",
            "Minimum",
            "FIXED",
            new BigDecimal("100000"),
            null,
            new BigDecimal("1000000"),
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60),
            null);
    assertThat(p.valid(new BigDecimal("999999"))).isFalse();
  }
}
