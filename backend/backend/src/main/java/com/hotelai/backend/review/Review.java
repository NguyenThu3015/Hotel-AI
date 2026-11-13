package com.hotelai.backend.review;

import com.hotelai.backend.account.User;
import com.hotelai.backend.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor
public class Review {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  private int rating;

  @Column(columnDefinition = "text")
  private String comment;

  private String status = "PUBLISHED";

  private String sentiment;

  @Column(name = "sentiment_score")
  private BigDecimal sentimentScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "topics", columnDefinition = "jsonb")
  private List<String> sentimentTopics = new ArrayList<>();

  @Column(name = "hotel_response", columnDefinition = "text")
  private String hotelResponse;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  public Review(Booking b, User u, int rating, String comment) {
    booking = b;
    user = u;
    this.rating = rating;
    this.comment = comment;
  }

  public void moderate(String s) {
    status = s;
  }

  public void respond(String r) {
    hotelResponse = r;
    respondedAt = Instant.now();
  }

  public void analyze(String sentiment, BigDecimal score, List<String> topics) {
    this.sentiment = sentiment;
    this.sentimentScore = score;
    this.sentimentTopics = new ArrayList<>(topics);
  }
}
