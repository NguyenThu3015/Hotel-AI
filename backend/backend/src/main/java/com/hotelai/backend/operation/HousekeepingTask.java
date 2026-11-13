package com.hotelai.backend.operation;

import com.hotelai.backend.account.User;
import com.hotelai.backend.booking.Booking;
import com.hotelai.backend.catalog.Room;
import com.hotelai.backend.common.ApiException;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "housekeeping_tasks")
@Getter
@NoArgsConstructor
public class HousekeepingTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to")
  private User assignedTo;

  private String status = "OPEN";
  private String priority = "NORMAL";
  private String note;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version private long version;

  public HousekeepingTask(Room r, Booking b, String priority, String note) {
    room = r;
    booking = b;
    this.priority = priority;
    this.note = note;
  }

  public void assign(User u) {
    assignedTo = u;
    status = "ASSIGNED";
  }

  public void progress(String next) {
    if ("IN_PROGRESS".equals(next)) {
      if (!java.util.List.of("OPEN", "ASSIGNED").contains(status)) throw bad();
      status = next;
      startedAt = Instant.now();
    } else if ("COMPLETED".equals(next)) {
      if (!"IN_PROGRESS".equals(status)) throw bad();
      status = next;
      completedAt = Instant.now();
    } else throw bad();
  }

  private ApiException bad() {
    return new ApiException(HttpStatus.BAD_REQUEST, "Chuyển trạng thái công việc không hợp lệ");
  }
}
