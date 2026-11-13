package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "hotel_profiles")
public class HotelProfile {
    @Id private short id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 500)
    private String address;
    @Column(columnDefinition = "text")
    private String description;
    @Column(length = 30)
    private String phone;
    @Column(length = 320)
    private String email;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected HotelProfile() {}

    public void update(
            String name,
            String address,
            String description,
            String phone,
            String email,
            boolean active) {
        this.name = name.trim();
        this.address = address.trim();
        this.description = clean(description);
        this.phone = clean(phone);
        this.email = clean(email);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public short getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
