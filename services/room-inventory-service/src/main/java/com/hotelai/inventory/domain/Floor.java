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
@Table(name = "floors")
public class Floor {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "level_number", nullable = false, unique = true)
    private int levelNumber;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected Floor() {}

    public Floor(String code, String name, int levelNumber) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.active = true;
        update(code, name, levelNumber, true);
    }

    public void update(String code, String name, int levelNumber, boolean active) {
        this.code = normalizeCode(code);
        this.name = name.trim();
        this.levelNumber = levelNumber;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    private static String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getLevelNumber() { return levelNumber; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
