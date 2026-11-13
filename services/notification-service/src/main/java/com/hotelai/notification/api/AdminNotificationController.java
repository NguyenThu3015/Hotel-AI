package com.hotelai.notification.api;

import com.hotelai.notification.domain.NotificationApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
    private final NotificationApplicationService service;

    public AdminNotificationController(NotificationApplicationService service) { this.service = service; }

    @GetMapping("/templates")
    Page<NotificationDtos.TemplateResponse> templates(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.templates(PageRequest.of(page, bounded(size)));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    NotificationDtos.TemplateResponse upsert(@Valid @RequestBody NotificationDtos.TemplateRequest request) {
        return service.upsertTemplate(request);
    }

    @GetMapping("/jobs")
    Page<NotificationDtos.JobResponse> jobs(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.jobs(PageRequest.of(page, bounded(size)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    NotificationDtos.JobResponse retry(@PathVariable UUID jobId) { return service.retry(jobId); }

    private static int bounded(int size) { return Math.min(Math.max(size, 1), 100); }
}
