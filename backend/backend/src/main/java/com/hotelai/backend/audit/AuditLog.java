package com.hotelai.backend.audit;

import com.hotelai.backend.account.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private String entityId;

  @Column(columnDefinition = "text")
  private String details;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public AuditLog(
      User user, String action, String entityType, String entityId, String details, String ip) {
    this.user = user;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.details = details;
    this.ipAddress = ip;
  }
}
