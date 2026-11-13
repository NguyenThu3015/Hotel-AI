package com.hotelai.backend.audit;

import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditLogRepository repository;
  private final UserRepository users;
  private final HttpServletRequest request;

  public void record(String action, String type, Object id, String details) {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    User user =
        (a != null && a.isAuthenticated())
            ? users.findByEmailIgnoreCase(a.getName()).orElse(null)
            : null;
    repository.save(
        new AuditLog(
            user,
            action,
            type,
            id == null ? null : id.toString(),
            details,
            request.getRemoteAddr()));
  }
}
