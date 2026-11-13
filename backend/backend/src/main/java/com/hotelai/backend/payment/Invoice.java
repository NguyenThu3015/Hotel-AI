package com.hotelai.backend.payment;

import com.hotelai.backend.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor
public class Invoice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @Column(name = "invoice_number", unique = true)
  private String invoiceNumber;

  private BigDecimal subtotal;

  @Column(name = "discount_amount")
  private BigDecimal discountAmount;

  @Column(name = "tax_amount")
  private BigDecimal taxAmount;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(name = "issued_at", insertable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "pdf_url")
  private String pdfUrl;

  public Invoice(Booking b) {
    booking = b;
    invoiceNumber = "INV-" + b.getBookingCode();
    subtotal = b.getRoomSubtotal().add(b.getServiceSubtotal());
    discountAmount = b.getDiscountAmount();
    taxAmount = b.getTaxAmount();
    totalAmount = b.getTotalAmount();
  }
}
