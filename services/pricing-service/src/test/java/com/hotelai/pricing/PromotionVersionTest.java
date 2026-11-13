package com.hotelai.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionVersionTest {
  @Test
  void quotaAndBudgetStopPromotionAndReleaseRestoresCapacity() {
    PromotionVersion promotion =
        new PromotionVersion(
            UUID.randomUUID(),
            1,
            "ONEUSE",
            "One use",
            DiscountType.FIXED,
            new BigDecimal("50000"),
            null,
            null,
            null,
            null,
            Set.of(),
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            1,
            null,
            new BigDecimal("50000"),
            true,
            UUID.randomUUID());

    promotion.redeem(new BigDecimal("50000"));

    assertThat(promotion.getUsedCount()).isEqualTo(1);
    assertThat(promotion.getBudgetUsed()).isEqualByComparingTo("50000");
    assertThat(promotion.isActive()).isFalse();
    assertThatThrownBy(() -> promotion.redeem(BigDecimal.ONE))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exhausted");

    promotion.release(new BigDecimal("50000"));
    assertThat(promotion.getUsedCount()).isZero();
    assertThat(promotion.getBudgetUsed()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(promotion.isActive()).isTrue();
  }

  @Test
  void discountNeverExceedsSubtotalOrBudgetRemaining() {
    PromotionVersion promotion =
        new PromotionVersion(
            UUID.randomUUID(),
            1,
            "BIG",
            "Large discount",
            DiscountType.PERCENT,
            new BigDecimal("80"),
            null,
            null,
            null,
            null,
            Set.of(),
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            null,
            null,
            new BigDecimal("30000"),
            true,
            UUID.randomUUID());

    assertThat(promotion.calculateDiscount(new BigDecimal("100000"))).isEqualByComparingTo("30000");
  }
}
