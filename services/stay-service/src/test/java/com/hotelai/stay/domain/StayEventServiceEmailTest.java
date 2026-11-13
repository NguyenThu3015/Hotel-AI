package com.hotelai.stay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class StayEventServiceEmailTest {
    @Test
    void checkoutCreatesDomainAndEmailOutboxEvents() throws Exception {
        StayOutboxRepository outbox = mock(StayOutboxRepository.class);
        StayFolioEntryRepository folio = mock(StayFolioEntryRepository.class);
        when(folio.findByStayIdOrderByRecordedAtAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        StayEventService service = new StayEventService(outbox, folio, new ObjectMapper());
        Stay stay = mock(Stay.class);

        when(stay.getId()).thenReturn(UUID.randomUUID());
        when(stay.getCustomerId()).thenReturn(UUID.randomUUID());
        when(stay.getReservationReference()).thenReturn("HTL-STAY-001");
        when(stay.getContactName()).thenReturn("Test Guest");
        when(stay.getContactEmail()).thenReturn("guest@example.com");
        when(stay.getContactPhone()).thenReturn("0900000000");
        when(stay.getStatus()).thenReturn(StayStatus.CLOSED);
        when(stay.getCurrency()).thenReturn("VND");

        service.append(stay, "stay.checked-out.v1", List.of(), null);

        ArgumentCaptor<StayOutboxEvent> captor = ArgumentCaptor.forClass(StayOutboxEvent.class);
        verify(outbox, org.mockito.Mockito.times(2)).save(captor.capture());
        List<StayOutboxEvent> events = captor.getAllValues();
        assertThat(events).extracting(StayOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("stay.checked-out.v1", "notification.requested.v1");
        StayOutboxEvent notification = events.stream()
                .filter(event -> "notification.requested.v1".equals(event.getEventType()))
                .findFirst().orElseThrow();
        var payload = new ObjectMapper().readTree(notification.getPayload());
        assertThat(payload.path("channel").asText()).isEqualTo("EMAIL");
        assertThat(payload.path("templateCode").asText()).isEqualTo("CHECK_OUT_COMPLETED");
        assertThat(payload.path("recipient").asText()).isEqualTo("guest@example.com");
    }
}
