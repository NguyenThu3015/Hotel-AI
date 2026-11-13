package com.hotelai.payment.domain;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.provider.CassoPaymentProvider;
import com.hotelai.payment.provider.CassoWebhook;
import com.hotelai.payment.provider.MomoPaymentProvider;
import com.hotelai.payment.provider.MomoWebhook;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {
    private static final Pattern CASSO_ORDER_ID =
            Pattern.compile("(?i)(?<![A-Z0-9])PAY[-\\s]?([A-F0-9]{24,32})(?![A-Z0-9])");
    private final PaymentIntentRepository payments;
    private final PaymentWebhookRepository deliveries;
    private final PaymentApplicationService application;
    private final PaymentEventService events;
    private final ReservationSnapshotCodec snapshots;
    private final MomoPaymentProvider momo;
    private final CassoPaymentProvider casso;

    public PaymentWebhookService(
            PaymentIntentRepository payments,
            PaymentWebhookRepository deliveries,
            PaymentApplicationService application,
            PaymentEventService events,
            ReservationSnapshotCodec snapshots,
            MomoPaymentProvider momo,
            CassoPaymentProvider casso) {
        this.payments = payments;
        this.deliveries = deliveries;
        this.application = application;
        this.events = events;
        this.snapshots = snapshots;
        this.momo = momo;
        this.casso = casso;
    }

    @Transactional
    public WebhookOutcome processMomo(String rawPayload, String correlationId) {
        MomoWebhook webhook = momo.parseWebhook(rawPayload);
        boolean signatureValid = momo.verifyWebhook(webhook);
        String eventKey = signatureValid
                ? webhook.eventKey()
                : webhook.eventKey() + ":invalid:" + ReservationSnapshotCodec.sha256(rawPayload).substring(0, 16);
        PaymentWebhookDelivery existing =
                deliveries.findByProviderAndProviderEventKey("MOMO", eventKey).orElse(null);
        if (existing != null) {
            existing.replayed();
            return replayOutcome(existing);
        }

        PaymentWebhookDelivery delivery = deliveries.save(new PaymentWebhookDelivery(
                "MOMO",
                eventKey,
                rawPayload,
                webhook.signature(),
                signatureValid,
                correlationId == null ? UUID.randomUUID().toString() : correlationId));
        if (!signatureValid) {
            delivery.processed(null, webhook.transId(), "INVALID_SIGNATURE", "MoMo signature verification failed");
            return new WebhookOutcome(HttpStatus.UNAUTHORIZED, false, "INVALID_SIGNATURE", null);
        }

        try {
            PaymentIntent payment = payments.findLockedByProviderOrderId(webhook.orderId())
                    .orElseThrow(() -> ApiException.notFound("Payment intent for MoMo order was not found"));
            validateIdentity(payment, webhook);
            ReservationSnapshotCodec.SnapshotExtra extra = snapshots.decode(webhook.extraData());
            validateSnapshotIdentity(payment, extra);
            application.validateProviderCallback(
                    payment, webhook.amount(), "VND", extra.snapshotHash());

            if (webhook.succeeded()) {
                if (webhook.transId() == null || webhook.transId().isBlank()) {
                    throw ApiException.conflict(
                            "provider-reference-missing", "Successful MoMo callback has no transaction reference");
                }
                if (payment.succeed(webhook.transId(), webhook.occurredAt())) {
                    events.payment(payment, "payment.succeeded.v1");
                }
                delivery.processed(payment.getId(), webhook.transId(), "SUCCEEDED", null);
            } else {
                if (payment.fail("MOMO_" + webhook.resultCode(), webhook.message())) {
                    events.payment(payment, "payment.failed.v1");
                }
                delivery.processed(
                        payment.getId(), webhook.transId(), "FAILED", webhook.resultCode() + ": " + webhook.message());
            }
            return new WebhookOutcome(HttpStatus.NO_CONTENT, false, delivery.getProcessingResult(), payment.getId());
        } catch (RuntimeException error) {
            delivery.processed(null, webhook.transId(), "REJECTED", safeMessage(error));
            return new WebhookOutcome(HttpStatus.CONFLICT, false, "REJECTED", null);
        }
    }

    @Transactional
    public WebhookOutcome processCasso(
            String rawPayload, String signature, String correlationId) {
        CassoWebhook webhook = casso.parseWebhook(rawPayload);
        boolean signatureValid = casso.verifyWebhook(rawPayload, signature);
        String eventKey = signatureValid
                ? webhook.eventKey()
                : webhook.eventKey() + ":invalid:"
                        + ReservationSnapshotCodec.sha256(rawPayload).substring(0, 16);
        PaymentWebhookDelivery existing =
                deliveries.findByProviderAndProviderEventKey("CASSO", eventKey).orElse(null);
        if (existing != null && !canReconcile(existing)) {
            existing.replayed();
            return cassoReplayOutcome(existing);
        }

        PaymentWebhookDelivery delivery = existing == null
                ? deliveries.save(new PaymentWebhookDelivery(
                        "CASSO",
                        eventKey,
                        rawPayload,
                        signature,
                        signatureValid,
                        correlationId == null ? UUID.randomUUID().toString() : correlationId))
                : existing;
        if (existing != null) delivery.replayed();
        if (!signatureValid) {
            delivery.processed(
                    null,
                    webhook.transactionReference(),
                    "INVALID_SIGNATURE",
                    "Casso Webhook V2 signature verification failed");
            return new WebhookOutcome(HttpStatus.UNAUTHORIZED, false, "INVALID_SIGNATURE", null);
        }

        return completeCassoTransfer(delivery, webhook);
    }

    private WebhookOutcome completeCassoTransfer(
            PaymentWebhookDelivery delivery, CassoWebhook webhook) {
        if (webhook.amount().signum() <= 0) {
            delivery.processed(
                    null, webhook.transactionReference(), "IGNORED_OUTGOING", null);
            return new WebhookOutcome(HttpStatus.NO_CONTENT, false, "IGNORED_OUTGOING", null);
        }
        String providerOrderId = extractCassoOrderId(webhook.description());
        if (providerOrderId == null) {
            delivery.processed(
                    null, webhook.transactionReference(), "IGNORED_UNMATCHED", null);
            return new WebhookOutcome(HttpStatus.NO_CONTENT, false, "IGNORED_UNMATCHED", null);
        }
        List<PaymentIntent> matches = payments.findLockedByProviderOrderIdPrefix(providerOrderId);
        PaymentIntent payment = matches.size() == 1 ? matches.get(0) : null;
        if (payment == null) {
            delivery.processed(
                    null,
                    webhook.transactionReference(),
                    matches.isEmpty() ? "IGNORED_UNKNOWN_ORDER" : "REJECTED_AMBIGUOUS_ORDER",
                    matches.isEmpty()
                            ? "No Casso payment intent matches the transfer content"
                            : "Casso transfer content matches more than one payment intent");
            return new WebhookOutcome(
                    HttpStatus.NO_CONTENT,
                    false,
                    matches.isEmpty() ? "IGNORED_UNKNOWN_ORDER" : "REJECTED_AMBIGUOUS_ORDER",
                    null);
        }

        try {
            validateCassoIdentity(payment, webhook, providerOrderId);
            PaymentIntent duplicate = payments.findByProviderTransactionId(
                            webhook.transactionReference())
                    .orElse(null);
            if (duplicate != null && !duplicate.getId().equals(payment.getId())) {
                throw ApiException.conflict(
                        "provider-reference-reused",
                        "Casso bank reference is already assigned to another payment");
            }
            application.validateProviderCallback(
                    payment,
                    webhook.amount(),
                    "VND",
                    payment.getReservationSnapshotHash());
            if (payment.succeedVerifiedBankTransfer(
                    webhook.transactionReference(), webhook.occurredAt())) {
                events.payment(payment, "payment.succeeded.v1");
            }
            delivery.processed(
                    payment.getId(), webhook.transactionReference(), "SUCCEEDED", null);
            return new WebhookOutcome(HttpStatus.NO_CONTENT, false, "SUCCEEDED", payment.getId());
        } catch (ApiException | IllegalStateException error) {
            delivery.processed(
                    payment.getId(), webhook.transactionReference(), "REJECTED", safeMessage(error));
            return new WebhookOutcome(HttpStatus.NO_CONTENT, false, "REJECTED", payment.getId());
        }
    }

    @Scheduled(fixedDelayString = "${hotel.providers.casso.reconciliation-delay-ms:15000}")
    @Transactional
    public void reconcileIgnoredCassoTransfers() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<PaymentWebhookDelivery> candidates = deliveries
                .findTop50ByProviderAndSignatureValidTrueAndProcessingResultInOrderByReceivedAtAsc(
                        "CASSO", List.of("IGNORED_UNMATCHED", "IGNORED_UNKNOWN_ORDER"));
        for (PaymentWebhookDelivery delivery : candidates) {
            if (delivery.getReceivedAt().isBefore(cutoff)) continue;
            try {
                delivery.replayed();
                completeCassoTransfer(delivery, casso.parseWebhook(delivery.getRawPayload()));
            } catch (RuntimeException error) {
                delivery.processed(
                        delivery.getPaymentId(),
                        delivery.getProviderTransactionId(),
                        "REJECTED",
                        safeMessage(error));
            }
        }
    }

    private static boolean canReconcile(PaymentWebhookDelivery delivery) {
        return delivery.isSignatureValid()
                && ("IGNORED_UNMATCHED".equals(delivery.getProcessingResult())
                    || "IGNORED_UNKNOWN_ORDER".equals(delivery.getProcessingResult()));
    }

    private static void validateIdentity(PaymentIntent payment, MomoWebhook webhook) {
        if (payment.getProvider() != PaymentProviderCode.MOMO) {
            throw ApiException.conflict("wrong-provider", "Callback order is not a MoMo payment");
        }
        if (!Objects.equals(payment.getProviderOrderId(), webhook.orderId())
                || !Objects.equals(payment.getProviderRequestId(), webhook.requestId())) {
            throw ApiException.conflict(
                    "provider-identity-mismatch", "MoMo order or request identity does not match payment intent");
        }
    }

    private void validateCassoIdentity(
            PaymentIntent payment, CassoWebhook webhook, String providerOrderId) {
        if (payment.getProvider() != PaymentProviderCode.CASSO) {
            throw ApiException.conflict("wrong-provider", "Transfer order is not a Casso payment");
        }
        if (!payment.getProviderOrderId().startsWith(providerOrderId)) {
            throw ApiException.conflict(
                    "provider-identity-mismatch", "Casso transfer content does not match payment intent");
        }
        if (!casso.matchesReceivingAccount(webhook)) {
            throw ApiException.conflict(
                    "receiving-account-mismatch", "Casso transaction belongs to another bank account");
        }
    }

    private static String extractCassoOrderId(String description) {
        if (description == null || description.isBlank()) return null;
        Matcher matcher = CASSO_ORDER_ID.matcher(description);
        return matcher.find()
                ? "PAY-" + matcher.group(1).toUpperCase(java.util.Locale.ROOT)
                : null;
    }

    private static void validateSnapshotIdentity(
            PaymentIntent payment, ReservationSnapshotCodec.SnapshotExtra extra) {
        if (!payment.getId().equals(extra.paymentId())
                || !payment.getReservationId().equals(extra.reservationId())
                || !payment.getCurrency().equals(extra.currency())
                || payment.getReservationVersionSnapshot() != extra.reservationVersion()) {
            throw ApiException.conflict(
                    "reservation-snapshot-mismatch", "MoMo extraData does not match payment intent snapshot");
        }
    }

    private static WebhookOutcome replayOutcome(PaymentWebhookDelivery delivery) {
        String result = delivery.getProcessingResult();
        HttpStatus status = "INVALID_SIGNATURE".equals(result)
                ? HttpStatus.UNAUTHORIZED
                : "REJECTED".equals(result) ? HttpStatus.CONFLICT : HttpStatus.NO_CONTENT;
        return new WebhookOutcome(status, true, result, delivery.getPaymentId());
    }

    private static WebhookOutcome cassoReplayOutcome(PaymentWebhookDelivery delivery) {
        String result = delivery.getProcessingResult();
        HttpStatus status = "INVALID_SIGNATURE".equals(result)
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.NO_CONTENT;
        return new WebhookOutcome(status, true, result, delivery.getPaymentId());
    }

    private static String safeMessage(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        return value.substring(0, Math.min(500, value.length()));
    }

    public record WebhookOutcome(
            HttpStatus httpStatus, boolean replay, String processingResult, UUID paymentId) {}
}
