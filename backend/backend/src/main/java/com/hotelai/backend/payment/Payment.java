package com.hotelai.backend.payment;

import com.hotelai.backend.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;

  private String provider;

  @Column(name = "provider_transaction_id", unique = true)
  private String providerTransactionId;

  private String type;
  private String status;
  private BigDecimal amount;

  @Column(name = "refunded_amount")
  private BigDecimal refundedAmount = BigDecimal.ZERO;

  private String currency = "VND";

  @Column(name = "request_payload", columnDefinition = "text")
  private String requestPayload;

  @Column(name = "response_payload", columnDefinition = "text")
  private String responsePayload;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  public Payment(Booking b, String key, String provider, String type, BigDecimal amount) {
    booking = b;
    idempotencyKey = key;
    this.provider = provider;
    this.type = type;
    this.amount = amount;
    status = "PENDING";
  }

  public boolean success(String txn, String payload) {
    if ("SUCCEEDED".equals(status)) return false;
    providerTransactionId = txn;
    responsePayload = payload;
    status = "SUCCEEDED";
    paidAt = Instant.now();
    return true;
  }

  public void fail(String payload) {
    if (!"SUCCEEDED".equals(status)) {
      status = "FAILED";
      responsePayload = payload;
    }
  }

  public void refund(BigDecimal refund) {
    refundedAmount = refundedAmount.add(refund);
    status = refundedAmount.compareTo(amount) >= 0 ? "REFUNDED" : "PARTIALLY_REFUNDED";
    refundedAt = Instant.now();
  }
}
