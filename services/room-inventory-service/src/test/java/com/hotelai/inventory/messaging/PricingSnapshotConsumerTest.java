package com.hotelai.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hotelai.inventory.domain.ProcessedEvent;
import com.hotelai.inventory.domain.RoomType;
import com.hotelai.inventory.domain.RoomTypePriceSnapshot;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PricingSnapshotConsumerTest {
    @Mock PricingRoomTypeRepository roomTypes;
    @Mock PricingSnapshotRepository prices;
    @Mock PricingProcessedEventRepository processed;

    private PricingSnapshotConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PricingSnapshotConsumer(new ObjectMapper(), roomTypes, prices, processed);
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutSecondSideEffect() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(processed.existsById(eventId)).thenReturn(true);

        consumer.consume(envelope(eventId, UUID.randomUUID()));

        verifyNoInteractions(roomTypes, prices);
        verify(processed, never()).save(any());
    }

    @Test
    void priceEventUpdatesReadModelAndRecordsInbox() throws Exception {
        UUID eventId = UUID.randomUUID();
        RoomType type = new RoomType(
                "DELUXE", "Deluxe", "Phòng Deluxe", 2, 1, 3,
                BigDecimal.valueOf(28), null, true, Set.of());
        when(processed.existsById(eventId)).thenReturn(false);
        when(roomTypes.findById(type.getId())).thenReturn(Optional.of(type));
        when(prices.findById(type.getId())).thenReturn(Optional.empty());

        consumer.consume(envelope(eventId, type.getId()));

        ArgumentCaptor<RoomTypePriceSnapshot> snapshot =
                ArgumentCaptor.forClass(RoomTypePriceSnapshot.class);
        verify(prices).save(snapshot.capture());
        assertThat(snapshot.getValue().getNightlyPrice()).isEqualByComparingTo("1250000");
        assertThat(snapshot.getValue().getCurrency()).isEqualTo("VND");
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void stalePolicyEventCannotOverwriteNewerSnapshot() throws Exception {
        UUID roomTypeId = UUID.randomUUID();
        RoomType type = new RoomType(
                "DELUXE", "Deluxe", "Phòng Deluxe", 2, 1, 3,
                BigDecimal.valueOf(28), null, true, Set.of());
        type.updatePolicySummary("Chính sách mới", 9);
        when(processed.existsById(any(UUID.class))).thenReturn(false);
        when(roomTypes.findById(roomTypeId)).thenReturn(Optional.of(type));

        consumer.consume(policyEnvelope(UUID.randomUUID(), roomTypeId, "Chính sách cũ", 8));

        assertThat(type.getPolicySummary()).isEqualTo("Chính sách mới");
        assertThat(type.getPolicySnapshotVersion()).isEqualTo(9);
        verify(processed).save(any(ProcessedEvent.class));
    }

    private static String envelope(UUID eventId, UUID roomTypeId) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"pricing.room-type-price-published.v1",
                  "producer":"pricing-service",
                  "correlationId":"%s",
                  "aggregateType":"RoomTypePrice",
                  "aggregateId":"%s",
                  "schemaVersion":1,
                  "payload":{
                    "roomTypeId":"%s",
                    "nightlyPrice":1250000,
                    "currency":"VND",
                    "taxRate":0.10,
                    "serviceFee":100000,
                    "pricingVersion":7
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), roomTypeId, roomTypeId);
    }

    private static String policyEnvelope(
            UUID eventId, UUID roomTypeId, String summary, long policyVersion) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"pricing.room-type-policy-published.v1",
                  "producer":"pricing-service",
                  "correlationId":"%s",
                  "aggregateType":"HotelPolicy",
                  "aggregateId":"%s",
                  "schemaVersion":1,
                  "payload":{
                    "roomTypeId":"%s",
                    "summary":"%s",
                    "policyVersion":%d
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), roomTypeId, roomTypeId, summary, policyVersion);
    }
}
