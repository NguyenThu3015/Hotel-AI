package com.hotelai.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.review.domain.StayReviewProjection.StaySnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:review-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewPersistenceTest {
    @Autowired StayReviewProjectionRepository stays;
    @Autowired CustomerIdentityProjectionRepository identities;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewResponseRepository responses;
    @Autowired ReviewResponseRevisionRepository revisions;
    @Autowired ReviewModerationDecisionRepository decisions;
    @Autowired ReviewOutboxRepository outbox;
    @Autowired EntityManager entityManager;

    @Test
    void persistsVerifiedReviewModerationAndResponseHistory() {
        UUID customerId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T08:00:00Z");

        CustomerIdentityProjection identity = new CustomerIdentityProjection();
        identity.synchronize(customerId, identityId, 1, now);
        identities.save(identity);

        StayReviewProjection stay = new StayReviewProjection();
        stay.synchronize(
                new StaySnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), customerId,
                        "BK-PERSIST", "Nguyen Van A", "guest@example.com",
                        "0900000000", "CLOSED",
                        LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17),
                        now.minusSeconds(86400), now,
                        List.of(UUID.randomUUID()), List.of("101"), "VND",
                        new BigDecimal("1000000"), new BigDecimal("1000000"),
                        BigDecimal.ZERO, List.of()),
                now);
        stays.save(stay);

        Review review = new Review(
                stay, 4, "Phòng sạch và dịch vụ tốt.", "a".repeat(64),
                "CLEANLINESS,SERVICE", "[\"PROHIBITED_TERM\"]",
                ReviewStatus.PENDING_MODERATION, "review-persist-001",
                "b".repeat(64));
        review.moderate(ModerationAction.PUBLISH, now.plusSeconds(10));
        reviews.save(review);
        decisions.save(new ReviewModerationDecision(
                review.getId(), ModerationAction.PUBLISH, "Nội dung hợp lệ",
                actorId, "moderate-persist-001", "c".repeat(64),
                now.plusSeconds(10)));

        ReviewResponse response = responses.save(new ReviewResponse(
                review.getId(), "Cảm ơn quý khách.", actorId,
                now.plusSeconds(20)));
        revisions.save(new ReviewResponseRevision(
                response, "response-persist-001", "d".repeat(64), actorId));
        response.revise(
                "Cảm ơn quý khách, chúng tôi đã ghi nhận góp ý.",
                actorId, now.plusSeconds(30));
        revisions.save(new ReviewResponseRevision(
                response, "response-persist-002", "e".repeat(64), actorId));
        outbox.save(new ReviewOutboxEvent(
                "Review", review.getId(), "review.published.v1", "{}"));

        entityManager.flush();
        entityManager.clear();

        Review restored = reviews.findByStayId(stay.getStayId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(restored.isVerified()).isTrue();
        assertThat(responses.findByReviewId(review.getId()))
                .get()
                .extracting(ReviewResponse::getRevisionNumber)
                .isEqualTo(2);
        assertThat(revisions.findByReviewIdOrderByRevisionNumberAsc(review.getId()))
                .extracting(ReviewResponseRevision::getRevisionNumber)
                .containsExactly(1, 2);
        assertThat(decisions.findByReviewIdOrderByDecidedAtAsc(review.getId()))
                .singleElement()
                .extracting(ReviewModerationDecision::getAction)
                .isEqualTo(ModerationAction.PUBLISH);
        assertThat(outbox.findAll()).hasSize(1);
    }
}
