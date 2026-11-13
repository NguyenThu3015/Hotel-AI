package com.hotelai.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_images")
@Getter
@NoArgsConstructor
public class RoomImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_type_id")
  private RoomType roomType;

  @Column(nullable = false)
  private String url;

  @Column(name = "alt_text")
  private String altText;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public RoomImage(RoomType roomType, String url, String altText, int displayOrder) {
    this.roomType = roomType;
    this.url = url;
    this.altText = altText;
    this.displayOrder = displayOrder;
  }
}
