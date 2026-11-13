package com.hotelai.reporting.api;

import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditLedgerService.AuditRecord;
import com.hotelai.reporting.audit.AuditLedgerService.IntegrityResult;
import com.hotelai.reporting.audit.AuditQueryService;
import com.hotelai.reporting.audit.AuditQueryService.AuditPage;
import com.hotelai.reporting.audit.AuditQueryService.AuditView;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditController {
    private final AuditQueryService queries;
    private final AuditLedgerService audit;

    public AuditController(AuditQueryService queries, AuditLedgerService audit) {
        this.queries = queries;
        this.audit = audit;
    }

    @GetMapping
    AuditPage search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        AuditPage result = queries.search(
                from, to, actorId, action, entityType, entityId, page, size);
        logAccess(jwt, "AUDIT_LOG_VIEWED", request, "page=" + page);
        return result;
    }

    @GetMapping("/export.csv")
    ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            HttpServletRequest request) {
        byte[] content = queries.exportCsv(
                from, to, actorId, action, entityType, entityId);
        logAccess(jwt, "AUDIT_LOG_EXPORTED", request, "CSV export");
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    @GetMapping("/integrity")
    IntegrityResult integrity(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        IntegrityResult result = audit.verifyIntegrity();
        logAccess(jwt, "AUDIT_INTEGRITY_VERIFIED", request, "valid=" + result.valid());
        return result;
    }

    private void logAccess(
            Jwt jwt, String action, HttpServletRequest request, String details) {
        audit.append(new AuditRecord(
                null,
                ReportingController.actor(jwt),
                action,
                "AuditLog",
                null,
                "reporting-audit-service",
                MDC.get("correlationId"),
                ReportingController.clientIp(request),
                null,
                null,
                details,
                Instant.now()));
    }
}
