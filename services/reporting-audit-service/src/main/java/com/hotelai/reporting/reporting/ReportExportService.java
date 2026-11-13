package com.hotelai.reporting.reporting;

import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditLedgerService.AuditRecord;
import com.hotelai.reporting.common.ApiException;
import com.hotelai.reporting.reporting.ReportFileRenderer.ExportFormat;
import com.hotelai.reporting.reporting.ReportFileRenderer.RenderedReport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReportExportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ReportingQueryService reports;
    private final ReportFileRenderer renderer;
    private final AuditLedgerService audit;

    public ReportExportService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ReportingQueryService reports,
            ReportFileRenderer renderer,
            AuditLedgerService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.reports = reports;
        this.renderer = renderer;
        this.audit = audit;
    }

    @Transactional
    public ExportJob create(
            UUID actorId,
            String idempotencyKey,
            ExportRequest request,
            String correlationId,
            String ipAddress) {
        requireKey(idempotencyKey);
        reports.range(request.from(), request.to());
        String requestHash = AuditLedgerService.sha256(
                actorId + "|" + request.format() + "|" + request.from() + "|"
                        + request.to() + "|" + request.roomTypeId() + "|"
                        + request.channel());
        ExportJob replay = findByKey(idempotencyKey);
        if (replay != null) {
            if (!replay.requestHash().equals(requestHash)) {
                throw new ApiException(
                        409,
                        "idempotency-conflict",
                        "Idempotency-Key was already used for another export");
            }
            return replay;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_export_jobs(
                    id, requested_by, format, date_from, date_to, filters,
                    status, created_at, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, ?, ?)
                """,
                id,
                actorId,
                request.format().name(),
                Date.valueOf(request.from()),
                Date.valueOf(request.to()),
                json(Map.of(
                        "roomTypeId", request.roomTypeId() == null
                                ? "" : request.roomTypeId().toString(),
                        "channel", request.channel() == null ? "" : request.channel())),
                Timestamp.from(Instant.now()),
                idempotencyKey,
                requestHash);
        audit.append(new AuditRecord(
                null,
                actorId,
                "REPORT_EXPORT_REQUESTED",
                "ReportExport",
                id.toString(),
                "reporting-audit-service",
                correlationId,
                ipAddress,
                null,
                objectMapper.valueToTree(Map.of(
                        "format", request.format(),
                        "from", request.from(),
                        "to", request.to())),
                "Background report export requested",
                Instant.now()));
        return required(id, actorId);
    }

    @Transactional(readOnly = true)
    public ExportJob required(UUID id, UUID actorId) {
        List<ExportJob> values = jdbc.query(
                "SELECT * FROM report_export_jobs WHERE id = ? AND requested_by = ?",
                (result, row) -> row(result),
                id,
                actorId);
        if (values.isEmpty()) throw ApiException.notFound("Report export was not found");
        return values.getFirst();
    }

    @Transactional(readOnly = true)
    public RenderedReport download(UUID id, UUID actorId) {
        ExportJob job = required(id, actorId);
        if (!"COMPLETED".equals(job.status())) {
            throw new ApiException(
                    409, "report-export-not-ready", "Report export is not completed");
        }
        byte[] content = jdbc.queryForObject(
                "SELECT content FROM report_export_jobs WHERE id = ?",
                byte[].class,
                id);
        return new RenderedReport(content, job.contentType(), job.fileName());
    }

    @Scheduled(fixedDelayString = "${hotel.reporting.export-worker-delay-ms:1000}")
    @Transactional
    public void processPending() {
        List<UUID> pending = jdbc.query(
                """
                SELECT id FROM report_export_jobs
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT 5
                FOR UPDATE SKIP LOCKED
                """,
                (result, row) -> result.getObject("id", UUID.class));
        for (UUID id : pending) {
            jdbc.update(
                    "UPDATE report_export_jobs SET status = 'PROCESSING' WHERE id = ?",
                    id);
            try {
                ExportJob job = find(id);
                ReportingQueryService.Dashboard dashboard = reports.dashboard(
                        job.from(), job.to(), job.roomTypeId(), job.channel());
                RenderedReport rendered = renderer.render(dashboard, job.format());
                jdbc.update("""
                        UPDATE report_export_jobs
                        SET status = 'COMPLETED', file_name = ?, content_type = ?,
                            content = ?, completed_at = ?
                        WHERE id = ?
                        """,
                        rendered.fileName(),
                        rendered.contentType(),
                        rendered.content(),
                        Timestamp.from(Instant.now()),
                        id);
            } catch (Exception error) {
                jdbc.update("""
                        UPDATE report_export_jobs
                        SET status = 'FAILED', error = ?, completed_at = ?
                        WHERE id = ?
                        """,
                        truncate(error.getMessage(), 2000),
                        Timestamp.from(Instant.now()),
                        id);
            }
        }
    }

    private ExportJob findByKey(String key) {
        List<ExportJob> values = jdbc.query(
                "SELECT * FROM report_export_jobs WHERE idempotency_key = ?",
                (result, row) -> row(result),
                key);
        return values.isEmpty() ? null : values.getFirst();
    }

    private ExportJob find(UUID id) {
        return jdbc.queryForObject(
                "SELECT * FROM report_export_jobs WHERE id = ?",
                (result, row) -> row(result),
                id);
    }

    private ExportJob row(java.sql.ResultSet result) throws java.sql.SQLException {
        String filters = result.getString("filters");
        String roomType = "";
        String channel = "";
        try {
            var tree = objectMapper.readTree(filters);
            roomType = tree.path("roomTypeId").asText("");
            channel = tree.path("channel").asText("");
        } catch (Exception error) {
            throw new IllegalStateException("Stored report filters are invalid", error);
        }
        Timestamp completed = result.getTimestamp("completed_at");
        return new ExportJob(
                result.getObject("id", UUID.class),
                result.getObject("requested_by", UUID.class),
                ExportFormat.valueOf(result.getString("format")),
                result.getDate("date_from").toLocalDate(),
                result.getDate("date_to").toLocalDate(),
                roomType.isBlank() ? null : UUID.fromString(roomType),
                channel.isBlank() ? null : channel,
                result.getString("status"),
                result.getString("file_name"),
                result.getString("content_type"),
                result.getString("error"),
                result.getTimestamp("created_at").toInstant(),
                completed == null ? null : completed.toInstant(),
                result.getString("request_hash"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize report filters", error);
        }
    }

    private static void requireKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw ApiException.badRequest(
                    "invalid-idempotency-key",
                    "Idempotency-Key must contain 1-160 characters");
        }
    }

    private static String truncate(String value, int length) {
        if (value == null) return "Report generation failed";
        return value.length() <= length ? value : value.substring(0, length);
    }

    public record ExportRequest(
            LocalDate from,
            LocalDate to,
            UUID roomTypeId,
            String channel,
            ExportFormat format) {}

    public record ExportJob(
            UUID id,
            UUID requestedBy,
            ExportFormat format,
            LocalDate from,
            LocalDate to,
            UUID roomTypeId,
            String channel,
            String status,
            String fileName,
            String contentType,
            String error,
            Instant createdAt,
            Instant completedAt,
            String requestHash) {}
}
