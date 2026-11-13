package com.hotelai.notification.api;

import com.hotelai.notification.domain.NotificationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {
    private final NotificationApplicationService service;

    public InternalNotificationController(NotificationApplicationService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    NotificationDtos.JobResponse enqueue(@Valid @RequestBody NotificationDtos.EnqueueRequest request) {
        return service.enqueue(request);
    }
}
