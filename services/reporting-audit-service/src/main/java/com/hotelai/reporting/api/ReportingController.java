package com.hotelai.reporting.api;

import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditLedgerService.AuditRecord;
import com.hotelai.reporting.reporting.ReportExportService;
import com.hotelai.reporting.reporting.ReportExportService.ExportJob;
import com.hotelai.reporting.reporting.ReportExportService.ExportRequest;
import com.hotelai.reporting.reporting.ReportFileRenderer;
import com.hotelai.reporting.reporting.ReportFileRenderer.ExportFormat;
import com.hotelai.reporting.reporting.ReportFileRenderer.RenderedReport;
import com.hotelai.reporting.reporting.ReportingQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportingController {
    private final ReportingQueryService reports;
    private final ReportFileRenderer renderer;
    private final ReportExportService exports;
    private final AuditLedgerService audit;
    private final ObjectMapper objectMapper;

    public ReportingController(
            ReportingQueryService reports,
            ReportFileRenderer renderer,
            ReportExportService exports,
            AuditLedgerService audit,
            ObjectMapper objectMapper) {
        this.reports = reports;
        this.renderer = renderer;
        this.exports = exports;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    ReportingQueryService.Dashboard dashboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long hotelId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) String channel,
            HttpServletRequest request) {
        var result = reports.dashboard(from, to, roomTypeId, channel);
        logExportAccess(
                jwt, "REPORT_DASHBOARD_VIEWED", from, to, roomTypeId, channel, request);
        return result;
    }

    @GetMapping("/export.xlsx")
    ResponseEntity<byte[]> excel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long hotelId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) String channel,
            HttpServletRequest request) {
        return synchronous(
                jwt, from, to, roomTypeId, channel, ExportFormat.XLSX, request);
    }

    @GetMapping("/export.pdf")
    ResponseEntity<byte[]> pdf(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long hotelId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID roomTypeId,
            @RequestParam(required = false) String channel,
            HttpServletRequest request) {
        return synchronous(
                jwt, from, to, roomTypeId, channel, ExportFormat.PDF, request);
    }

    @PostMapping("/exports")
    ExportJob createExport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExportBody body,
            HttpServletRequest request) {
        return exports.create(
                actor(jwt),
                idempotencyKey,
                new ExportRequest(
                        body.from(),
                        body.to(),
                        body.roomTypeId(),
                        body.channel(),
                        body.format()),
                MDC.get("correlationId"),
                clientIp(request));
    }

    @GetMapping("/exports/{exportId}")
    ExportJob exportStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID exportId) {
        return exports.required(exportId, actor(jwt));
    }

    @GetMapping("/exports/{exportId}/download")
    ResponseEntity<byte[]> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID exportId,
            HttpServletRequest request) {
        RenderedReport file = exports.download(exportId, actor(jwt));
        audit.append(new AuditRecord(
                null,
                actor(jwt),
                "REPORT_EXPORT_DOWNLOADED",
                "ReportExport",
                exportId.toString(),
                "reporting-audit-service",
                MDC.get("correlationId"),
                clientIp(request),
                null,
                null,
                file.fileName(),
                Instant.now()));
        return file(file);
    }

    private ResponseEntity<byte[]> synchronous(
            Jwt jwt,
            LocalDate from,
            LocalDate to,
            UUID roomTypeId,
            String channel,
            ExportFormat format,
            HttpServletRequest request) {
        RenderedReport report = renderer.render(
                reports.dashboard(from, to, roomTypeId, channel), format);
        logExportAccess(
                jwt, "REPORT_EXPORTED_" + format, from, to, roomTypeId, channel, request);
        return file(report);
    }

    private static ResponseEntity<byte[]> file(RenderedReport report) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.fileName() + "\"")
                .contentType(MediaType.parseMediaType(report.contentType()))
                .body(report.content());
    }

    private void logExportAccess(
            Jwt jwt,
            String action,
            LocalDate from,
            LocalDate to,
            UUID roomTypeId,
            String channel,
            HttpServletRequest request) {
        audit.append(new AuditRecord(
                null,
                actor(jwt),
                action,
                "Report",
                from + ":" + to,
                "reporting-audit-service",
                MDC.get("correlationId"),
                clientIp(request),
                null,
                objectMapper.valueToTree(Map.of(
                        "from", from,
                        "to", to,
                        "roomTypeId", roomTypeId == null ? "" : roomTypeId,
                        "channel", channel == null ? "" : channel)),
                null,
                Instant.now()));
    }

    static UUID actor(Jwt jwt) {
        String value = jwt.getClaimAsString("uid");
        if (value == null) value = jwt.getSubject();
        return UUID.fromString(value);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record ExportBody(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            UUID roomTypeId,
            String channel,
            @NotNull ExportFormat format) {}
}
