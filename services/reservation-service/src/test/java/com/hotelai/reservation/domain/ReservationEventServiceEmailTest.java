package com.hotelai.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ReservationEventServiceEmailTest {
    @Test
    void confirmedReservationCreatesDomainAndEmailOutboxEvents() throws Exception {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        InboxEventRepository inbox = mock(InboxEventRepository.class);
        ReservationEventService service = new ReservationEventService(outbox, inbox, new ObjectMapper());
        Reservation reservation = mock(Reservation.class);
        UUID reservationId = UUID.randomUUID();

        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getReference()).thenReturn("HTL-EMAIL-001");
        when(reservation.getCustomerId()).thenReturn(UUID.randomUUID());
        when(reservation.getChannel()).thenReturn(ReservationChannel.ONLINE);
        when(reservation.getContactName()).thenReturn("Test Guest");
        when(reservation.getContactEmail()).thenReturn("guest@example.com");
        when(reservation.getContactPhone()).thenReturn("0900000000");
        when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getCurrency()).thenReturn("VND");
        when(reservation.getPriceBreakdown()).thenReturn("{}");
        when(reservation.getCancellationPolicy()).thenReturn("{}");

        service.append(reservation, "reservation.confirmed.v1", false,
                Map.of("confirmationMode", "PAYMENT"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, org.mockito.Mockito.times(2)).save(captor.capture());
        List<OutboxEvent> events = captor.getAllValues();
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("reservation.confirmed.v1", "notification.requested.v1");
        OutboxEvent notification = events.stream()
                .filter(event -> "notification.requested.v1".equals(event.getEventType()))
                .findFirst().orElseThrow();
        var payload = new ObjectMapper().readTree(notification.getPayload());
        assertThat(payload.path("channel").asText()).isEqualTo("EMAIL");
        assertThat(payload.path("templateCode").asText()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(payload.path("recipient").asText()).isEqualTo("guest@example.com");
    }
}
