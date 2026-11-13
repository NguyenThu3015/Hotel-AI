package com.hotelai.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "room_types")
public class RoomType {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(name = "max_adults", nullable = false)
    private int maxAdults;
    @Column(name = "max_children", nullable = false)
    private int maxChildren;
    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;
    @Column(name = "size_sqm", precision = 7, scale = 2)
    private BigDecimal sizeSqm;
    @Column(name = "bed_description", length = 500)
    private String bedDescription;
    @Column(name = "policy_summary", length = 2000)
    private String policySummary;
    @Column(name = "policy_snapshot_version", nullable = false)
    private long policySnapshotVersion;
    @Column(nullable = false)
    private boolean published;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "destination_code", nullable = false, length = 40)
    private String destinationCode = "PHU_QUOC";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "room_type_amenities",
            joinColumns = @JoinColumn(name = "room_type_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, orphanRemoval = false)
    private Set<RoomImage> images = new LinkedHashSet<>();

    protected RoomType() {}

    public RoomType(
            String code, String name, String description, int maxAdults, int maxChildren,
            int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
            boolean published, Set<Amenity> amenities) {
        this(code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                bedDescription, published, amenities, "PHU_QUOC");
    }

    public RoomType(
            String code, String name, String description, int maxAdults, int maxChildren,
            int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
            boolean published, Set<Amenity> amenities, String destinationCode) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.active = true;
        update(code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                bedDescription, published, true, amenities, destinationCode);
    }

    public void update(
            String code, String name, String description, int maxAdults, int maxChildren,
            int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
            boolean published, boolean active, Set<Amenity> amenities) {
        update(code, name, description, maxAdults, maxChildren, maxOccupancy, sizeSqm,
                bedDescription, published, active, amenities, this.destinationCode);
    }

    public void update(
            String code, String name, String description, int maxAdults, int maxChildren,
            int maxOccupancy, BigDecimal sizeSqm, String bedDescription,
            boolean published, boolean active, Set<Amenity> amenities, String destinationCode) {
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = description.trim();
        this.maxAdults = maxAdults;
        this.maxChildren = maxChildren;
        this.maxOccupancy = maxOccupancy;
        this.sizeSqm = sizeSqm;
        this.bedDescription = clean(bedDescription);
        this.published = published;
        this.active = active;
        if (destinationCode != null && !destinationCode.isBlank()) {
            this.destinationCode = destinationCode.trim().toUpperCase(Locale.ROOT);
        } else if (this.destinationCode == null || this.destinationCode.isBlank()) {
            this.destinationCode = "PHU_QUOC";
        }
        this.amenities.clear();
        this.amenities.addAll(amenities);
        this.updatedAt = Instant.now();
    }

    public boolean updatePolicySummary(String policySummary, long policyVersion) {
        if (policyVersion <= this.policySnapshotVersion) return false;
        this.policySummary = clean(policySummary);
        this.policySnapshotVersion = policyVersion;
        this.updatedAt = Instant.now();
        return true;
    }

    public void attachImage(RoomImage image) {
        this.images.add(image);
        this.updatedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getMaxAdults() { return maxAdults; }
    public int getMaxChildren() { return maxChildren; }
    public int getMaxOccupancy() { return maxOccupancy; }
    public BigDecimal getSizeSqm() { return sizeSqm; }
    public String getBedDescription() { return bedDescription; }
    public String getPolicySummary() { return policySummary; }
    public long getPolicySnapshotVersion() { return policySnapshotVersion; }
    public boolean isPublished() { return published; }
    public boolean isActive() { return active; }
    public String getDestinationCode() { return destinationCode; }
    public long getVersion() { return version; }
    public Set<Amenity> getAmenities() { return Set.copyOf(amenities); }
    public List<RoomImage> activeImages() {
        return images.stream()
                .filter(RoomImage::isActive)
                .sorted(Comparator.comparingInt(RoomImage::getDisplayOrder).thenComparing(RoomImage::getId))
                .toList();
    }
}
