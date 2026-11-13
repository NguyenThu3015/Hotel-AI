package com.hotelai.inventory.domain;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_holds")
public class InventoryHold {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "external_reference", length = 120)
    private String externalReference;
    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkIn;
    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOut;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected InventoryHold() {}

    public InventoryHold(
            RoomType roomType, String externalReference, LocalDate checkIn, LocalDate checkOut,
            int quantity, Instant expiresAt, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID();
        this.roomType = roomType;
        this.externalReference = clean(externalReference);
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.quantity = quantity;
        this.status = "HELD";
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isExpired(Instant now) {
        return "HELD".equals(status) && !expiresAt.isAfter(now);
    }

    public void expire(Instant now) {
        if (isExpired(now)) {
            this.status = "EXPIRED";
            this.updatedAt = now;
        }
    }

    public void confirm(UUID reservationId) {
        if ("CONFIRMED".equals(status) && reservationId.equals(this.reservationId)) return;
        if (!"HELD".equals(status) || isExpired(Instant.now())) {
            throw ApiException.conflict("hold-not-active", "Giữ chỗ không còn hiệu lực.");
        }
        this.reservationId = reservationId;
        this.status = "CONFIRMED";
        this.expiresAt = null;
        this.updatedAt = Instant.now();
    }

    public void release() {
        if ("RELEASED".equals(status)) return;
        if ("EXPIRED".equals(status)) return;
        this.status = "RELEASED";
        this.expiresAt = null;
        this.updatedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getRoomTypeId() { return roomType.getId(); }
    public UUID getReservationId() { return reservationId; }
    public String getExternalReference() { return externalReference; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public long getVersion() { return version; }
}
