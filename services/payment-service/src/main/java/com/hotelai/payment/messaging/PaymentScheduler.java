package com.hotelai.payment.messaging;

import com.hotelai.payment.domain.PaymentApplicationService;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentScheduler {
    private final PaymentApplicationService payments;

    public PaymentScheduler(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${hotel.payment.expiry-scan-ms:30000}")
    public void expirePaymentIntents() {
        payments.expirePayments(Instant.now(), 100);
    }

    @Scheduled(fixedDelayString = "${hotel.payment.provider-reconciliation-ms:60000}")
    public void reconcilePendingMomoPayments() {
        payments.reconcilePendingMomoPayments(50);
    }

    @Scheduled(cron = "${hotel.payment.idempotency-cleanup-cron:0 15 3 * * *}")
    public void cleanIdempotencyRecords() {
        payments.cleanExpiredIdempotency(Instant.now());
    }

    @Scheduled(fixedDelayString = "${hotel.payment.refund-reconciliation-ms:60000}")
    public void reconcilePendingRefunds() {
        payments.reconcileProcessingRefunds(50);
    }
}
