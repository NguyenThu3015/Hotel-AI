package com.hotelai.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.config.PaymentProperties;
import com.hotelai.payment.provider.PaymentProvider;
import com.hotelai.payment.provider.ProviderRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentApplicationServiceTest {
    private final PaymentIntentRepository paymentRepository = mock(PaymentIntentRepository.class);
    private final ReservationPaymentProjectionRepository reservationRepository =
            mock(ReservationPaymentProjectionRepository.class);
    private final RefundRequestRepository refundRepository = mock(RefundRequestRepository.class);
    private final PaymentIdempotencyRepository idempotencyRepository =
            mock(PaymentIdempotencyRepository.class);
    private final ProviderRegistry providers = mock(ProviderRegistry.class);
    private final PaymentEventService events = mock(PaymentEventService.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PaymentProperties properties = new PaymentProperties(
                new PaymentProperties.Security("internal"),
                new PaymentProperties.Payment(Duration.ofMinutes(15), Duration.ofHours(24)),
                new PaymentProperties.Messaging(
                        "hotel.events",
                        "payment.reservations",
                        "reservation.*.v1",
                        "payment.stays",
                        "stay.*.v1",
                        "hotel.events.dlx",
                        "payment.dlq",
                        "payment.dead",
                        1000),
                new PaymentProperties.Providers(
                        new PaymentProperties.Momo(
                                false,
                                URI.create("https://momo.example/create"),
                                URI.create("https://momo.example/query"),
                                URI.create("https://momo.example/refund"),
                                URI.create("https://momo.example/refund/query"),
                                "",
                                "",
                                "",
                                URI.create("http://localhost/result"),
                                URI.create("http://localhost/ipn")),
                        new PaymentProperties.Casso(
                                false,
                                "",
                                "970422",
                                "1234567890",
                                "HOTEL AI",
                                URI.create("https://img.vietqr.io/image"),
                                "compact2",
                                "HOTELAI",
                                "Asia/Ho_Chi_Minh"),
                        new PaymentProperties.Sandbox(
                                true, URI.create("http://localhost/sandbox"))));
        service = new PaymentApplicationService(
                paymentRepository,
                reservationRepository,
                refundRepository,
                idempotencyRepository,
                providers,
                new ReservationSnapshotCodec(objectMapper),
                events,
                objectMapper,
                properties);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.sumSucceeded(any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void onlineDepositCreatesPendingProviderCheckoutFromReservationProjection() {
        UUID customerId = UUID.randomUUID();
        ReservationPaymentProjection reservation = reservation(customerId);
        when(reservationRepository.findById(reservation.getReservationId()))
                .thenReturn(Optional.of(reservation));
        when(providers.required(PaymentProviderCode.SANDBOX)).thenReturn(provider);
        when(provider.createCheckout(any(), anyString()))
                .thenReturn(new PaymentProvider.CheckoutSession(
                        "request-1", "signed-extra", "https://sandbox.example/pay"));

        PaymentIntent result = service.createIntent(
                customerId,
                false,
                new PaymentCommands.CreateIntent(
                        reservation.getReservationId(),
                        PaymentPurpose.DEPOSIT,
                        PaymentProviderCode.SANDBOX,
                        new BigDecimal("300000"),
                        "VND"),
                "payment-create-0001");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getCheckoutUrl()).isEqualTo("https://sandbox.example/pay");
        verify(paymentRepository).lockReservation(reservation.getReservationId());
        verify(events).payment(result, "payment.intent-created.v1");
    }

    @Test
    void unknownMomoInitiationResultRemainsPendingForReconciliation() {
        UUID customerId = UUID.randomUUID();
        ReservationPaymentProjection reservation = reservation(customerId);
        when(reservationRepository.findById(reservation.getReservationId()))
                .thenReturn(Optional.of(reservation));
        when(providers.required(PaymentProviderCode.MOMO)).thenReturn(provider);
        when(provider.createCheckout(any(), anyString()))
                .thenThrow(new PaymentProvider.UncertainProviderResult(
                        "MoMo request timed out", "momo-request-1", "network timeout", null));

        PaymentIntent result = service.createIntent(
                customerId,
                false,
                new PaymentCommands.CreateIntent(
                        reservation.getReservationId(),
                        PaymentPurpose.DEPOSIT,
                        PaymentProviderCode.MOMO,
                        new BigDecimal("300000"),
                        "VND"),
                "payment-create-momo-unknown");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getProviderRequestId()).isEqualTo("momo-request-1");
        assertThat(result.getFailureCode()).isEqualTo("PROVIDER_RESULT_UNKNOWN");
        verify(events, never()).payment(result, "payment.failed.v1");
    }

    @Test
    void reconciliationCompletesUnknownMomoPaymentExactlyOnce() {
        UUID customerId = UUID.randomUUID();
        ReservationPaymentProjection reservation = reservation(customerId);
        ReservationSnapshotCodec codec = new ReservationSnapshotCodec(new ObjectMapper());
        PaymentIntent payment = PaymentIntent.create(
                reservation.getReservationId(),
                customerId,
                customerId,
                PaymentPurpose.DEPOSIT,
                PaymentProviderCode.MOMO,
                new BigDecimal("300000"),
                "VND",
                reservation,
                codec.hash(reservation),
                Instant.now().plusSeconds(900));
        String extraData = codec.encode(payment);
        payment.markProviderResultUnknown("momo-request-2", extraData, "network timeout");
        when(paymentRepository.findByStatusAndProviderOrderByUpdatedAtAsc(
                        any(), any(), any()))
                .thenReturn(List.of(payment));
        when(providers.required(PaymentProviderCode.MOMO)).thenReturn(provider);
        when(provider.queryPayment(payment))
                .thenReturn(new PaymentProvider.PaymentInquiry(
                        true,
                        true,
                        payment.getAmount(),
                        "VND",
                        extraData,
                        "99887766",
                        Instant.now(),
                        null,
                        "{\"resultCode\":0}"));

        service.reconcilePendingMomoPayments(50);
        service.reconcilePendingMomoPayments(50);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("99887766");
        verify(events).payment(payment, "payment.succeeded.v1");
    }

    @Test
    void customerCannotCreatePaymentForAnotherCustomersReservation() {
        ReservationPaymentProjection reservation = reservation(UUID.randomUUID());
        when(reservationRepository.findById(reservation.getReservationId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.createIntent(
                        UUID.randomUUID(),
                        false,
                        new PaymentCommands.CreateIntent(
                                reservation.getReservationId(),
                                PaymentPurpose.DEPOSIT,
                                PaymentProviderCode.SANDBOX,
                                new BigDecimal("300000"),
                                "VND"),
                        "payment-create-0002"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Reservation not found");
    }

    @Test
    void cashPaymentIsRecordedAsSucceededWithoutExternalProvider() {
        UUID receptionist = UUID.randomUUID();
        ReservationPaymentProjection reservation = reservation(UUID.randomUUID());
        when(reservationRepository.findById(reservation.getReservationId()))
                .thenReturn(Optional.of(reservation));

        PaymentIntent result = service.recordCash(
                receptionist,
                new PaymentCommands.RecordCash(
                        reservation.getReservationId(),
                        PaymentPurpose.DEPOSIT,
                        new BigDecimal("300000"),
                        "VND",
                        "Received at front desk"),
                "payment-cash-0001");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.getProvider()).isEqualTo(PaymentProviderCode.CASH);
        verify(events).payment(result, "payment.succeeded.v1");
    }

    private static ReservationPaymentProjection reservation(UUID customerId) {
        return new ReservationPaymentProjection(
                UUID.randomUUID(),
                customerId,
                "guest@example.com",
                "RSV-TEST",
                "PENDING_PAYMENT",
                "VND",
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                1,
                Instant.now());
    }
}
