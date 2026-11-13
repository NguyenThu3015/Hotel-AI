package com.hotelai.customer.api;

import com.hotelai.customer.domain.CustomerApplicationService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {
    private final CustomerApplicationService service;

    public AdminCustomerController(CustomerApplicationService service) {
        this.service = service;
    }

    @GetMapping
    Page<CustomerDtos.ProfileResponse> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(query, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerDtos.ProfileResponse createWalkIn(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.WalkInCreateRequest request) {
        return service.createWalkIn(request, identityId(jwt), roles(jwt));
    }

    @GetMapping("/{customerId}")
    CustomerDtos.ProfileResponse detail(
            @PathVariable UUID customerId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.getForStaff(customerId, roles(jwt));
    }

    @PutMapping("/{customerId}")
    CustomerDtos.ProfileResponse update(
            @PathVariable UUID customerId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.StaffUpdateRequest request) {
        return service.updateForStaff(customerId, request, identityId(jwt), roles(jwt));
    }

    @GetMapping("/{customerId}/notes")
    Page<CustomerDtos.NoteResponse> notes(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.notes(customerId, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping("/{customerId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerDtos.NoteResponse addNote(
            @PathVariable UUID customerId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.NoteRequest request) {
        return service.addNote(customerId, request.note(), identityId(jwt));
    }

    @PostMapping("/merge-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerDtos.MergeResponse propose(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.MergeRequest request) {
        return service.proposeMerge(request, identityId(jwt));
    }

    @GetMapping("/merge-proposals")
    Page<CustomerDtos.MergeResponse> mergeProposals(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.mergeProposals(
                status, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping("/merge-proposals/{proposalId}/approve")
    CustomerDtos.MergeResponse approve(
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.MergeDecisionRequest request) {
        return service.decideMerge(proposalId, true, request.comment(), identityId(jwt), roles(jwt));
    }

    @PostMapping("/merge-proposals/{proposalId}/reject")
    CustomerDtos.MergeResponse reject(
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.MergeDecisionRequest request) {
        return service.decideMerge(proposalId, false, request.comment(), identityId(jwt), roles(jwt));
    }

    private static Set<String> roles(Jwt jwt) {
        java.util.stream.Stream<String> roles = claimValues(jwt, "roles");
        java.util.stream.Stream<String> permissions = claimValues(jwt, "permissions");
        return java.util.stream.Stream.concat(roles, permissions).collect(Collectors.toSet());
    }

    private static java.util.stream.Stream<String> claimValues(Jwt jwt, String name) {
        Object claim = jwt.getClaims().get(name);
        if (claim instanceof java.util.Collection<?> values) return values.stream().map(String::valueOf);
        return claim == null ? java.util.stream.Stream.empty()
                : Arrays.stream(String.valueOf(claim).split(",")).map(String::trim);
    }

    private static UUID identityId(Jwt jwt) {
        String value = jwt.getClaimAsString("uid");
        return UUID.fromString(value == null ? jwt.getSubject() : value);
    }
}
