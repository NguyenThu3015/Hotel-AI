package com.hotelai.review.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface StayReviewProjectionRepository
        extends JpaRepository<StayReviewProjection, UUID> {
    List<StayReviewProjection> findByStatusAndCustomerIdInOrderByActualCheckOutDesc(
            String status, Collection<UUID> customerIds);
}

interface CustomerIdentityProjectionRepository
        extends JpaRepository<CustomerIdentityProjection, UUID> {
    Optional<CustomerIdentityProjection> findByIdentityId(UUID identityId);
}

interface ReviewRepository
        extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {
    boolean existsByStayId(UUID stayId);
    Optional<Review> findByStayId(UUID stayId);
    Optional<Review> findByIdempotencyKey(String key);
    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);
    Page<Review> findByCustomerIdInOrderByCreatedAtDesc(
            Collection<UUID> customerIds, Pageable pageable);
    boolean existsByCustomerIdAndCommentHashAndCreatedAtAfter(
            UUID customerId, String commentHash, Instant threshold);
    long countByStatus(ReviewStatus status);
    long countByStatusAndRating(ReviewStatus status, int rating);
}

interface ReviewResponseRepository extends JpaRepository<ReviewResponse, UUID> {
    Optional<ReviewResponse> findByReviewId(UUID reviewId);
}

interface ReviewResponseRevisionRepository
        extends JpaRepository<ReviewResponseRevision, UUID> {
    Optional<ReviewResponseRevision> findByIdempotencyKey(String key);
    List<ReviewResponseRevision> findByReviewIdOrderByRevisionNumberAsc(UUID reviewId);
}

interface ReviewModerationDecisionRepository
        extends JpaRepository<ReviewModerationDecision, UUID> {
    Optional<ReviewModerationDecision> findByIdempotencyKey(String key);
    List<ReviewModerationDecision> findByReviewIdOrderByDecidedAtAsc(UUID reviewId);
}

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
