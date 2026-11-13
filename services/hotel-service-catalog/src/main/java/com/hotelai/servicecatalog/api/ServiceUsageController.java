package com.hotelai.servicecatalog.api;

import com.hotelai.servicecatalog.api.CatalogDtos.AdjustUsageRequest;
import com.hotelai.servicecatalog.api.CatalogDtos.CancelUsageRequest;
import com.hotelai.servicecatalog.api.CatalogDtos.MarkPerformedRequest;
import com.hotelai.servicecatalog.api.CatalogDtos.RecordUsageRequest;
import com.hotelai.servicecatalog.api.CatalogDtos.StaySummaryView;
import com.hotelai.servicecatalog.api.CatalogDtos.UsageLedgerView;
import com.hotelai.servicecatalog.domain.ServiceUsageApplicationService;
import com.hotelai.servicecatalog.domain.ServiceUsageApplicationService.AdjustUsage;
import com.hotelai.servicecatalog.domain.ServiceUsageApplicationService.CancelUsage;
import com.hotelai.servicecatalog.domain.ServiceUsageApplicationService.RecordUsage;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/admin")
public class ServiceUsageController {
    private final ServiceUsageApplicationService application;

    public ServiceUsageController(ServiceUsageApplicationService application) {
        this.application = application;
    }

    @GetMapping("/service-stays")
    List<StaySummaryView> searchStays(
            @RequestParam(name = "query", required = false) String query) {
        return application.searchStays(query).stream().map(StaySummaryView::from).toList();
    }

    @GetMapping("/service-stays/{stayId}/usages")
    UsageLedgerView ledger(@PathVariable UUID stayId) {
        return UsageLedgerView.from(application.ledger(stayId));
    }

    @PostMapping("/service-usages")
    @ResponseStatus(HttpStatus.CREATED)
    UsageLedgerView record(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecordUsageRequest request) {
        return UsageLedgerView.from(application.record(
                HotelServiceController.actor(authentication),
                idempotencyKey,
                new RecordUsage(
                        request.stayId(), request.serviceId(), request.quantity(),
                        request.occurredAt(), request.performed(), request.reason())));
    }

    @PutMapping("/service-usages/{usageId}")
    UsageLedgerView adjust(
            Authentication authentication,
            @PathVariable UUID usageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AdjustUsageRequest request) {
        return UsageLedgerView.from(application.adjust(
                HotelServiceController.actor(authentication),
                usageId,
                idempotencyKey,
                new AdjustUsage(
                        request.quantity(), request.occurredAt(), request.reason())));
    }

    @PostMapping("/service-usages/{usageId}/performed")
    UsageLedgerView markPerformed(
            Authentication authentication,
            @PathVariable UUID usageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) MarkPerformedRequest request) {
        return UsageLedgerView.from(application.markPerformed(
                HotelServiceController.actor(authentication),
                usageId,
                idempotencyKey,
                request == null ? null : request.reason()));
    }

    @PostMapping("/service-usages/{usageId}/cancel")
    UsageLedgerView cancel(
            Authentication authentication,
            @PathVariable UUID usageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CancelUsageRequest request) {
        return UsageLedgerView.from(application.cancel(
                HotelServiceController.actor(authentication),
                manager(authentication),
                usageId,
                idempotencyKey,
                new CancelUsage(request.reason())));
    }

    private static boolean manager(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_MANAGER".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
