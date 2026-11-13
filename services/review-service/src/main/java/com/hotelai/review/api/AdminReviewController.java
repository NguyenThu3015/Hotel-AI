package com.hotelai.review.api;

import com.hotelai.review.api.ReviewDtos.ModerationRequest;
import com.hotelai.review.api.ReviewDtos.OfficialResponseRequest;
import com.hotelai.review.api.ReviewDtos.ReviewView;
import com.hotelai.review.api.ReviewDtos.ReviewViewScope;
import com.hotelai.review.domain.ReviewApplicationService;
import com.hotelai.review.domain.ReviewApplicationService.ModerationCommand;
import com.hotelai.review.domain.ReviewStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {
    private final ReviewApplicationService application;
    private final ObjectMapper objectMapper;

    public AdminReviewController(
            ReviewApplicationService application, ObjectMapper objectMapper) {
        this.application = application;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    Page<ReviewView> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String search,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable) {
        return application.adminReviews(status, rating, search, pageable)
                .map(value -> ReviewView.from(
                        value, ReviewViewScope.ADMIN, objectMapper));
    }

    @PatchMapping("/{reviewId}/moderation")
    ReviewView moderate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ModerationRequest request) {
        return ReviewView.from(application.moderate(
                ReviewController.actor(jwt),
                reviewId,
                idempotencyKey,
                new ModerationCommand(request.action(), request.reason())),
                ReviewViewScope.ADMIN,
                objectMapper);
    }

    @PutMapping("/{reviewId}/response")
    ReviewView respond(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OfficialResponseRequest request) {
        return ReviewView.from(application.upsertResponse(
                ReviewController.actor(jwt),
                reviewId,
                idempotencyKey,
                request.content()), ReviewViewScope.ADMIN, objectMapper);
    }
}
