package com.hotelai.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotelai.review.api.ReviewDtos.ReviewView;
import com.hotelai.review.api.ReviewDtos.ReviewViewScope;
import com.hotelai.review.domain.Review;
import com.hotelai.review.domain.ReviewApplicationService.ReviewDetails;
import com.hotelai.review.domain.ReviewResponse;
import com.hotelai.review.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReviewDtosTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicViewHidesBookingAndStaffIdentifiers() {
        UUID staffId = UUID.randomUUID();
        Review review = review();
        ReviewResponse response =
                new ReviewResponse(review.getId(), "Cảm ơn quý khách.", staffId, Instant.now());
        ReviewDetails details = new ReviewDetails(review, response, List.of(), List.of());

        ReviewView view = ReviewView.from(details, ReviewViewScope.PUBLIC, objectMapper);

        assertThat(view.stayId()).isNull();
        assertThat(view.reservationId()).isNull();
        assertThat(view.reservationReference()).isNull();
        assertThat(view.hotelResponse().respondedBy()).isNull();
    }

    @Test
    void ownerViewIncludesBookingButNotModerationOrStaffIdentifiers() {
        UUID staffId = UUID.randomUUID();
        Review review = review();
        ReviewResponse response =
                new ReviewResponse(review.getId(), "Cảm ơn quý khách.", staffId, Instant.now());
        ReviewDetails details = new ReviewDetails(review, response, List.of(), List.of());

        ReviewView view = ReviewView.from(details, ReviewViewScope.OWNER, objectMapper);

        assertThat(view.stayId()).isEqualTo(review.getStayId());
        assertThat(view.reservationReference()).isEqualTo("RSV-2026-001");
        assertThat(view.hotelResponse().respondedBy()).isNull();
        assertThat(view.moderationFlags()).isEmpty();
        assertThat(view.moderationHistory()).isEmpty();
    }

    @Test
    void administrativeViewIncludesAuditIdentifiers() {
        UUID staffId = UUID.randomUUID();
        Review review = review();
        ReviewResponse response =
                new ReviewResponse(review.getId(), "Cảm ơn quý khách.", staffId, Instant.now());
        ReviewDetails details = new ReviewDetails(review, response, List.of(), List.of());

        ReviewView view = ReviewView.from(details, ReviewViewScope.ADMIN, objectMapper);

        assertThat(view.stayId()).isEqualTo(review.getStayId());
        assertThat(view.hotelResponse().respondedBy()).isEqualTo(staffId);
    }

    private static Review review() {
        Review review = mock(Review.class);
        when(review.getId()).thenReturn(UUID.randomUUID());
        when(review.getStayId()).thenReturn(UUID.randomUUID());
        when(review.getReservationId()).thenReturn(UUID.randomUUID());
        when(review.getReservationReference()).thenReturn("RSV-2026-001");
        when(review.getAuthorDisplayName()).thenReturn("N. V. A.");
        when(review.getRating()).thenReturn(5);
        when(review.getComment()).thenReturn("Phòng sạch và nhân viên thân thiện.");
        when(review.getTopics()).thenReturn("ROOM,STAFF");
        when(review.getStatus()).thenReturn(ReviewStatus.PUBLISHED);
        when(review.isVerified()).thenReturn(true);
        when(review.getModerationFlags()).thenReturn("[]");
        when(review.getCreatedAt()).thenReturn(Instant.now());
        when(review.getPublishedAt()).thenReturn(Instant.now());
        return review;
    }
}
