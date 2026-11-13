package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stay_operations")
public class StayOperation {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID stayId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private OperationType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OperationStatus status;
    @Column(nullable = false, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String requestHash;
    private UUID fromRoomId;
    private UUID targetRoomId;
    private LocalDate previousCheckoutDate;
    private LocalDate requestedCheckoutDate;
    @Column(precision = 19, scale = 2)
    private BigDecimal amountDelta;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(length = 500)
    private String lastError;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant completedAt;
    @Version
    private long version;

    protected StayOperation() {}

    public static StayOperation roomChange(
            UUID stayId, String key, String hash, UUID fromRoomId, UUID targetRoomId,
            BigDecimal amountDelta, String reason, UUID actorId) {
        StayOperation value = base(stayId, OperationType.ROOM_CHANGE, key, hash, actorId);
        value.status = OperationStatus.PENDING_TARGET;
        value.fromRoomId = fromRoomId;
        value.targetRoomId = targetRoomId;
        value.amountDelta = amountDelta == null ? BigDecimal.ZERO.setScale(2) : amountDelta.setScale(2);
        value.reason = clean(reason);
        return value;
    }

    public static StayOperation extension(
            UUID stayId, String key, String hash, LocalDate currentCheckout,
            LocalDate requestedCheckout, BigDecimal amountDelta, String reason, UUID actorId) {
        StayOperation value = base(stayId, OperationType.EXTENSION, key, hash, actorId);
        value.status = OperationStatus.PENDING_HOLD;
        value.previousCheckoutDate = currentCheckout;
        value.requestedCheckoutDate = requestedCheckout;
        value.amountDelta = amountDelta.setScale(2);
        value.reason = clean(reason);
        return value;
    }

    public static StayOperation checkout(
            UUID stayId, String key, String hash, UUID actorId) {
        StayOperation value = base(stayId, OperationType.CHECKOUT, key, hash, actorId);
        value.status = OperationStatus.PAYMENT_PENDING;
        return value;
    }

    private static StayOperation base(
            UUID stayId, OperationType type, String key, String hash, UUID actorId) {
        StayOperation value = new StayOperation();
        value.id = UUID.randomUUID();
        value.stayId = stayId;
        value.type = type;
        value.idempotencyKey = key;
        value.requestHash = hash;
        value.requestedBy = actorId;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void targetOccupied() {
        require(OperationStatus.PENDING_TARGET);
        status = OperationStatus.TARGET_OCCUPIED;
        clearError();
    }

    public void releasePending() {
        if (status != OperationStatus.TARGET_OCCUPIED
                && status != OperationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Operation cannot enter release phase from " + status);
        }
        status = OperationStatus.RELEASE_PENDING;
        clearError();
    }

    public void paymentPending() {
        if (type != OperationType.CHECKOUT) {
            throw new IllegalStateException("Only checkout can wait for payment");
        }
        status = OperationStatus.PAYMENT_PENDING;
        updatedAt = Instant.now();
    }

    public void complete() {
        status = OperationStatus.COMPLETED;
        completedAt = Instant.now();
        clearError();
    }

    public void failed(String detail) {
        String value = detail == null ? "Unknown saga failure" : detail.trim();
        lastError = value.substring(0, Math.min(500, value.length()));
        updatedAt = Instant.now();
    }

    private void require(OperationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected operation status " + expected + " but was " + status);
        }
    }

    private void clearError() {
        lastError = null;
        updatedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public OperationType getType() { return type; }
    public OperationStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getFromRoomId() { return fromRoomId; }
    public UUID getTargetRoomId() { return targetRoomId; }
    public LocalDate getPreviousCheckoutDate() { return previousCheckoutDate; }
    public LocalDate getRequestedCheckoutDate() { return requestedCheckoutDate; }
    public BigDecimal getAmountDelta() { return amountDelta; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
}
