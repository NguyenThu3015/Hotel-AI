package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "hotel_services")
public class HotelServiceItem {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false, length = 30)
    private String unit;
    @Column(name = "service_days", nullable = false, length = 100)
    private String serviceDays;
    @Column(name = "service_start_time")
    private LocalTime serviceStartTime;
    @Column(name = "service_end_time")
    private LocalTime serviceEndTime;
    @Column(name = "service_conditions", length = 1000)
    private String serviceConditions;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected HotelServiceItem() {}

    public HotelServiceItem(
            String code,
            String name,
            String description,
            String unit,
            Set<DayOfWeek> serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime,
            String serviceConditions,
            boolean active,
            UUID createdBy) {
        this.id = UUID.randomUUID();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        update(
                code, name, description, unit, serviceDays,
                serviceStartTime, serviceEndTime, serviceConditions, active);
    }

    public void update(
            String code,
            String name,
            String description,
            String unit,
            Set<DayOfWeek> serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime,
            String serviceConditions,
            boolean active) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Service code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name is required");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Service description is required");
        }
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Service unit is required");
        if (serviceDays == null || serviceDays.isEmpty()) {
            throw new IllegalArgumentException("At least one service day is required");
        }
        if ((serviceStartTime == null) != (serviceEndTime == null)) {
            throw new IllegalArgumentException("Service start and end time must both be supplied");
        }
        if (serviceStartTime != null && !serviceStartTime.isBefore(serviceEndTime)) {
            throw new IllegalArgumentException("Service start time must be before end time");
        }
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = description.trim();
        this.unit = unit.trim();
        this.serviceDays = new TreeSet<>(serviceDays).stream()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
        this.serviceStartTime = serviceStartTime;
        this.serviceEndTime = serviceEndTime;
        this.serviceConditions = clean(serviceConditions);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public boolean isAvailableAt(Instant occurredAt, ZoneId zoneId) {
        return active && isScheduledAt(occurredAt, zoneId);
    }

    public boolean isScheduledAt(Instant occurredAt, ZoneId zoneId) {
        if (occurredAt == null) return false;
        ZonedDateTime local = occurredAt.atZone(zoneId);
        if (!getServiceDays().contains(local.getDayOfWeek())) return false;
        if (serviceStartTime == null) return true;
        LocalTime time = local.toLocalTime();
        return !time.isBefore(serviceStartTime) && time.isBefore(serviceEndTime);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public Set<DayOfWeek> getServiceDays() {
        return Arrays.stream(serviceDays.split(","))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
    public LocalTime getServiceStartTime() { return serviceStartTime; }
    public LocalTime getServiceEndTime() { return serviceEndTime; }
    public String getServiceConditions() { return serviceConditions; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
