package com.hotelai.review.api;

import com.hotelai.review.api.ReviewDtos.CreateReviewRequest;
import com.hotelai.review.api.ReviewDtos.EligibleStayView;
import com.hotelai.review.api.ReviewDtos.ReviewSummaryView;
import com.hotelai.review.api.ReviewDtos.ReviewView;
import com.hotelai.review.api.ReviewDtos.ReviewViewScope;
import com.hotelai.review.domain.ReviewApplicationService;
import com.hotelai.review.domain.ReviewApplicationService.CreateCommand;
import com.hotelai.review.domain.ReviewTopic;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewApplicationService application;
    private final ObjectMapper objectMapper;

    public ReviewController(
            ReviewApplicationService application, ObjectMapper objectMapper) {
        this.application = application;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/review-stays")
    List<EligibleStayView> eligible(@AuthenticationPrincipal Jwt jwt) {
        return application.eligibleStays(actor(jwt)).stream()
                .map(EligibleStayView::from)
                .toList();
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewView create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateReviewRequest request) {
        return ReviewView.from(application.create(
                actor(jwt),
                idempotencyKey,
                new CreateCommand(
                        request.stayId(),
                        request.rating(),
                        request.topics(),
                        request.comment())), ReviewViewScope.OWNER, objectMapper);
    }

    @GetMapping("/reviews/mine")
    Page<ReviewView> mine(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable) {
        return application.mine(actor(jwt), pageable)
                .map(value -> ReviewView.from(
                        value, ReviewViewScope.OWNER, objectMapper));
    }

    @GetMapping("/reviews")
    Page<ReviewView> published(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) ReviewTopic topic,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable) {
        return application.publicReviews(rating, topic, pageable)
                .map(value -> ReviewView.from(
                        value, ReviewViewScope.PUBLIC, objectMapper));
    }

    @GetMapping("/reviews/summary")
    ReviewSummaryView summary() {
        return ReviewSummaryView.from(application.summary());
    }

    @GetMapping("/reviews/{reviewId}")
    ReviewView detail(@PathVariable UUID reviewId) {
        return ReviewView.from(
                application.publicDetail(reviewId),
                ReviewViewScope.PUBLIC,
                objectMapper);
    }

    static UUID actor(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        return UUID.fromString(uid);
    }
}
