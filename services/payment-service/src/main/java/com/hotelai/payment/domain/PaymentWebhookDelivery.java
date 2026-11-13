package com.hotelai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_deliveries")
public class PaymentWebhookDelivery {
    @Id
    private UUID id;

    @Column(nullable = false, length = 20, updatable = false)
    private String provider;

    @Column(name = "provider_event_key", nullable = false, length = 320, updatable = false)
    private String providerEventKey;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "text", updatable = false)
    private String rawPayload;

    @Column(name = "raw_payload_sha256", nullable = false, length = 64, updatable = false)
    private String rawPayloadSha256;

    @Column(name = "signature_sha256", length = 64, updatable = false)
    private String signatureSha256;

    @Column(name = "signature_valid", nullable = false, updatable = false)
    private boolean signatureValid;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Column(name = "processing_result", length = 80)
    private String processingResult;

    @Column(name = "failure_detail", length = 500)
    private String failureDetail;

    @Column(name = "correlation_id", nullable = false, length = 120, updatable = false)
    private String correlationId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    private long version;

    protected PaymentWebhookDelivery() {}

    public PaymentWebhookDelivery(
            String provider,
            String providerEventKey,
            String rawPayload,
            String signature,
            boolean signatureValid,
            String correlationId) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.providerEventKey = providerEventKey;
        this.rawPayload = rawPayload;
        this.rawPayloadSha256 = ReservationSnapshotCodec.sha256(rawPayload);
        this.signatureSha256 = signature == null ? null : ReservationSnapshotCodec.sha256(signature);
        this.signatureValid = signatureValid;
        this.correlationId = correlationId;
        this.receivedAt = Instant.now();
    }

    public void replayed() { replayCount++; }

    public void processed(UUID paymentId, String transactionId, String result, String detail) {
        this.paymentId = paymentId;
        this.providerTransactionId = transactionId;
        this.processingResult = truncate(result, 80);
        this.failureDetail = truncate(detail, 500);
        this.processedAt = Instant.now();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    public UUID getId() { return id; }
    public String getRawPayload() { return rawPayload; }
    public Instant getReceivedAt() { return receivedAt; }
    public boolean isSignatureValid() { return signatureValid; }
    public int getReplayCount() { return replayCount; }
    public String getProcessingResult() { return processingResult; }
    public UUID getPaymentId() { return paymentId; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public String getFailureDetail() { return failureDetail; }
}
