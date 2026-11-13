package com.hotelai.backend.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "promotions")
@Getter
@NoArgsConstructor
public class Promotion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String code;

  private String name;

  @Column(name = "discount_type")
  private String discountType;

  @Column(name = "discount_value")
  private BigDecimal discountValue;

  @Column(name = "max_discount")
  private BigDecimal maxDiscount;

  @Column(name = "min_booking_amount")
  private BigDecimal minBookingAmount;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Column(name = "usage_limit")
  private Integer usageLimit;

  @Column(name = "used_count")
  private int usedCount;

  private boolean active = true;
  @Version private long version;

  public Promotion(
      String c,
      String n,
      String t,
      BigDecimal v,
      BigDecimal max,
      BigDecimal min,
      Instant from,
      Instant to,
      Integer limit) {
    code = c.toUpperCase();
    name = n;
    discountType = t;
    discountValue = v;
    maxDiscount = max;
    minBookingAmount = min;
    validFrom = from;
    validTo = to;
    usageLimit = limit;
  }

  public boolean valid(BigDecimal amount) {
    Instant n = Instant.now();
    return active
        && !n.isBefore(validFrom)
        && n.isBefore(validTo)
        && (usageLimit == null || usedCount < usageLimit)
        && (minBookingAmount == null || amount.compareTo(minBookingAmount) >= 0);
  }

  public BigDecimal discount(BigDecimal amount) {
    BigDecimal d =
        "PERCENT".equals(discountType)
            ? amount.multiply(discountValue).divide(BigDecimal.valueOf(100))
            : discountValue;
    if (maxDiscount != null && d.compareTo(maxDiscount) > 0) d = maxDiscount;
    return d.min(amount);
  }

  public void use() {
    usedCount++;
  }

  public void update(
      String n,
      BigDecimal v,
      BigDecimal max,
      BigDecimal min,
      Instant from,
      Instant to,
      Integer limit,
      boolean active) {
    name = n;
    discountValue = v;
    maxDiscount = max;
    minBookingAmount = min;
    validFrom = from;
    validTo = to;
    usageLimit = limit;
    this.active = active;
  }
}
