package com.hotelai.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.reservation.api.ReservationDtos;
import com.hotelai.reservation.common.ApiException;
import com.hotelai.reservation.config.ReservationProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ReservationApplicationServiceTest {
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationChangeRequestRepository changes = mock(ReservationChangeRequestRepository.class);
    private final IdempotencyRecordRepository idempotency = mock(IdempotencyRecordRepository.class);
    private final InventoryGateway inventory = mock(InventoryGateway.class);
    private final PricingGateway pricing = mock(PricingGateway.class);
    private final ReservationEventService events = mock(ReservationEventService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReservationApplicationService service;

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties(
                new ReservationProperties.Security("internal"),
                new ReservationProperties.Clients("http://inventory", "http://pricing", Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new ReservationProperties.Reservation(Duration.ofMinutes(15), 60, Duration.ofHours(24)),
                new ReservationProperties.Messaging(
                        "hotel.events", "reservation.payment", "payment.*.v1",
                        "reservation.stay", List.of(
                                "stay.checked-in.v1", "stay.checked-out.v1", "stay.no-show.v1"),
                        1000, 30000));
        service = new ReservationApplicationService(
                reservations, changes, idempotency, inventory, pricing, events, objectMapper, properties);
        when(reservations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(changes.save(any())).thenAnswer(call -> call.getArgument(0));
        when(idempotency.save(any())).thenAnswer(call -> call.getArgument(0));
        when(inventory.acquire(any(), anyString()))
                .thenReturn(new InventoryHold(UUID.randomUUID(), Instant.now().plusSeconds(900), 0));
    }

    @Test
    void onlineCreateSnapshotsQuoteAndCreatesInventoryHold() {
        when(pricing.quote(any())).thenReturn(price("1000000", "300000", false));
        UUID customer = UUID.randomUUID();

        Reservation result = service.createForCustomer(customer, createRequest(), "create-key-0001");

        assertThat(result.getCustomerId()).isEqualTo(customer);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(result.getPricingVersion()).isEqualTo("price-v1");
        assertThat(result.getGuests()).hasSize(1);
        verify(pricing).quote(any());
        verify(inventory).acquire(any(), anyString());
        verify(events).append(result, "reservation.created.v1", false, java.util.Map.of("channel", "ONLINE"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSecondHold() {
        when(pricing.quote(any())).thenReturn(price("1000000", "300000", false));
        UUID customer = UUID.randomUUID();
        ReservationDtos.CreateRequest request = createRequest();
        Reservation first = service.createForCustomer(customer, request, "create-key-0002");
        ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotency).save(record.capture());
        when(idempotency.findByKeyAndScopeAndActorId("create-key-0002", "CREATE", customer))
                .thenReturn(Optional.of(record.getValue()));
        when(reservations.findById(first.getId())).thenReturn(Optional.of(first));

        Reservation replay = service.createForCustomer(customer, request, "create-key-0002");

        assertThat(replay).isSameAs(first);
        verify(inventory, times(1)).acquire(any(), anyString());
        verify(pricing, times(1)).quote(any());
    }

    @Test
    void confirmedCustomerChangeCreatesApprovalRequestWithoutCallingDependencies() {
        when(pricing.quote(any())).thenReturn(price("1000000", "0", false));
        UUID customer = UUID.randomUUID();
        Reservation existing = service.createForCustomer(customer, createRequest(), "create-key-0003");
        assertThat(existing.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        when(reservations.findById(existing.getId())).thenReturn(Optional.of(existing));

        ReservationApplicationService.ChangeOutcome outcome = service.changeForCustomer(
                customer, existing.getId(), changeRequest(existing.getVersion()), "change-key-0003");

        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.changeRequest()).isNotNull();
        verify(changes).save(any(ReservationChangeRequest.class));
        verify(inventory, times(1)).acquire(any(), anyString());
        verify(pricing, times(1)).quote(any());
    }

    @Test
    void pendingPaymentCustomerCanChangeDirectlyBeforeCutoff() {
        when(pricing.quote(any())).thenReturn(price("1000000", "300000", false));
        UUID customer = UUID.randomUUID();
        Reservation existing = service.createForCustomer(customer, createRequest(), "create-key-0004");
        when(reservations.findById(existing.getId())).thenReturn(Optional.of(existing));

        ReservationApplicationService.ChangeOutcome outcome = service.changeForCustomer(
                customer, existing.getId(), changeRequest(existing.getVersion()), "change-key-0004");

        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.reservation().getRoomCount()).isEqualTo(2);
        verify(inventory, times(2)).acquire(any(), anyString());
        verify(inventory).release(any(), anyString());
        verify(changes, never()).save(any());
    }

    @Test
    void customerCannotReadAnotherCustomersReservation() {
        when(pricing.quote(any())).thenReturn(price("1000000", "0", false));
        UUID owner = UUID.randomUUID();
        Reservation existing = service.createForCustomer(owner, createRequest(), "create-key-0005");
        when(reservations.findDetailedById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getForCustomer(UUID.randomUUID(), existing.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Reservation not found");
    }

    @Test
    void failedReservationPersistenceCompensatesInventoryAndPromotionQuota() {
        when(pricing.quote(any())).thenReturn(price("1000000", "300000", false));
        when(reservations.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        UUID customer = UUID.randomUUID();

        assertThatThrownBy(() -> service.createForCustomer(
                        customer, createRequest(), "create-key-compensation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(pricing).synchronizePromotion(
                any(UUID.class), any(UUID.class), any(UUID.class), org.mockito.ArgumentMatchers.eq(false), anyString());
        verify(pricing).releasePromotion(any(UUID.class), anyString());
        verify(inventory).release(any(UUID.class), org.mockito.ArgumentMatchers.eq("reservation-create-failed"));
    }

    private static ReservationDtos.CreateRequest createRequest() {
        return new ReservationDtos.CreateRequest(
                UUID.randomUUID(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                1, 2, 0, "Nguyen An", "an@example.com", "0900000000", null, null,
                List.of(new ReservationDtos.GuestRequest("Nguyen An", null, null, "VN", true)));
    }

    private static ReservationDtos.ChangeRequest changeRequest(long version) {
        return new ReservationDtos.ChangeRequest(
                UUID.randomUUID(), LocalDate.now().plusDays(6), LocalDate.now().plusDays(9),
                2, 3, 1, "Late arrival", null, version);
    }

    private static PricingSnapshot price(String total, String deposit, boolean manual) {
        return new PricingSnapshot(
                UUID.randomUUID(), "price-v1", "VND", new BigDecimal(total), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(total), new BigDecimal(deposit), manual,
                Instant.now().plusSeconds(300),
                "{\"nights\":2}", "{\"code\":\"FLEX\"}", Instant.now().plusSeconds(3600), BigDecimal.ZERO);
    }
}
