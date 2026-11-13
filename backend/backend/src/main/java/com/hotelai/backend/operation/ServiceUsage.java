package com.hotelai.backend.operation;

import com.hotelai.backend.account.User;
import com.hotelai.backend.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_usages")
@Getter
@NoArgsConstructor
public class ServiceUsage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id")
  private ExtraService service;

  private BigDecimal quantity;

  @Column(name = "unit_price")
  private BigDecimal unitPrice;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(name = "used_at", insertable = false, updatable = false)
  private Instant usedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recorded_by")
  private User recordedBy;

  private String note;

  public ServiceUsage(Booking b, ExtraService s, BigDecimal q, User u, String note) {
    booking = b;
    service = s;
    quantity = q;
    unitPrice = s.getPrice();
    totalAmount = unitPrice.multiply(q);
    recordedBy = u;
    this.note = note;
  }
}
