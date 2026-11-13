package com.hotelai.customer.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.hotelai.customer.api.CustomerDtos;
import com.hotelai.customer.api.PiiMasking;
import com.hotelai.customer.common.ApiException;
import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApplicationService {
    private final CustomerProfileRepository profiles;
    private final CustomerNoteRepository notes;
    private final MergeProposalRepository merges;
    private final CustomerAuditRepository audits;
    private final OutboxEventRepository outbox;
    private final ProcessedIdentityEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public CustomerApplicationService(
            CustomerProfileRepository profiles,
            CustomerNoteRepository notes,
            MergeProposalRepository merges,
            CustomerAuditRepository audits,
            OutboxEventRepository outbox,
            ProcessedIdentityEventRepository processedEvents,
            ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.notes = notes;
        this.merges = merges;
        this.audits = audits;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomerDtos.ProfileResponse createFromIdentity(CustomerDtos.IdentityCreatedRequest request) {
        if (processedEvents.existsById(request.eventId())) {
            return full(requiredByIdentity(request.identityId()));
        }
        String identityStatus = request.status() == null || request.status().isBlank() ? "ACTIVE" : request.status();
        String roles = request.roles() == null || request.roles().isEmpty()
                ? "CUSTOMER"
                : String.join(",", request.roles());
        CustomerProfile profile = profiles.findByIdentityId(request.identityId()).orElseGet(() ->
                profiles.save(new CustomerProfile(
                        request.identityId(), request.fullName().trim(), request.email().trim(), request.phone(),
                        Boolean.TRUE.equals(request.emailVerified()), identityStatus, roles)));
        profile.refreshIdentitySnapshot(Boolean.TRUE.equals(request.emailVerified()), identityStatus, roles);
        processedEvents.save(new ProcessedIdentityEvent(request.eventId()));
        auditAndPublish(profile, request.identityId(), "CUSTOMER_PROFILE_CREATED", "Customer base profile created");
        return full(profile);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.ProfileResponse getMine(UUID identityId) {
        return full(requiredByIdentity(identityId));
    }

    @Transactional
    public CustomerDtos.ProfileResponse updateMine(UUID identityId, CustomerDtos.UpdateProfileRequest request) {
        CustomerProfile profile = requiredByIdentity(identityId);
        ensureActive(profile);
        if (request.email() != null && !request.email().equalsIgnoreCase(profile.getEmail())) {
            throw new ApiException(
                    409,
                    "Email đăng nhập do Identity Service sở hữu; phải xác minh lại trước khi thay đổi");
        }
        if (request.expectedVersion() != null && profile.getVersion() != request.expectedVersion()) {
            throw new ApiException(409, "Profile version is stale; reload before saving");
        }
        profile.updateContact(
                value(request.fullName(), profile.getFullName()), profile.getEmail(),
                request.phone() == null ? profile.getPhone() : request.phone(),
                request.dateOfBirth() == null ? profile.getDateOfBirth() : request.dateOfBirth(),
                request.gender() == null ? profile.getGender() : request.gender());
        profile.updatePreferences(
                value(request.preferredLanguage(), profile.getPreferredLanguage()),
                value(request.roomPreferences(), profile.getRoomPreferences()));
        profile.updateConsent(
                request.marketingEmail() == null ? profile.isMarketingEmail() : request.marketingEmail(),
                request.marketingSms() == null ? profile.isMarketingSms() : request.marketingSms(),
                request.marketingZalo() == null ? profile.isMarketingZalo() : request.marketingZalo());
        auditAndPublish(profile, identityId, "CUSTOMER_PROFILE_UPDATED", "Contact, preferences or consent updated");
        return full(profile);
    }

    @Transactional(readOnly = true)
    public Page<CustomerDtos.ProfileResponse> search(String query, Pageable pageable) {
        return profiles.search(query == null ? "" : query.trim(), pageable).map(this::masked);
    }

    @Transactional
    public CustomerDtos.ProfileResponse createWalkIn(
            CustomerDtos.WalkInCreateRequest request, UUID actorId, Set<String> roles) {
        ensureCustomerWrite(roles);
        CustomerProfile existing = profiles
                .findFirstByEmailIgnoreCaseAndStatus(
                        request.email().trim(), CustomerProfile.Status.ACTIVE)
                .orElse(null);
        if (existing != null) return full(existing);
        CustomerProfile profile = profiles.save(new CustomerProfile(
                request.fullName(), request.email(), request.phone()));
        auditAndPublish(
                profile, actorId, "WALK_IN_CUSTOMER_CREATED", "Walk-in customer created by staff");
        return full(profile);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.ProfileResponse getForStaff(UUID customerId, Set<String> roles) {
        CustomerProfile profile = required(customerId);
        boolean canViewPii = roles.stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(role -> role.equals("ADMIN") || role.equals("MANAGER") || role.equals("PII_VIEW")
                        || role.equals("CUSTOMER_PII_READ") || role.equals("PERM_CUSTOMER_PII_READ"));
        return canViewPii ? full(profile) : masked(profile);
    }

    @Transactional
    public CustomerDtos.ProfileResponse updateForStaff(
            UUID customerId,
            CustomerDtos.StaffUpdateRequest request,
            UUID actorId,
            Set<String> roles) {
        ensureCustomerWrite(roles);
        CustomerProfile profile = required(customerId);
        ensureActive(profile);
        if (profile.getVersion() != request.expectedVersion()) {
            throw new ApiException(409, "Profile version is stale; reload before saving");
        }
        profile.updateContact(
                value(request.fullName(), profile.getFullName()),
                profile.getEmail(),
                request.phone() == null ? profile.getPhone() : request.phone(),
                request.dateOfBirth() == null ? profile.getDateOfBirth() : request.dateOfBirth(),
                request.gender() == null ? profile.getGender() : request.gender());
        profile.updatePreferences(
                value(request.preferredLanguage(), profile.getPreferredLanguage()),
                value(request.roomPreferences(), profile.getRoomPreferences()));
        auditAndPublish(profile, actorId, "CUSTOMER_PROFILE_UPDATED", "Customer profile updated by staff");
        return full(profile);
    }

    @Transactional
    public CustomerDtos.NoteResponse addNote(UUID customerId, String note, UUID actorId) {
        ensureActive(required(customerId));
        CustomerNote saved = notes.save(new CustomerNote(customerId, note.trim(), actorId));
        audits.save(new CustomerAudit(customerId, actorId, "CUSTOMER_NOTE_ADDED", "Staff note added"));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CustomerDtos.NoteResponse> notes(UUID customerId, Pageable pageable) {
        required(customerId);
        return notes.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable).map(this::toResponse);
    }

    @Transactional
    public CustomerDtos.MergeResponse proposeMerge(CustomerDtos.MergeRequest request, UUID actorId) {
        if (request.sourceCustomerId().equals(request.targetCustomerId())) {
            throw new ApiException(400, "Source and target customers must differ");
        }
        ensureActive(required(request.sourceCustomerId()));
        ensureActive(required(request.targetCustomerId()));
        MergeProposal proposal = merges.save(new MergeProposal(
                request.sourceCustomerId(), request.targetCustomerId(), request.reason().trim(), actorId));
        audits.save(new CustomerAudit(request.sourceCustomerId(), actorId, "CUSTOMER_MERGE_PROPOSED",
                "Target customer: " + request.targetCustomerId()));
        return toResponse(proposal);
    }

    @Transactional(readOnly = true)
    public Page<CustomerDtos.MergeResponse> mergeProposals(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return merges.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
        }
        try {
            return merges.findByStatusOrderByCreatedAtDesc(
                    MergeProposal.Status.valueOf(status.trim().toUpperCase(Locale.ROOT)), pageable)
                    .map(this::toResponse);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "Unknown merge proposal status");
        }
    }

    @Transactional
    public CustomerDtos.MergeResponse decideMerge(
            UUID proposalId,
            boolean approve,
            String comment,
            UUID actorId,
            Set<String> roles) {
        ensureMergeApprove(roles);
        MergeProposal proposal = merges.findById(proposalId)
                .orElseThrow(() -> new ApiException(404, "Merge proposal not found"));
        if (proposal.getStatus() != MergeProposal.Status.PENDING) {
            throw new ApiException(409, "Merge proposal was already decided");
        }
        if (approve) {
            CustomerProfile source = required(proposal.getSourceCustomerId());
            CustomerProfile target = required(proposal.getTargetCustomerId());
            ensureActive(source);
            ensureActive(target);
            source.mergeInto(target);
            proposal.decide(MergeProposal.Status.APPROVED, actorId, comment);
            auditAndPublish(source, actorId, "CUSTOMER_MERGED", "Merged into customer " + target.getId());
        } else {
            proposal.decide(MergeProposal.Status.REJECTED, actorId, comment);
            audits.save(new CustomerAudit(proposal.getSourceCustomerId(), actorId, "CUSTOMER_MERGE_REJECTED", comment));
        }
        return toResponse(proposal);
    }

    private CustomerProfile required(UUID id) {
        return profiles.findById(id).orElseThrow(() -> new ApiException(404, "Customer not found"));
    }

    private CustomerProfile requiredByIdentity(UUID identityId) {
        return profiles.findByIdentityId(identityId)
                .orElseThrow(() -> new ApiException(404, "Customer profile has not been provisioned"));
    }

    private static void ensureActive(CustomerProfile profile) {
        if (profile.getStatus() != CustomerProfile.Status.ACTIVE) {
            throw new ApiException(409, "Customer profile is not active");
        }
    }

    private static void ensureCustomerWrite(Set<String> roles) {
        if (!hasAnyRole(roles, "ADMIN", "MANAGER", "CUSTOMER_WRITE", "PERM_CUSTOMER_WRITE")) {
            throw new ApiException(403, "Customer profile write permission is required");
        }
    }

    private static void ensureMergeApprove(Set<String> roles) {
        if (!hasAnyRole(roles, "ADMIN", "MANAGER", "CUSTOMER_MERGE_APPROVE", "PERM_CUSTOMER_MERGE_APPROVE")) {
            throw new ApiException(403, "Customer merge approval permission is required");
        }
    }

    private static boolean hasAnyRole(Set<String> roles, String... expected) {
        Set<String> normalized = roles.stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(expected).anyMatch(normalized::contains);
    }

    private static String value(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
    }

    private void auditAndPublish(CustomerProfile profile, UUID actor, String action, String detail) {
        audits.save(new CustomerAudit(profile.getId(), actor, action, detail));
        outbox.save(new OutboxEvent(
                "CustomerProfile",
                profile.getId(),
                eventType(action),
                correlationId(),
                json(new EventPayload(profile.getId(), profile.getIdentityId(), action, profile.getVersion()))));
    }

    private static String eventType(String action) {
        return switch (action) {
            case "CUSTOMER_PROFILE_CREATED" -> "customer.profile.created.v1";
            case "CUSTOMER_PROFILE_UPDATED" -> "customer.profile.updated.v1";
            case "CUSTOMER_MERGED" -> "customer.profile.merged.v1";
            default -> "customer.activity.recorded.v1";
        };
    }

    private static UUID correlationId() {
        try {
            String value = MDC.get("correlationId");
            return value == null || value.isBlank() ? UUID.randomUUID() : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize outbox event", ex);
        }
    }

    private CustomerDtos.ProfileResponse full(CustomerProfile p) {
        return response(p, p.getEmail(), p.getPhone(), false);
    }

    private CustomerDtos.ProfileResponse masked(CustomerProfile p) {
        return response(p, PiiMasking.email(p.getEmail()), PiiMasking.phone(p.getPhone()), true);
    }

    private static CustomerDtos.ProfileResponse response(CustomerProfile p, String email, String phone, boolean masked) {
        return new CustomerDtos.ProfileResponse(
                p.getId(), p.getIdentityId(), p.getFullName(), email, phone, p.getDateOfBirth(), p.getGender(),
                p.getPreferredLanguage(), p.getRoomPreferences(), p.isMarketingEmail(), p.isMarketingSms(),
                p.isMarketingZalo(), p.getIdentityStatus(), p.getStatus().name(), p.isEmailVerified(),
                new LinkedHashSet<>(Arrays.asList(p.getRolesSnapshot().split(","))),
                p.getMergedIntoCustomerId(), p.getVersion(),
                p.getUpdatedAt(), masked);
    }

    private CustomerDtos.NoteResponse toResponse(CustomerNote n) {
        return new CustomerDtos.NoteResponse(n.getId(), n.getNote(), n.getCreatedByIdentityId(), n.getCreatedAt());
    }

    private CustomerDtos.MergeResponse toResponse(MergeProposal m) {
        return new CustomerDtos.MergeResponse(
                m.getId(), m.getSourceCustomerId(), m.getTargetCustomerId(), m.getReason(), m.getStatus().name(),
                m.getProposedByIdentityId(), m.getReviewedByIdentityId(), m.getReviewComment(), m.getCreatedAt(),
                m.getReviewedAt(), m.getVersion());
    }

    private record EventPayload(UUID customerId, UUID identityId, String eventType, long version) {}
}
