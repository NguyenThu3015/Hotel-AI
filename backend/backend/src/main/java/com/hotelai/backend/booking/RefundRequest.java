package com.hotelai.backend.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refund_requests")
@Getter
@NoArgsConstructor
public class RefundRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  private BigDecimal amount;
  private String reason;
  private String status = "PENDING";

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public RefundRequest(Booking booking, BigDecimal amount, String reason) {
    this.booking = booking;
    this.amount = amount;
    this.reason = reason;
  }

  public void complete() {
    status = "COMPLETED";
    completedAt = Instant.now();
  }
}
