package com.hotelai.backend.account;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public RefreshToken(User user, String hash, Instant expiresAt) {
    this.user = user;
    this.tokenHash = hash;
    this.expiresAt = expiresAt;
  }

  public boolean usable() {
    return revokedAt == null && expiresAt.isAfter(Instant.now());
  }

  public void revoke() {
    revokedAt = Instant.now();
  }
}
