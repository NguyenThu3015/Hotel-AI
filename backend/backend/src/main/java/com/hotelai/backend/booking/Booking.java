package com.hotelai.backend.booking;

import com.hotelai.backend.account.User;
import com.hotelai.backend.catalog.Hotel;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "booking_code", unique = true)
  private String bookingCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @Column(name = "check_in_date")
  private LocalDate checkInDate;

  @Column(name = "check_out_date")
  private LocalDate checkOutDate;

  private int adults;
  private int children;
  private String status;
  private String source = "WEB";

  @Column(name = "contact_name")
  private String contactName;

  @Column(name = "contact_email")
  private String contactEmail;

  @Column(name = "contact_phone")
  private String contactPhone;

  @Column(name = "special_requests", columnDefinition = "text")
  private String specialRequests;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "promotion_id")
  private Promotion promotion;

  @Column(name = "room_subtotal")
  private BigDecimal roomSubtotal;

  @Column(name = "service_subtotal")
  private BigDecimal serviceSubtotal = BigDecimal.ZERO;

  @Column(name = "discount_amount")
  private BigDecimal discountAmount;

  @Column(name = "tax_amount")
  private BigDecimal taxAmount;

  @Column(name = "service_fee")
  private BigDecimal serviceFee;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(name = "paid_amount")
  private BigDecimal paidAmount = BigDecimal.ZERO;

  @Column(name = "hold_expires_at")
  private Instant holdExpiresAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "checked_out_at")
  private Instant checkedOutAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Version private long version;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<BookingRoom> rooms = new ArrayList<>();

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Guest> guests = new ArrayList<>();

  public Booking(
      String code,
      User user,
      Hotel hotel,
      LocalDate in,
      LocalDate out,
      int adults,
      int children,
      String name,
      String email,
      String phone,
      String requests,
      Promotion promo,
      BigDecimal room,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal fee,
      BigDecimal total,
      Instant expires) {
    bookingCode = code;
    this.user = user;
    this.hotel = hotel;
    checkInDate = in;
    checkOutDate = out;
    this.adults = adults;
    this.children = children;
    status = "PENDING_PAYMENT";
    contactName = name;
    contactEmail = email;
    contactPhone = phone;
    specialRequests = requests;
    promotion = promo;
    roomSubtotal = room;
    discountAmount = discount;
    taxAmount = tax;
    serviceFee = fee;
    totalAmount = total;
    holdExpiresAt = expires;
  }

  public void addRoom(BookingRoom r) {
    rooms.add(r);
  }

  public void addGuest(Guest g) {
    guests.add(g);
  }

  public void source(String source) {
    if (source != null && !source.isBlank()) this.source = source;
  }

  public void confirm() {
    if (!"PENDING_PAYMENT".equals(status)) throw new IllegalStateException();
    status = "CONFIRMED";
    holdExpiresAt = null;
    updatedAt = Instant.now();
  }

  public void paid(BigDecimal amount) {
    paidAmount = paidAmount.add(amount);
  }

  public void cancel(String reason) {
    status = "CANCELLED";
    cancelledAt = Instant.now();
    cancellationReason = reason;
    holdExpiresAt = null;
  }

  public void expire() {
    status = "EXPIRED";
    holdExpiresAt = null;
  }

  public void checkIn() {
    status = "CHECKED_IN";
    checkedInAt = Instant.now();
  }

  public void checkOut() {
    status = "CHECKED_OUT";
    checkedOutAt = Instant.now();
  }

  public void refunded(BigDecimal amount) {
    paidAmount = paidAmount.subtract(amount).max(BigDecimal.ZERO);
  }

  public void noShow() {
    status = "NO_SHOW";
    holdExpiresAt = null;
    updatedAt = Instant.now();
  }

  public void addService(BigDecimal amount) {
    serviceSubtotal = serviceSubtotal.add(amount);
    totalAmount = totalAmount.add(amount);
  }

  public void reschedule(
      LocalDate in,
      LocalDate out,
      BigDecimal room,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal fee,
      BigDecimal total,
      Instant hold) {
    checkInDate = in;
    checkOutDate = out;
    roomSubtotal = room;
    discountAmount = discount;
    taxAmount = tax;
    serviceFee = fee;
    totalAmount = total.add(serviceSubtotal);
    if ("PENDING_PAYMENT".equals(status)) holdExpiresAt = hold;
    updatedAt = Instant.now();
  }
}
