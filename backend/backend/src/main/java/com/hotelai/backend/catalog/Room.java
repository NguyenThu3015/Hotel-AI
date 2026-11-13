package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_type_id")
  private RoomType roomType;

  @Column(name = "room_number")
  private String roomNumber;

  private String floor;
  private String status = "AVAILABLE";
  @Version private long version;

  public Room(Hotel h, RoomType t, String number, String floor) {
    hotel = h;
    roomType = t;
    roomNumber = number;
    this.floor = floor;
  }

  public void update(RoomType t, String number, String floor, String status) {
    roomType = t;
    roomNumber = number;
    this.floor = floor;
    this.status = status;
  }

  public void status(String s) {
    status = s;
  }
}
