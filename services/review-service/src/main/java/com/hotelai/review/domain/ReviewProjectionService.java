package com.hotelai.review.domain;

import com.hotelai.review.domain.StayReviewProjection.StaySnapshot;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewProjectionService {
    private final StayReviewProjectionRepository stays;
    private final CustomerIdentityProjectionRepository identities;
    private final ProcessedEventRepository processed;

    public ReviewProjectionService(
            StayReviewProjectionRepository stays,
            CustomerIdentityProjectionRepository identities,
            ProcessedEventRepository processed) {
        this.stays = stays;
        this.identities = identities;
        this.processed = processed;
    }

    @Transactional
    public void stay(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            StaySnapshot snapshot) {
        if (processed.existsById(eventId)) return;
        StayReviewProjection projection = stays.findById(snapshot.stayId())
                .orElseGet(StayReviewProjection::new);
        projection.synchronize(snapshot, occurredAt);
        stays.save(projection);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void customer(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            CustomerSnapshot snapshot) {
        if (processed.existsById(eventId)) return;
        CustomerIdentityProjection projection =
                identities.findById(snapshot.customerId())
                        .orElseGet(CustomerIdentityProjection::new);
        projection.synchronize(
                snapshot.customerId(),
                snapshot.identityId(),
                snapshot.version(),
                occurredAt);
        identities.save(projection);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    public record CustomerSnapshot(
            UUID customerId,
            UUID identityId,
            String eventType,
            long version) {}
}
