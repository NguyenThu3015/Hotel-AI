package com.hotelai.backend.operation;

import com.hotelai.backend.catalog.Hotel;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotel_services")
@Getter
@NoArgsConstructor
public class ExtraService {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  private String code;
  private String name;
  private String unit;
  private BigDecimal price;
  private boolean active = true;

  public ExtraService(Hotel h, String c, String n, String unit, BigDecimal price) {
    hotel = h;
    code = c;
    name = n;
    this.unit = unit;
    this.price = price;
  }

  public void update(String n, String u, BigDecimal p, boolean a) {
    name = n;
    unit = u;
    price = p;
    active = a;
  }
}
