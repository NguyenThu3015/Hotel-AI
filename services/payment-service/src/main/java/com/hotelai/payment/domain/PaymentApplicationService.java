package com.hotelai.payment.domain;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.config.PaymentProperties;
import com.hotelai.payment.provider.CassoPaymentProvider;
import com.hotelai.payment.provider.PaymentProvider;
import com.hotelai.payment.provider.ProviderRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentApplicationService {
    private static final List<PaymentStatus> ACTIVE = List.of(PaymentStatus.CREATED, PaymentStatus.PENDING);
    private static final List<String> PAYMENT_ELIGIBLE = List.of(
            "PENDING_PAYMENT", "PENDING_CONFIRMATION", "CONFIRMED", "CHECKED_IN", "CHECKED_OUT");

    private final PaymentIntentRepository payments;
    private final ReservationPaymentProjectionRepository reservations;
    private final RefundRequestRepository refunds;
    private final PaymentIdempotencyRepository idempotency;
    private final ProviderRegistry providers;
    private final ReservationSnapshotCodec snapshots;
    private final PaymentEventService events;
    private final ObjectMapper objectMapper;
    private final PaymentProperties properties;

    public PaymentApplicationService(
            PaymentIntentRepository payments,
            ReservationPaymentProjectionRepository reservations,
            RefundRequestRepository refunds,
            PaymentIdempotencyRepository idempotency,
            ProviderRegistry providers,
            ReservationSnapshotCodec snapshots,
            PaymentEventService events,
            ObjectMapper objectMapper,
            PaymentProperties properties) {
        this.payments = payments;
        this.reservations = reservations;
        this.refunds = refunds;
        this.idempotency = idempotency;
        this.providers = providers;
        this.snapshots = snapshots;
        this.events = events;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public PaymentIntent createIntent(
            UUID actorId, boolean staff, PaymentCommands.CreateIntent command, String key) {
        requireKey(key);
        if (command.provider() == null || command.provider() == PaymentProviderCode.CASH) {
            throw ApiException.validation("Online payment intent provider must be CASSO, MOMO or SANDBOX");
        }
        String scope = "CREATE_INTENT:" + command.reservationId();
        String hash = hash(command);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayPayment(replay.get(), hash);

        ReservationPaymentProjection reservation = requiredReservation(command.reservationId());
        requireAccess(reservation, actorId, staff);
        validateCharge(reservation, command.purpose(), command.amount(), command.currency());
        payments.lockReservation(reservation.getReservationId());
        requireNoActivePayment(reservation.getReservationId());

        PaymentIntent payment = payments.save(PaymentIntent.create(
                reservation.getReservationId(), reservation.getCustomerId(), actorId,
                command.purpose(), command.provider(), command.amount(), command.currency(), reservation,
                snapshots.hash(reservation), Instant.now().plus(properties.payment().intentTtl())));
        events.payment(payment, "payment.intent-created.v1");
        try {
            String extraData = snapshots.encode(payment);
            PaymentProvider.CheckoutSession checkout = providers.required(command.provider())
                    .createCheckout(payment, extraData);
            payment.startProvider(checkout.requestId(), checkout.extraData(), checkout.checkoutUrl());
        } catch (PaymentProvider.UncertainProviderResult error) {
            payment.markProviderResultUnknown(
                    error.providerRequestId(),
                    snapshots.encode(payment),
                    error.auditDetail());
        } catch (RuntimeException error) {
            payment.fail("PROVIDER_INITIATION_FAILED", safeMessage(error));
            events.payment(payment, "payment.failed.v1");
        }
        remember(key, scope, actorId, hash, "PAYMENT", payment.getId());
        return payment;
    }

    @Transactional
    public PaymentIntent recordCash(
            UUID actorId, PaymentCommands.RecordCash command, String key) {
        requireKey(key);
        String scope = "RECORD_CASH:" + command.reservationId();
        String hash = hash(command);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayPayment(replay.get(), hash);

        ReservationPaymentProjection reservation = requiredReservation(command.reservationId());
        validateCharge(reservation, command.purpose(), command.amount(), command.currency());
        payments.lockReservation(reservation.getReservationId());
        requireNoActivePayment(reservation.getReservationId());
        PaymentIntent payment = PaymentIntent.create(
                reservation.getReservationId(), reservation.getCustomerId(), actorId,
                command.purpose(), PaymentProviderCode.CASH, command.amount(), command.currency(), reservation,
                snapshots.hash(reservation), null);
        payment.setStaffNote(command.note());
        payment = payments.save(payment);
        events.payment(payment, "payment.intent-created.v1");
        payment.succeed("CASH-" + payment.getId(), Instant.now());
        events.payment(payment, "payment.succeeded.v1");
        remember(key, scope, actorId, hash, "PAYMENT", payment.getId());
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentIntent get(UUID actorId, boolean staff, UUID paymentId) {
        PaymentIntent payment = requiredPayment(paymentId);
        requireAccess(payment, actorId, staff);
        return payment;
    }

    @Transactional(readOnly = true)
    public CassoPaymentProvider.TransferInstructions bankTransferInstructions(
            UUID actorId, boolean staff, UUID paymentId) {
        PaymentIntent payment = requiredPayment(paymentId);
        requireAccess(payment, actorId, staff);
        PaymentProvider provider = providers.required(payment.getProvider());
        if (!(provider instanceof CassoPaymentProvider casso)) {
            throw ApiException.conflict(
                    "wrong-provider", "Payment does not use Casso bank transfer");
        }
        return casso.transferInstructions(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> history(UUID actorId, boolean staff, UUID reservationId, Pageable pageable) {
        if (reservationId == null) {
            if (!staff) return payments.findByCustomerIdOrderByCreatedAtDesc(actorId, pageable);
            throw ApiException.validation("Staff payment history requires reservationId");
        }
        ReservationPaymentProjection reservation = requiredReservation(reservationId);
        requireAccess(reservation, actorId, staff);
        return payments.findByReservationIdOrderByCreatedAtDesc(reservationId, pageable);
    }

    @Transactional
    public PaymentIntent cancel(UUID actorId, boolean staff, UUID paymentId, String key) {
        requireKey(key);
        String scope = "CANCEL_PAYMENT:" + paymentId;
        String hash = hash(paymentId);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayPayment(replay.get(), hash);
        PaymentIntent payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        requireAccess(payment, actorId, staff);
        if (payment.cancel()) events.payment(payment, "payment.cancelled.v1");
        remember(key, scope, actorId, hash, "PAYMENT", paymentId);
        return payment;
    }

    @Transactional
    public RefundRequest requestRefund(
            UUID actorId, boolean staff, PaymentCommands.RequestRefund command, String key) {
        requireKey(key);
        String scope = "REQUEST_REFUND:" + command.paymentId();
        String hash = hash(command);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayRefund(replay.get(), hash);
        PaymentIntent payment = payments.findLockedById(command.paymentId())
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        requireAccess(payment, actorId, staff);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw ApiException.conflict("payment-not-refundable", "Only a succeeded payment can be refunded");
        }
        BigDecimal amount = money(command.amount());
        BigDecimal reserved = refunds.findByPaymentIdOrderByRequestedAtDesc(payment.getId()).stream()
                .filter(refund -> refund.getStatus().reservesFunds())
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reserved.add(amount).compareTo(payment.getAmount()) > 0) {
            throw ApiException.conflict("refund-exceeds-payment", "Refund total exceeds the succeeded payment amount");
        }
        RefundRequest refund = refunds.save(new RefundRequest(
                payment.getId(), amount, payment.getCurrency(), command.reason(), actorId));
        events.refund(payment, refund, "payment.refund-requested.v1");
        remember(key, scope, actorId, hash, "REFUND", refund.getId());
        return refund;
    }

    @Transactional
    public RefundRequest approveRefund(UUID actorId, UUID refundId, String key) {
        requireKey(key);
        String scope = "APPROVE_REFUND:" + refundId;
        String hash = hash(refundId);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayRefund(replay.get(), hash);
        RefundRequest refund = requiredLockedRefund(refundId);
        PaymentIntent payment = requiredPayment(refund.getPaymentId());
        refund.approve(actorId);
        events.refund(payment, refund, "payment.refund-approved.v1");
        try {
            PaymentProvider.RefundSubmission submission = providers.required(payment.getProvider())
                    .submitRefund(payment, refund);
            if (submission.completed()) {
                completeRefund(
                        payment,
                        refund,
                        submission.providerRefundId(),
                        submission.rawResponse());
            } else {
                refund.processing(submission.providerRefundId(), submission.rawResponse());
            }
        } catch (PaymentProvider.UncertainProviderResult error) {
            refund.processing(error.providerRequestId(), error.auditDetail());
        } catch (RuntimeException error) {
            refund.fail(safeMessage(error));
            events.refund(payment, refund, "payment.refund-failed.v1");
        }
        remember(key, scope, actorId, hash, "REFUND", refundId);
        return refund;
    }

    @Transactional
    public RefundRequest rejectRefund(UUID actorId, UUID refundId, String reason, String key) {
        requireKey(key);
        String scope = "REJECT_REFUND:" + refundId;
        String hash = hash(refundId, reason);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayRefund(replay.get(), hash);
        RefundRequest refund = requiredLockedRefund(refundId);
        PaymentIntent payment = requiredPayment(refund.getPaymentId());
        refund.reject(actorId, reason);
        events.refund(payment, refund, "payment.refund-rejected.v1");
        remember(key, scope, actorId, hash, "REFUND", refundId);
        return refund;
    }

    @Transactional
    public RefundRequest completeRefundByStaff(
            UUID actorId, UUID refundId, String providerReference, String note, String key) {
        requireKey(key);
        RefundRequest refund = requiredLockedRefund(refundId);
        PaymentIntent payment = requiredPayment(refund.getPaymentId());
        if (payment.getProvider() == PaymentProviderCode.MOMO) {
            throw ApiException.forbidden("MoMo refunds can only be completed by a verified provider callback");
        }
        String scope = "COMPLETE_REFUND:" + refundId;
        String hash = hash(refundId, providerReference, note);
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayRefund(replay.get(), hash);
        completeRefund(payment, refund, providerReference, note);
        remember(key, scope, actorId, hash, "REFUND", refundId);
        return refund;
    }

    @Transactional
    public RefundRequest completeRefundInternal(
            UUID refundId, String providerReference, boolean succeeded, String providerResponse) {
        RefundRequest refund = requiredLockedRefund(refundId);
        PaymentIntent payment = requiredPayment(refund.getPaymentId());
        if (succeeded) {
            completeRefund(payment, refund, providerReference, providerResponse);
        } else {
            refund.fail(providerResponse);
            events.refund(payment, refund, "payment.refund-failed.v1");
        }
        return refund;
    }

    @Transactional
    public PaymentIntent completeSandbox(
            UUID paymentId,
            boolean succeeded,
            BigDecimal amount,
            String currency,
            String snapshotHash,
            String providerTransactionId,
            String failureCode) {
        PaymentIntent payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (payment.getProvider() != PaymentProviderCode.SANDBOX) {
            throw ApiException.conflict("wrong-provider", "Payment is not a sandbox payment");
        }
        validateProviderCallback(payment, amount, currency, snapshotHash);
        if (succeeded) {
            if (payment.succeed(providerTransactionId, Instant.now())) {
                events.payment(payment, "payment.succeeded.v1");
            }
        } else if (payment.fail(failureCode, "Sandbox payment failed")) {
            events.payment(payment, "payment.failed.v1");
        }
        return payment;
    }

    @Transactional
    public void expirePayments(Instant now, int batchSize) {
        payments.findExpiredForUpdate(ACTIVE, now, Pageable.ofSize(batchSize)).forEach(payment -> {
            if (payment.getProvider() == PaymentProviderCode.MOMO) {
                try {
                    if (resolveProviderInquiry(payment)) return;
                } catch (RuntimeException ignored) {
                    // Financially safer to keep an unknown MoMo result pending than mark a paid order expired.
                    return;
                }
            }
            if (payment.expire(now)) events.payment(payment, "payment.failed.v1");
        });
    }

    @Transactional(readOnly = true)
    public RefundRequest getRefund(UUID refundId) {
        return refunds.findById(refundId).orElseThrow(() -> ApiException.notFound("Refund request not found"));
    }

    @Transactional(readOnly = true)
    public Page<RefundRequest> refundHistory(UUID paymentId, Pageable pageable) {
        requiredPayment(paymentId);
        return refunds.findByPaymentIdOrderByRequestedAtDesc(paymentId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequest> refundHistory(Pageable pageable) {
        return refunds.findAllByOrderByRequestedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public PaymentReceipt receipt(UUID actorId, boolean staff, UUID paymentId) {
        PaymentIntent payment = requiredPayment(paymentId);
        requireAccess(payment, actorId, staff);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw ApiException.conflict("receipt-unavailable", "A receipt is available only for a succeeded payment");
        }
        ReservationPaymentProjection reservation = requiredReservation(payment.getReservationId());
        return PaymentReceipt.from(payment, reservation);
    }

    @Transactional
    public PaymentReceipt requestReceiptEmail(UUID actorId, boolean staff, UUID paymentId, String key) {
        requireKey(key);
        String scope = "EMAIL_RECEIPT:" + paymentId;
        String hash = hash(paymentId, "EMAIL");
        Optional<PaymentIdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) {
            PaymentIntent replayed = replayPayment(replay.get(), hash);
            return receipt(actorId, staff, replayed.getId());
        }
        PaymentIntent payment = requiredPayment(paymentId);
        requireAccess(payment, actorId, staff);
        PaymentReceipt receipt = receipt(actorId, staff, paymentId);
        ReservationPaymentProjection reservation = requiredReservation(payment.getReservationId());
        events.requestReceiptEmail(payment, reservation, actorId);
        remember(key, scope, actorId, hash, "PAYMENT", paymentId);
        return receipt;
    }

    @Transactional
    public long cleanExpiredIdempotency(Instant now) {
        return idempotency.deleteByExpiresAtBefore(now);
    }

    @Transactional
    public void reconcileProcessingRefunds(int batchSize) {
        refunds.findByStatusOrderByUpdatedAtAsc(
                        RefundStatus.PROCESSING, Pageable.ofSize(batchSize))
                .forEach(refund -> {
                    PaymentIntent payment = requiredPayment(refund.getPaymentId());
                    try {
                        PaymentProvider.RefundInquiry inquiry =
                                providers.required(payment.getProvider()).queryRefund(payment, refund);
                        if (!inquiry.terminal()) return;
                        if (inquiry.succeeded()) {
                            completeRefund(
                                    payment,
                                    refund,
                                    inquiry.providerRefundId(),
                                    inquiry.rawResponse());
                        } else {
                            refund.fail(inquiry.rawResponse());
                            events.refund(payment, refund, "payment.refund-failed.v1");
                        }
                    } catch (RuntimeException ignored) {
                        // A query failure is not a refund failure. Keep PROCESSING for the next reconciliation.
                    }
                });
    }

    @Transactional
    public void reconcilePendingMomoPayments(int batchSize) {
        payments.findByStatusAndProviderOrderByUpdatedAtAsc(
                        PaymentStatus.PENDING, PaymentProviderCode.MOMO, Pageable.ofSize(batchSize))
                .forEach(payment -> {
                    try {
                        resolveProviderInquiry(payment);
                    } catch (RuntimeException ignored) {
                        // Keep PENDING. The next scheduler run or a verified IPN can resolve the transaction.
                    }
                });
    }

    private boolean resolveProviderInquiry(PaymentIntent payment) {
        PaymentProvider.PaymentInquiry inquiry =
                providers.required(payment.getProvider()).queryPayment(payment);
        if (!inquiry.terminal()) return false;
        if (inquiry.succeeded()) {
            String extraData = inquiry.extraData() == null
                    ? payment.getProviderExtraData()
                    : inquiry.extraData();
            ReservationSnapshotCodec.SnapshotExtra extra = snapshots.decode(extraData);
            if (!payment.getId().equals(extra.paymentId())
                    || !payment.getReservationId().equals(extra.reservationId())) {
                throw ApiException.conflict(
                        "reservation-snapshot-mismatch",
                        "Provider query snapshot does not match payment intent");
            }
            validateProviderCallback(
                    payment, inquiry.amount(), inquiry.currency(), extra.snapshotHash());
            if (payment.succeed(inquiry.providerTransactionId(), inquiry.occurredAt())) {
                events.payment(payment, "payment.succeeded.v1");
            }
        } else if (payment.fail(inquiry.failureCode(), inquiry.rawResponse())) {
            events.payment(payment, "payment.failed.v1");
        }
        return true;
    }

    public void validateProviderCallback(
            PaymentIntent payment, BigDecimal amount, String currency, String snapshotHash) {
        if (amount == null || payment.getAmount().compareTo(amount) != 0) {
            throw ApiException.conflict("provider-amount-mismatch", "Provider amount does not match payment intent");
        }
        if (!payment.getCurrency().equals(currency)) {
            throw ApiException.conflict("provider-currency-mismatch", "Provider currency does not match payment intent");
        }
        if (!constantTimeEquals(payment.getReservationSnapshotHash(), snapshotHash)) {
            throw ApiException.conflict("reservation-snapshot-mismatch", "Provider reservation snapshot does not match payment intent");
        }
    }

    private void validateCharge(
            ReservationPaymentProjection reservation,
            PaymentPurpose purpose,
            BigDecimal requestedAmount,
            String currency) {
        if (!PAYMENT_ELIGIBLE.contains(reservation.getStatus())) {
            throw ApiException.conflict(
                    "reservation-not-payable", "Reservation cannot be paid in status " + reservation.getStatus());
        }
        if (purpose == null) throw ApiException.validation("Payment purpose is required");
        if (!reservation.getCurrency().equals(currency)) {
            throw ApiException.validation("Payment currency must match the reservation currency");
        }
        BigDecimal localSucceeded = payments.sumSucceeded(reservation.getReservationId());
        BigDecimal effectivePaid = reservation.getPaidAmount().max(localSucceeded == null ? BigDecimal.ZERO : localSucceeded);
        BigDecimal expected;
        switch (purpose) {
            case DEPOSIT -> expected = reservation.getDepositRequired().subtract(effectivePaid).max(BigDecimal.ZERO);
            case FULL -> {
                if (effectivePaid.signum() > 0) {
                    throw ApiException.conflict(
                            "full-payment-not-available", "Use BALANCE after a partial or deposit payment");
                }
                expected = reservation.getTotalAmount();
            }
            case BALANCE -> {
                if (effectivePaid.signum() <= 0) {
                    throw ApiException.conflict(
                            "balance-payment-not-available", "Use FULL when no previous payment exists");
                }
                expected = reservation.getTotalAmount().subtract(effectivePaid).max(BigDecimal.ZERO);
            }
            default -> throw ApiException.validation("Unsupported payment purpose");
        }
        BigDecimal amount = money(requestedAmount);
        if (expected.signum() <= 0) {
            throw ApiException.conflict("nothing-to-pay", "No amount remains for this payment purpose");
        }
        if (expected.compareTo(amount) != 0) {
            throw ApiException.conflict(
                    "payment-amount-mismatch", "Expected " + expected.toPlainString() + " " + currency);
        }
    }

    private void requireNoActivePayment(UUID reservationId) {
        if (payments.existsByReservationIdAndStatusIn(reservationId, ACTIVE)) {
            throw ApiException.conflict(
                    "payment-already-pending", "Reservation already has an active payment intent");
        }
    }

    private void completeRefund(
            PaymentIntent payment, RefundRequest refund, String providerReference, String response) {
        if (refund.complete(providerReference, response)) {
            events.refund(payment, refund, "payment.refund-completed.v1");
        }
    }

    private ReservationPaymentProjection requiredReservation(UUID id) {
        return reservations.findById(id).orElseThrow(() -> ApiException.conflict(
                "reservation-projection-unavailable",
                "Reservation is not yet available in Payment Service; retry after event synchronization"));
    }

    private PaymentIntent requiredPayment(UUID id) {
        return payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment not found"));
    }

    private RefundRequest requiredLockedRefund(UUID id) {
        return refunds.findLockedById(id).orElseThrow(() -> ApiException.notFound("Refund request not found"));
    }

    private static void requireAccess(ReservationPaymentProjection reservation, UUID actorId, boolean staff) {
        if (!staff && !reservation.getCustomerId().equals(actorId)) {
            throw ApiException.notFound("Reservation not found");
        }
    }

    private static void requireAccess(PaymentIntent payment, UUID actorId, boolean staff) {
        if (!staff && !payment.getCustomerId().equals(actorId)) {
            throw ApiException.notFound("Payment not found");
        }
    }

    private PaymentIntent replayPayment(PaymentIdempotencyRecord record, String hash) {
        requireSameRequest(record, hash);
        if (!"PAYMENT".equals(record.getResourceType())) {
            throw ApiException.conflict("idempotency-key-reused", "Idempotency key has a different result type");
        }
        return requiredPayment(record.getResourceId());
    }

    private RefundRequest replayRefund(PaymentIdempotencyRecord record, String hash) {
        requireSameRequest(record, hash);
        if (!"REFUND".equals(record.getResourceType())) {
            throw ApiException.conflict("idempotency-key-reused", "Idempotency key has a different result type");
        }
        return refunds.findById(record.getResourceId())
                .orElseThrow(() -> ApiException.notFound("Refund request not found"));
    }

    private void remember(
            String key, String scope, UUID actorId, String hash, String type, UUID resourceId) {
        idempotency.save(new PaymentIdempotencyRecord(
                key, scope, actorId, hash, type, resourceId,
                Instant.now().plus(properties.payment().idempotencyTtl())));
    }

    private Optional<PaymentIdempotencyRecord> replay(String key, String scope, UUID actorId) {
        idempotency.lockScopeKey(actorId + "|" + scope + "|" + key);
        return idempotency.findByKeyAndScopeAndActorId(key, scope, actorId);
    }

    private static void requireSameRequest(PaymentIdempotencyRecord record, String hash) {
        if (!constantTimeEquals(record.getRequestHash(), hash)) {
            throw ApiException.conflict(
                    "idempotency-key-reused", "Idempotency-Key was already used with a different request");
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation("A valid Idempotency-Key header (8-160 safe characters) is required");
        }
    }

    private String hash(Object... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(values)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash idempotent payment command", error);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw ApiException.validation("Amount must be positive");
        try {
            return value.setScale(2);
        } catch (ArithmeticException error) {
            throw ApiException.validation("Amount supports at most two decimal places");
        }
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.substring(0, Math.min(500, message.length()));
    }

    public record PaymentReceipt(
            UUID paymentId,
            UUID reservationId,
            String reservationReference,
            UUID customerId,
            PaymentPurpose purpose,
            PaymentProviderCode provider,
            BigDecimal amount,
            String currency,
            String providerReference,
            Instant paidAt) {
        static PaymentReceipt from(
                PaymentIntent payment, ReservationPaymentProjection reservation) {
            return new PaymentReceipt(
                    payment.getId(),
                    payment.getReservationId(),
                    reservation.getReference(),
                    payment.getCustomerId(),
                    payment.getPurpose(),
                    payment.getProvider(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProviderTransactionId(),
                    payment.getCompletedAt());
        }
    }
}
