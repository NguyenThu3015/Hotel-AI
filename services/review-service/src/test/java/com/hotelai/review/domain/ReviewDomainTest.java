package com.hotelai.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.review.domain.StayReviewProjection.StaySnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewDomainTest {

    @Test
    void storesAiTriageWithModelLineage() {
        Review review = review(closedStay(), ReviewStatus.PUBLISHED);

        review.applyAiAnalysis(
                "NEGATIVE",
                new BigDecimal("-0.82"),
                "[\"CLEANLINESS\",\"ROOM\"]",
                "HIGH",
                new BigDecimal("0.91"),
                "Khách sạn thành thật xin lỗi về trải nghiệm này.",
                "gemini-review-v1",
                "gemini");

        assertThat(review.getAiSentiment()).isEqualTo("NEGATIVE");
        assertThat(review.getAiUrgency()).isEqualTo("HIGH");
        assertThat(review.getAiTopics()).contains("CLEANLINESS");
        assertThat(review.getAiModelVersion()).isEqualTo("gemini-review-v1");
        assertThat(review.getAiAnalyzedAt()).isNotNull();
    }

    @Test
    void createsVerifiedReviewForCompletedStay() {
        Review review = review(closedStay(), ReviewStatus.PUBLISHED);

        assertThat(review.isVerified()).isTrue();
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(review.getPublishedAt()).isNotNull();
        assertThat(review.getAuthorDisplayName()).isEqualTo("N. V. A.");
    }

    @Test
    void rejectsReviewBeforeCheckout() {
        StayReviewProjection stay = stay("IN_HOUSE");

        assertThatThrownBy(() -> review(stay, ReviewStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed stay");
    }

    @Test
    void moderationChangesPublicationStateButKeepsReviewContent() {
        Review review = review(closedStay(), ReviewStatus.PENDING_MODERATION);
        String original = review.getComment();

        review.moderate(ModerationAction.PUBLISH, Instant.now());
        review.moderate(ModerationAction.HIDE, Instant.now());

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(review.getComment()).isEqualTo(original);
    }

    @Test
    void officialResponseMaintainsRevisionNumber() {
        UUID actor = UUID.randomUUID();
        ReviewResponse response =
                new ReviewResponse(UUID.randomUUID(), "Cảm ơn quý khách.", actor, Instant.now());

        response.revise(
                "Cảm ơn quý khách, chúng tôi đã ghi nhận góp ý.",
                actor,
                Instant.now());

        assertThat(response.getRevisionNumber()).isEqualTo(2);
        assertThat(response.getContent()).contains("ghi nhận góp ý");
    }

    private static Review review(
            StayReviewProjection stay, ReviewStatus status) {
        return new Review(
                stay,
                5,
                "Phòng sạch và nhân viên thân thiện.",
                "a".repeat(64),
                "CLEANLINESS,STAFF",
                "[]",
                status,
                "review-domain-001",
                "b".repeat(64));
    }

    private static StayReviewProjection closedStay() {
        return stay("CLOSED");
    }

    private static StayReviewProjection stay(String status) {
        StayReviewProjection value = new StayReviewProjection();
        value.synchronize(
                new StaySnapshot(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BK-2026-0001",
                        "Nguyen Van A",
                        "guest@example.com",
                        "0900000000",
                        status,
                        LocalDate.of(2026, 7, 16),
                        LocalDate.of(2026, 7, 17),
                        Instant.parse("2026-07-16T07:00:00Z"),
                        "CLOSED".equals(status)
                                ? Instant.parse("2026-07-17T05:00:00Z") : null,
                        List.of(UUID.randomUUID()),
                        List.of("101"),
                        "VND",
                        new BigDecimal("1000000"),
                        new BigDecimal("1000000"),
                        BigDecimal.ZERO,
                        List.of()),
                Instant.parse("2026-07-17T05:00:00Z"));
        return value;
    }
}
