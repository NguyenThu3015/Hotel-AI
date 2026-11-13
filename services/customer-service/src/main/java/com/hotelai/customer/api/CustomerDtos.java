package com.hotelai.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record UpdateProfileRequest(
            @Size(max = 200) String fullName,
            @Email @Size(max = 320) String email,
            @Pattern(regexp = "^$|^[0-9+(). -]{7,32}$") String phone,
            LocalDate dateOfBirth,
            @Size(max = 16) String gender,
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String preferredLanguage,
            @Size(max = 8000) String roomPreferences,
            Boolean marketingEmail,
            Boolean marketingSms,
            Boolean marketingZalo,
            Long expectedVersion) {}

    public record ProfileResponse(
            UUID id,
            UUID identityId,
            String fullName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String gender,
            String preferredLanguage,
            String roomPreferences,
            boolean marketingEmail,
            boolean marketingSms,
            boolean marketingZalo,
            String status,
            String customerStatus,
            boolean emailVerified,
            Set<String> roles,
            UUID mergedIntoCustomerId,
            long version,
            Instant updatedAt,
            boolean piiMasked) {}

    public record IdentityCreatedRequest(
            @NotBlank @Size(max = 120) String eventId,
            @NotNull UUID identityId,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @Pattern(regexp = "^$|^[0-9+(). -]{7,32}$") String phone,
            Boolean emailVerified,
            @Size(max = 20) String status,
            Set<@Size(max = 80) String> roles) {}

    public record WalkInCreateRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @Pattern(regexp = "^$|^[0-9+(). -]{7,32}$") String phone) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {}

    public record StaffUpdateRequest(
            @Size(max = 200) String fullName,
            @Pattern(regexp = "^$|^[0-9+(). -]{7,32}$") String phone,
            LocalDate dateOfBirth,
            @Size(max = 16) String gender,
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String preferredLanguage,
            @Size(max = 8000) String roomPreferences,
            @NotNull Long expectedVersion) {}

    public record NoteResponse(UUID id, String note, UUID createdByIdentityId, Instant createdAt) {}

    public record MergeRequest(
            @NotNull UUID sourceCustomerId,
            @NotNull UUID targetCustomerId,
            @NotBlank @Size(max = 2000) String reason) {}

    public record MergeDecisionRequest(@NotBlank @Size(max = 2000) String comment) {}

    public record MergeResponse(
            UUID id,
            UUID sourceCustomerId,
            UUID targetCustomerId,
            String reason,
            String status,
            UUID proposedByIdentityId,
            UUID reviewedByIdentityId,
            String reviewComment,
            Instant createdAt,
            Instant reviewedAt,
            long version) {}
}
