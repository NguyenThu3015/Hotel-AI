package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor
public class Hotel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String address;

  @Column(columnDefinition = "text")
  private String description;

  private String phone;
  private String email;

  @Column(name = "check_in_time")
  private LocalTime checkInTime = LocalTime.of(14, 0);

  @Column(name = "check_out_time")
  private LocalTime checkOutTime = LocalTime.NOON;

  private boolean active = true;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  public Hotel(String n, String a, String d, String p, String e) {
    name = n;
    address = a;
    description = d;
    phone = p;
    email = e;
  }

  public void update(String n, String a, String d, String p, String e, boolean active) {
    name = n;
    address = a;
    description = d;
    phone = p;
    email = e;
    this.active = active;
    updatedAt = Instant.now();
  }
}
