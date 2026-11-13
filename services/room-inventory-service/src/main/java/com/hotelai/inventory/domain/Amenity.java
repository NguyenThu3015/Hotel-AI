package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "amenities")
public class Amenity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(name = "icon_url", length = 1000)
    private String iconUrl;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected Amenity() {}

    public Amenity(String code, String name, String description, String iconUrl) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.active = true;
        update(code, name, description, iconUrl, true);
    }

    public void update(String code, String name, String description, String iconUrl, boolean active) {
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = clean(description);
        this.iconUrl = clean(iconUrl);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconUrl() { return iconUrl; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
