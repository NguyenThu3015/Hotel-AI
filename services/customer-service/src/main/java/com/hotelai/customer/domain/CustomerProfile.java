package com.hotelai.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {
    public enum Status { ACTIVE, MERGED, ANONYMIZED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identity_id", unique = true, updatable = false)
    private UUID identityId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 16)
    private String gender;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "vi";

    @Column(name = "room_preferences", nullable = false, columnDefinition = "text")
    private String roomPreferences = "{}";

    @Column(name = "marketing_email", nullable = false)
    private boolean marketingEmail;

    @Column(name = "marketing_sms", nullable = false)
    private boolean marketingSms;

    @Column(name = "marketing_zalo", nullable = false)
    private boolean marketingZalo;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "identity_status", nullable = false, length = 20)
    private String identityStatus = "ACTIVE";

    @Column(name = "roles_snapshot", nullable = false, length = 500)
    private String rolesSnapshot = "CUSTOMER";

    @Column(name = "consent_updated_at")
    private Instant consentUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "merged_into_customer_id")
    private UUID mergedIntoCustomerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CustomerProfile() {}

    public CustomerProfile(
            UUID identityId,
            String fullName,
            String email,
            String phone,
            boolean emailVerified,
            String identityStatus,
            String rolesSnapshot) {
        this.identityId = identityId;
        this.fullName = fullName;
        this.email = email.toLowerCase();
        this.phone = phone;
        this.emailVerified = emailVerified;
        this.identityStatus = identityStatus;
        this.rolesSnapshot = rolesSnapshot;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public CustomerProfile(String fullName, String email, String phone) {
        this.identityId = null;
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.emailVerified = false;
        this.identityStatus = "OFFLINE";
        this.rolesSnapshot = "GUEST";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateContact(String fullName, String email, String phone, LocalDate dateOfBirth, String gender) {
        this.fullName = fullName;
        this.email = email.toLowerCase();
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.updatedAt = Instant.now();
    }

    public void updatePreferences(String language, String preferences) {
        this.preferredLanguage = language;
        this.roomPreferences = preferences;
        this.updatedAt = Instant.now();
    }

    public void updateConsent(boolean email, boolean sms, boolean zalo) {
        this.marketingEmail = email;
        this.marketingSms = sms;
        this.marketingZalo = zalo;
        this.consentUpdatedAt = Instant.now();
        this.updatedAt = consentUpdatedAt;
    }

    public void refreshIdentitySnapshot(boolean emailVerified, String identityStatus, String rolesSnapshot) {
        this.emailVerified = emailVerified;
        this.identityStatus = identityStatus;
        this.rolesSnapshot = rolesSnapshot;
        this.updatedAt = Instant.now();
    }

    public void mergeInto(CustomerProfile target) {
        this.status = Status.MERGED;
        this.mergedIntoCustomerId = target.id;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIdentityId() { return identityId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getRoomPreferences() { return roomPreferences; }
    public boolean isMarketingEmail() { return marketingEmail; }
    public boolean isMarketingSms() { return marketingSms; }
    public boolean isMarketingZalo() { return marketingZalo; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getIdentityStatus() { return identityStatus; }
    public String getRolesSnapshot() { return rolesSnapshot; }
    public Instant getConsentUpdatedAt() { return consentUpdatedAt; }
    public Status getStatus() { return status; }
    public UUID getMergedIntoCustomerId() { return mergedIntoCustomerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
