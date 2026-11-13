package com.hotelai.backend.notification;

import com.hotelai.backend.account.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationRepository repo;
  private final UserRepository users;

  @GetMapping
  List<View> list(Principal p) {
    var u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    return repo.findByUserIdOrderByCreatedAtDesc(u.getId()).stream().map(View::of).toList();
  }

  @PatchMapping("/{id}/read")
  @Transactional
  void read(@PathVariable Long id, Principal p) {
    Notification n = repo.findById(id).orElseThrow();
    if (!n.getUser().getEmail().equalsIgnoreCase(p.getName()))
      throw new org.springframework.security.access.AccessDeniedException("forbidden");
    n.read();
  }

  record View(
      Long id, String type, String title, String content, Instant readAt, Instant createdAt) {
    static View of(Notification n) {
      return new View(
          n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getReadAt(), n.getCreatedAt());
    }
  }
}
