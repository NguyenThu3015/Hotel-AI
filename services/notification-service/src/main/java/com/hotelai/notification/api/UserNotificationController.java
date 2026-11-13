package com.hotelai.notification.api;

import com.hotelai.notification.domain.NotificationApplicationService;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {
    private final NotificationApplicationService service;

    public UserNotificationController(NotificationApplicationService service) { this.service = service; }

    @GetMapping
    Page<NotificationDtos.JobResponse> inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.userInbox(identityId(jwt), PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @PatchMapping("/{jobId}/read")
    NotificationDtos.JobResponse read(
            @PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        return service.markRead(jobId, identityId(jwt));
    }

    @GetMapping("/preferences")
    java.util.List<NotificationDtos.PreferenceResponse> preferences(
            @AuthenticationPrincipal Jwt jwt) {
        return service.preferences(identityId(jwt));
    }

    @PutMapping("/preferences")
    NotificationDtos.PreferenceResponse preference(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationDtos.PreferenceRequest request) {
        return service.updatePreference(identityId(jwt), request);
    }

    private static UUID identityId(Jwt jwt) {
        String value = jwt.getClaimAsString("uid");
        return UUID.fromString(value == null ? jwt.getSubject() : value);
    }
}
