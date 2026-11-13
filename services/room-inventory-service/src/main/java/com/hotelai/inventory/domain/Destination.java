package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "destinations")
public class Destination {
    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String province;

    @Column(nullable = false, length = 20)
    private String region;

    @Column(name = "region_label", nullable = false, length = 60)
    private String regionLabel;

    @Column(name = "style_tag", nullable = false, length = 80)
    private String styleTag;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(nullable = false, length = 200)
    private String landmark;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    protected Destination() {}

    public Destination(
            String code,
            String name,
            String province,
            String region,
            String regionLabel,
            String styleTag,
            String propertyName,
            String landmark,
            String description,
            int displayOrder,
            boolean active) {
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.province = province.trim();
        this.region = region.trim().toUpperCase();
        this.regionLabel = regionLabel.trim();
        this.styleTag = styleTag.trim();
        this.propertyName = propertyName.trim();
        this.landmark = landmark.trim();
        this.description = description.trim();
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void update(
            String name,
            String province,
            String region,
            String regionLabel,
            String styleTag,
            String propertyName,
            String landmark,
            String description,
            int displayOrder,
            boolean active) {
        this.name = name.trim();
        this.province = province.trim();
        this.region = region.trim().toUpperCase();
        this.regionLabel = regionLabel.trim();
        this.styleTag = styleTag.trim();
        this.propertyName = propertyName.trim();
        this.landmark = landmark.trim();
        this.description = description.trim();
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getProvince() { return province; }
    public String getRegion() { return region; }
    public String getRegionLabel() { return regionLabel; }
    public String getStyleTag() { return styleTag; }
    public String getPropertyName() { return propertyName; }
    public String getLandmark() { return landmark; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
