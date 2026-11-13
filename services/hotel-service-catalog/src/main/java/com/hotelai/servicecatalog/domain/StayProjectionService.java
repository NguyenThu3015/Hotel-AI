package com.hotelai.servicecatalog.domain;

import com.hotelai.servicecatalog.domain.StayProjection.StaySnapshot;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StayProjectionService {
    private final StayProjectionRepository stays;
    private final ProcessedEventRepository processed;

    public StayProjectionService(
            StayProjectionRepository stays, ProcessedEventRepository processed) {
        this.stays = stays;
        this.processed = processed;
    }

    @Transactional
    public void synchronize(
            UUID eventId,
            String eventType,
            String producer,
            Instant occurredAt,
            StaySnapshot snapshot) {
        if (processed.existsById(eventId)) return;
        StayProjection projection =
                stays.findById(snapshot.stayId()).orElseGet(StayProjection::new);
        projection.synchronize(snapshot, occurredAt);
        stays.save(projection);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }
}
