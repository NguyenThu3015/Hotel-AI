package com.hotelai.backend.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guests")
@Getter
@NoArgsConstructor
public class Guest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @Column(name = "full_name")
  private String fullName;

  @Column(name = "document_type")
  private String documentType;

  @Column(name = "document_number")
  private String documentNumber;

  private String nationality;

  @Column(name = "primary_guest")
  private boolean primaryGuest;

  public Guest(
      Booking b, String n, String type, String number, String nationality, boolean primary) {
    booking = b;
    fullName = n;
    documentType = type;
    documentNumber = number;
    this.nationality = nationality;
    primaryGuest = primary;
  }
}
