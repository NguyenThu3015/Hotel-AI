package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_types")
@Getter
@NoArgsConstructor
public class RoomType {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "max_adults")
  private int maxAdults;

  @Column(name = "max_children")
  private int maxChildren;

  @Column(name = "base_price")
  private BigDecimal basePrice;

  @Column(name = "size_m2")
  private BigDecimal sizeM2;

  @Column(name = "bed_type")
  private String bedType;

  private boolean active = true;
  @Version private long version;

  @ManyToMany
  @JoinTable(
      name = "room_type_amenities",
      joinColumns = @JoinColumn(name = "room_type_id"),
      inverseJoinColumns = @JoinColumn(name = "amenity_id"))
  private Set<Amenity> amenities = new HashSet<>();

  public RoomType(
      Hotel h,
      String c,
      String n,
      String d,
      int a,
      int ch,
      BigDecimal price,
      BigDecimal size,
      String bed,
      Set<Amenity> am) {
    hotel = h;
    code = c;
    name = n;
    description = d;
    maxAdults = a;
    maxChildren = ch;
    basePrice = price;
    sizeM2 = size;
    bedType = bed;
    amenities.addAll(am);
  }

  public void update(
      String n,
      String d,
      int a,
      int ch,
      BigDecimal price,
      BigDecimal size,
      String bed,
      boolean active,
      Set<Amenity> am) {
    name = n;
    description = d;
    maxAdults = a;
    maxChildren = ch;
    basePrice = price;
    sizeM2 = size;
    bedType = bed;
    this.active = active;
    amenities.clear();
    amenities.addAll(am);
  }
}
