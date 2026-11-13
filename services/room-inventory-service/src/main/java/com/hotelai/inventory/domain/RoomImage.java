package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_images")
public class RoomImage {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;
    @Column(nullable = false, length = 1000)
    private String url;
    @Column(name = "alt_text", nullable = false, length = 300)
    private String altText;
    @Column(name = "media_type", nullable = false, length = 80)
    private String mediaType;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected RoomImage() {}

    public RoomImage(
            RoomType roomType, String url, String altText, String mediaType, long byteSize,
            int displayOrder) {
        this.id = UUID.randomUUID();
        this.roomType = roomType;
        this.createdAt = Instant.now();
        this.active = true;
        update(url, altText, mediaType, byteSize, displayOrder, true);
    }

    public void update(
            String url, String altText, String mediaType, long byteSize, int displayOrder,
            boolean active) {
        this.url = url.trim();
        this.altText = altText.trim();
        this.mediaType = mediaType;
        this.byteSize = byteSize;
        this.displayOrder = displayOrder;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRoomTypeId() { return roomType.getId(); }
    public String getUrl() { return url; }
    public String getAltText() { return altText; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
