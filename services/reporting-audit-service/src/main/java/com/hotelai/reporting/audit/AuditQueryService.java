package com.hotelai.reporting.audit;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditPage search(
            Instant from,
            Instant to,
            UUID actorId,
            String action,
            String entityType,
            String entityId,
            int page,
            int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Filter filter = filter(from, to, actorId, action, entityType, entityId);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE 1=1 " + filter.sql(),
                Long.class,
                filter.parameters().toArray());
        List<Object> values = new ArrayList<>(filter.parameters());
        values.add(safeSize);
        values.add((long) safePage * safeSize);
        List<AuditView> content = jdbc.query(
                """
                SELECT id, sequence_no, actor_id, action, entity_type,
                       entity_id, producer, correlation_id, ip_address,
                       before_state, after_state, details, occurred_at,
                       recorded_at, previous_hash, integrity_hash
                FROM audit_logs
                WHERE 1=1
                """ + filter.sql() + """
                ORDER BY occurred_at DESC, sequence_no DESC
                LIMIT ? OFFSET ?
                """,
                (result, row) -> new AuditView(
                        result.getObject("id", UUID.class),
                        result.getLong("sequence_no"),
                        result.getObject("actor_id", UUID.class),
                        result.getString("action"),
                        result.getString("entity_type"),
                        result.getString("entity_id"),
                        result.getString("producer"),
                        result.getString("correlation_id"),
                        result.getString("ip_address"),
                        tree(result.getString("before_state")),
                        tree(result.getString("after_state")),
                        result.getString("details"),
                        result.getTimestamp("occurred_at").toInstant(),
                        result.getTimestamp("recorded_at").toInstant(),
                        result.getString("previous_hash"),
                        result.getString("integrity_hash")),
                values.toArray());
        long total = count == null ? 0 : count;
        return new AuditPage(
                content,
                safePage,
                safeSize,
                total,
                (int) Math.ceil(total / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            Instant from,
            Instant to,
            UUID actorId,
            String action,
            String entityType,
            String entityId) {
        Filter filter = filter(from, to, actorId, action, entityType, entityId);
        StringBuilder csv = new StringBuilder(
                "\uFEFFsequence,occurredAt,actorId,action,entityType,entityId,"
                        + "producer,correlationId,details,integrityHash\r\n");
        jdbc.query(
                """
                SELECT sequence_no, occurred_at, actor_id, action, entity_type,
                       entity_id, producer, correlation_id, details, integrity_hash
                FROM audit_logs
                WHERE 1=1
                """ + filter.sql() + """
                ORDER BY sequence_no
                """,
                result -> {
                    append(csv, result.getString("sequence_no"));
                    append(csv, result.getString("occurred_at"));
                    append(csv, result.getString("actor_id"));
                    append(csv, result.getString("action"));
                    append(csv, result.getString("entity_type"));
                    append(csv, result.getString("entity_id"));
                    append(csv, result.getString("producer"));
                    append(csv, result.getString("correlation_id"));
                    append(csv, result.getString("details"));
                    last(csv, result.getString("integrity_hash"));
                },
                filter.parameters().toArray());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Filter filter(
            Instant from,
            Instant to,
            UUID actorId,
            String action,
            String entityType,
            String entityId) {
        StringBuilder sql = new StringBuilder();
        List<Object> values = new ArrayList<>();
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            values.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at < ?");
            values.add(Timestamp.from(to));
        }
        if (actorId != null) {
            sql.append(" AND actor_id = ?");
            values.add(actorId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            values.add(action.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND LOWER(entity_type) = LOWER(?)");
            values.add(entityType.trim());
        }
        if (entityId != null && !entityId.isBlank()) {
            sql.append(" AND entity_id = ?");
            values.add(entityId.trim());
        }
        return new Filter(sql.toString(), values);
    }

    private JsonNode tree(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception error) {
            throw new IllegalStateException("Stored audit JSON is invalid", error);
        }
    }

    private static void append(StringBuilder csv, String value) {
        csv.append(cell(value)).append(',');
    }

    private static void last(StringBuilder csv, String value) {
        csv.append(cell(value)).append("\r\n");
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private record Filter(String sql, List<Object> parameters) {}

    public record AuditPage(
            List<AuditView> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    public record AuditView(
            UUID id,
            long sequence,
            UUID actorId,
            String action,
            String entityType,
            String entityId,
            String producer,
            String correlationId,
            String ipAddress,
            JsonNode beforeState,
            JsonNode afterState,
            String details,
            Instant occurredAt,
            Instant createdAt,
            String previousHash,
            String integrityHash) {}
}
