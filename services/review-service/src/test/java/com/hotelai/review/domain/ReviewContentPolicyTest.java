package com.hotelai.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotelai.review.config.ReviewProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReviewContentPolicyTest {
    private ReviewRepository reviews;
    private ReviewContentPolicy policy;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        reviews = mock(ReviewRepository.class);
        ReviewProperties properties = new ReviewProperties(
                10,
                5000,
                Duration.ofHours(24),
                List.of("từ cấm"),
                null);
        policy = new ReviewContentPolicy(
                reviews, properties, new ObjectMapper());
        customerId = UUID.randomUUID();
    }

    @Test
    void cleanContentIsPublishedAutomatically() {
        ReviewContentPolicy.Assessment result =
                policy.assess(customerId, "Phòng rất sạch và nhân viên thân thiện.");

        assertThat(result.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(result.flags()).isEmpty();
        assertThat(result.commentHash()).hasSize(64);
    }

    @Test
    void piiIsRedactedBeforePersistenceAndRequiresModeration() {
        ReviewContentPolicy.Assessment result = policy.assess(
                customerId,
                "Liên hệ tôi tại guest@example.com hoặc 0901234567 để trao đổi.");

        assertThat(result.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(result.sanitizedComment())
                .contains("[REDACTED_EMAIL]", "[REDACTED_PHONE]")
                .doesNotContain("guest@example.com", "0901234567");
        assertThat(result.flags()).contains("PII_EMAIL", "PII_PHONE");
    }

    @Test
    void duplicateAndProhibitedContentAreHeldForModeration() {
        when(reviews.existsByCustomerIdAndCommentHashAndCreatedAtAfter(
                eq(customerId), anyString(), any()))
                .thenReturn(true);

        ReviewContentPolicy.Assessment result =
                policy.assess(customerId, "Đây là nội dung có TỪ CẤM cần kiểm tra.");

        assertThat(result.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(result.flags())
                .contains("PROHIBITED_TERM", "DUPLICATE_CONTENT");
    }
}
