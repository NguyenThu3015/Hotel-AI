package com.hotelai.backend.booking;

import com.hotelai.backend.catalog.Room;
import com.hotelai.backend.catalog.RoomType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_rooms")
@Getter
@NoArgsConstructor
public class BookingRoom {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_type_id")
  private RoomType roomType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(name = "nightly_price")
  private BigDecimal nightlyPrice;

  @Column(name = "guests_count")
  private int guestsCount;

  public BookingRoom(Booking b, RoomType rt, BigDecimal p, int guests) {
    booking = b;
    roomType = rt;
    nightlyPrice = p;
    guestsCount = guests;
  }

  public void assign(Room room) {
    this.room = room;
  }
}
