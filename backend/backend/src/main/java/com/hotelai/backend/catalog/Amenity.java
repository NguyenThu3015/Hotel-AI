package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "amenities")
@Getter
@NoArgsConstructor
public class Amenity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  @Column(name = "icon_url")
  private String iconUrl;

  public Amenity(String n, String d, String i) {
    name = n;
    description = d;
    iconUrl = i;
  }

  public void update(String n, String d, String i) {
    name = n;
    description = d;
    iconUrl = i;
  }
}
