package com.hotelai.backend.operation;

import com.hotelai.backend.account.User;
import com.hotelai.backend.catalog.Room;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "maintenance_requests")
@Getter
@NoArgsConstructor
public class MaintenanceRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reported_by")
  private User reportedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to")
  private User assignedTo;

  private String title;

  @Column(columnDefinition = "text")
  private String description;

  private String severity;
  private String status = "OPEN";

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolution_note", columnDefinition = "text")
  private String resolutionNote;

  @Version private long version;

  public MaintenanceRequest(Room r, User reporter, String title, String d, String severity) {
    room = r;
    reportedBy = reporter;
    this.title = title;
    description = d;
    this.severity = severity;
  }

  public void assign(User u) {
    assignedTo = u;
    status = "IN_PROGRESS";
  }

  public void resolve(String note) {
    status = "RESOLVED";
    resolutionNote = note;
    resolvedAt = Instant.now();
  }
}
