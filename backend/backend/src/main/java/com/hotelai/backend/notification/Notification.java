package com.hotelai.backend.notification;

import com.hotelai.backend.account.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  private String type;
  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public Notification(User u, String t, String title, String c) {
    user = u;
    type = t;
    this.title = title;
    content = c;
  }

  public void read() {
    readAt = Instant.now();
  }
}
