package com.hotelai.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.payment.provider.CassoPaymentProvider;
import com.hotelai.payment.provider.CassoWebhook;
import com.hotelai.payment.provider.MomoPaymentProvider;
import com.hotelai.payment.provider.MomoWebhook;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class PaymentWebhookServiceTest {
    private final PaymentIntentRepository payments = mock(PaymentIntentRepository.class);
    private final PaymentWebhookRepository deliveries = mock(PaymentWebhookRepository.class);
    private final PaymentApplicationService application = mock(PaymentApplicationService.class);
    private final PaymentEventService events = mock(PaymentEventService.class);
    private final MomoPaymentProvider momo = mock(MomoPaymentProvider.class);
    private final CassoPaymentProvider casso = mock(CassoPaymentProvider.class);
    private ReservationSnapshotCodec snapshots;
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        snapshots = new ReservationSnapshotCodec(new ObjectMapper());
        service = new PaymentWebhookService(
                payments, deliveries, application, events, snapshots, momo, casso);
        when(deliveries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void verifiedMomoCallbackMarksPaymentSucceededAndEmitsEventOnce() {
        PaymentIntent payment = momoPayment();
        String extra = snapshots.encode(payment);
        MomoWebhook webhook = webhook(payment, extra, 0);
        when(momo.parseWebhook("{}")).thenReturn(webhook);
        when(momo.verifyWebhook(webhook)).thenReturn(true);
        when(payments.findLockedByProviderOrderId(payment.getProviderOrderId()))
                .thenReturn(Optional.of(payment));

        PaymentWebhookService.WebhookOutcome result =
                service.processMomo("{}", "correlation-1");

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(events).payment(payment, "payment.succeeded.v1");
    }

    @Test
    void invalidSignatureIsAuditedWithoutTouchingPayment() {
        PaymentIntent payment = momoPayment();
        MomoWebhook webhook = webhook(payment, snapshots.encode(payment), 0);
        when(momo.parseWebhook("invalid")).thenReturn(webhook);
        when(momo.verifyWebhook(webhook)).thenReturn(false);

        PaymentWebhookService.WebhookOutcome result =
                service.processMomo("invalid", "correlation-2");

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.processingResult()).isEqualTo("INVALID_SIGNATURE");
        verify(payments, never()).findLockedByProviderOrderId(any());
        verify(events, never()).payment(any(), any());
    }

    @Test
    void verifiedCassoTransferMarksMatchingPaymentSucceeded() {
        PaymentIntent payment = cassoPayment();
        CassoWebhook webhook = new CassoWebhook(
                "73151",
                "BANK-REFERENCE-73151",
                "HOTELAI " + payment.getProviderOrderId(),
                payment.getAmount(),
                Instant.now(),
                "1234567890",
                "MB Bank",
                "MBB",
                null);
        when(casso.parseWebhook("{\"error\":0}")).thenReturn(webhook);
        when(casso.verifyWebhook("{\"error\":0}", "t=1,v1=signature"))
                .thenReturn(true);
        when(casso.matchesReceivingAccount(webhook)).thenReturn(true);
        when(payments.findLockedByProviderOrderIdPrefix(payment.getProviderOrderId()))
                .thenReturn(java.util.List.of(payment));
        when(payments.findByProviderTransactionId(webhook.transactionReference()))
                .thenReturn(Optional.empty());

        PaymentWebhookService.WebhookOutcome result = service.processCasso(
                "{\"error\":0}", "t=1,v1=signature", "correlation-casso-1");

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.processingResult()).isEqualTo("SUCCEEDED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("BANK-REFERENCE-73151");
        verify(application).validateProviderCallback(
                payment, payment.getAmount(), "VND", payment.getReservationSnapshotHash());
        verify(events).payment(payment, "payment.succeeded.v1");
    }

    @Test
    void verifiedCassoTransferMatchesMbbankTruncatedOrderPrefix() {
        PaymentIntent payment = cassoPayment();
        String truncatedOrderId = payment.getProviderOrderId().substring(0, 4 + 29);
        CassoWebhook webhook = new CassoWebhook(
                "73153",
                "BANK-REFERENCE-73153",
                "MBVCB HOTELAI " + truncatedOrderId.replace("-", "") + ".CT DEN",
                payment.getAmount(),
                Instant.now(),
                "1234567890",
                "MB Bank",
                "MBB",
                null);
        when(casso.parseWebhook("truncated")).thenReturn(webhook);
        when(casso.verifyWebhook("truncated", "t=3,v1=signature")).thenReturn(true);
        when(casso.matchesReceivingAccount(webhook)).thenReturn(true);
        when(payments.findLockedByProviderOrderIdPrefix(truncatedOrderId))
                .thenReturn(java.util.List.of(payment));
        when(payments.findByProviderTransactionId(webhook.transactionReference()))
                .thenReturn(Optional.empty());

        PaymentWebhookService.WebhookOutcome result = service.processCasso(
                "truncated", "t=3,v1=signature", "correlation-casso-3");

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.processingResult()).isEqualTo("SUCCEEDED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(events).payment(payment, "payment.succeeded.v1");
    }

    @Test
    void unrelatedCassoTransferIsAcceptedButDoesNotTouchPayments() {
        CassoWebhook webhook = new CassoWebhook(
                "73152",
                "BANK-REFERENCE-73152",
                "CHUYEN TIEN CA NHAN",
                new BigDecimal("50000"),
                Instant.now(),
                "1234567890",
                "MB Bank",
                "MBB",
                null);
        when(casso.parseWebhook("unrelated")).thenReturn(webhook);
        when(casso.verifyWebhook("unrelated", "t=2,v1=signature")).thenReturn(true);

        PaymentWebhookService.WebhookOutcome result = service.processCasso(
                "unrelated", "t=2,v1=signature", "correlation-casso-2");

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.processingResult()).isEqualTo("IGNORED_UNMATCHED");
        verify(payments, never()).findLockedByProviderOrderId(any());
        verify(events, never()).payment(any(), any());
    }

    private static PaymentIntent momoPayment() {
        ReservationPaymentProjection reservation = new ReservationPaymentProjection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "guest@example.com",
                "RSV-MOMO",
                "PENDING_PAYMENT",
                "VND",
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                2,
                Instant.now());
        PaymentIntent payment = PaymentIntent.create(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getCustomerId(),
                PaymentPurpose.DEPOSIT,
                PaymentProviderCode.MOMO,
                new BigDecimal("300000"),
                "VND",
                reservation,
                "b".repeat(64),
                Instant.now().plusSeconds(900));
        payment.startProvider(payment.getId().toString(), "extra", "https://momo.example/pay");
        return payment;
    }

    private static PaymentIntent cassoPayment() {
        ReservationPaymentProjection reservation = new ReservationPaymentProjection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "guest@example.com",
                "RSV-CASSO",
                "PENDING_PAYMENT",
                "VND",
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                2,
                Instant.now());
        PaymentIntent payment = PaymentIntent.create(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getCustomerId(),
                PaymentPurpose.DEPOSIT,
                PaymentProviderCode.CASSO,
                new BigDecimal("300000"),
                "VND",
                reservation,
                "c".repeat(64),
                Instant.now().plusSeconds(900));
        payment.startProvider(
                "CASSO-" + payment.getId(), "snapshot", "https://img.vietqr.io/qr.png");
        return payment;
    }

    private static MomoWebhook webhook(PaymentIntent payment, String extra, long resultCode) {
        return new MomoWebhook(
                "PARTNER",
                payment.getProviderOrderId(),
                payment.getProviderRequestId(),
                "987654321",
                payment.getAmount(),
                resultCode,
                resultCode == 0 ? "Successful" : "Declined",
                "Hotel reservation",
                "momo_wallet",
                "qr",
                extra,
                "signature",
                Instant.now().toEpochMilli());
    }
}
