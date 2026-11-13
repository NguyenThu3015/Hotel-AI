package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_rates")
@Getter
@NoArgsConstructor
public class RoomRate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_type_id")
  private RoomType roomType;

  @Column(name = "rate_date")
  private LocalDate rateDate;

  private BigDecimal price;

  @Column(name = "available_inventory")
  private Integer availableInventory;

  @Column(name = "min_stay")
  private int minStay = 1;

  private boolean closed;
  @Version private long version;

  public RoomRate(RoomType rt, LocalDate d, BigDecimal p, Integer inv, int stay, boolean closed) {
    roomType = rt;
    rateDate = d;
    price = p;
    availableInventory = inv;
    minStay = stay;
    this.closed = closed;
  }

  public void update(BigDecimal p, Integer inv, int stay, boolean c) {
    price = p;
    availableInventory = inv;
    minStay = stay;
    closed = c;
  }
}
