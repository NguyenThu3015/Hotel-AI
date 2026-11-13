package com.hotelai.stay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.stay.domain.ProjectionService.ServiceUsageFinancialEvent;
import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionServiceTest {
    @Test
    void appliesPositiveAndNegativeServiceUsageDeltasToStayFolio() {
        ReservationStayProjectionRepository reservations =
                mock(ReservationStayProjectionRepository.class);
        RoomTypeProjectionRepository roomTypes = mock(RoomTypeProjectionRepository.class);
        RoomProjectionRepository rooms = mock(RoomProjectionRepository.class);
        ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        StayRepository stays = mock(StayRepository.class);
        RoomAssignmentRepository assignments = mock(RoomAssignmentRepository.class);
        StayFolioEntryRepository folio = mock(StayFolioEntryRepository.class);
        StayEventService events = mock(StayEventService.class);

        ReservationStayProjection reservation = new ReservationStayProjection(
                new ReservationSnapshot(
                        UUID.randomUUID(), "RSV-FOLIO", UUID.randomUUID(),
                        "Guest", "guest@example.com", "0900000000", "CONFIRMED", 1,
                        UUID.randomUUID(), LocalDate.of(2026, 7, 17),
                        LocalDate.of(2026, 7, 19), 1, 1, 0, "VND",
                        new BigDecimal("1000000"), new BigDecimal("300000"),
                        new BigDecimal("300000"), UUID.randomUUID()),
                Instant.now());
        Stay stay = Stay.pendingCheckIn(
                reservation, UUID.randomUUID(), "a".repeat(64), false);
        stay.open(Instant.now());
        when(processed.existsById(any())).thenReturn(false);
        when(stays.findLockedById(stay.getId())).thenReturn(Optional.of(stay));
        when(assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                stay.getId(), AssignmentStatus.ACTIVE)).thenReturn(List.of());

        ProjectionService service = new ProjectionService(
                reservations, roomTypes, rooms, processed, stays, assignments, folio, events);
        UUID usageId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        service.serviceUsage(
                UUID.randomUUID(), "hotel-service.usage-recorded.v1",
                "hotel-service-catalog",
                event(usageId, stay.getId(), serviceId, actor, "RECORDED", "110000"));
        assertThat(stay.getChargesTotal()).isEqualByComparingTo("1110000.00");

        service.serviceUsage(
                UUID.randomUUID(), "hotel-service.usage-cancelled.v1",
                "hotel-service-catalog",
                event(usageId, stay.getId(), serviceId, actor, "CANCELLED", "-110000"));
        assertThat(stay.getChargesTotal()).isEqualByComparingTo("1000000.00");
        verify(folio, org.mockito.Mockito.times(2)).save(any(StayFolioEntry.class));
        verify(events, org.mockito.Mockito.times(2)).append(
                stay, "stay.folio-updated.v1", List.of(), null);
    }

    private static ServiceUsageFinancialEvent event(
            UUID usageId,
            UUID stayId,
            UUID serviceId,
            UUID actor,
            String action,
            String delta) {
        return new ServiceUsageFinancialEvent(
                usageId, stayId, UUID.randomUUID(), serviceId, "BF", "Breakfast",
                "PERSON", BigDecimal.ONE, new BigDecimal("100000"),
                new BigDecimal("0.1000"), new BigDecimal("100000"),
                new BigDecimal("10000"), new BigDecimal("110000"),
                new BigDecimal(delta), "VND", Instant.now(), action, 1, action, actor);
    }
}
