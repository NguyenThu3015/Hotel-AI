package com.hotelai.reporting.audit;

import com.hotelai.reporting.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AuditLedgerService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditRedactor redactor;

    public AuditLedgerService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, AuditRedactor redactor) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
    }

    @Transactional
    public UUID append(AuditRecord command) {
        if (command.sourceEventId() != null) {
            UUID existing = jdbc.query(
                    "SELECT id FROM audit_logs WHERE source_event_id = ?",
                    result -> result.next()
                            ? result.getObject("id", UUID.class) : null,
                    command.sourceEventId());
            if (existing != null) return existing;
        }

        ChainState state = jdbc.queryForObject(
                "SELECT last_sequence, last_hash FROM audit_chain_state WHERE id = 1 FOR UPDATE",
                (result, row) -> new ChainState(
                        result.getLong("last_sequence"),
                        result.getString("last_hash")));
        if (state == null) throw new IllegalStateException("Audit chain state is missing");
        if (command.sourceEventId() != null) {
            UUID existing = jdbc.query(
                    "SELECT id FROM audit_logs WHERE source_event_id = ?",
                    result -> result.next()
                            ? result.getObject("id", UUID.class) : null,
                    command.sourceEventId());
            if (existing != null) return existing;
        }

        long sequence = state.sequence() + 1;
        UUID id = UUID.randomUUID();
        Instant occurredAt = (command.occurredAt() == null
                ? Instant.now() : command.occurredAt()).truncatedTo(ChronoUnit.MICROS);
        JsonNode before = redactor.redact(command.beforeState());
        JsonNode after = redactor.redact(command.afterState());
        String details = redactor.maskText(command.details());
        String canonical = canonical(
                sequence, id, command, occurredAt, before, after, details, state.hash());
        String integrityHash = sha256(canonical);

        jdbc.update("""
                INSERT INTO audit_logs(
                    id, sequence_no, source_event_id, actor_id, action,
                    entity_type, entity_id, producer, correlation_id,
                    ip_address, before_state, after_state, details,
                    occurred_at, recorded_at, previous_hash, integrity_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb),
                    CAST(? AS jsonb), ?, ?, ?, ?, ?)
                """,
                id,
                sequence,
                command.sourceEventId(),
                command.actorId(),
                required(command.action(), "action"),
                required(command.entityType(), "entityType"),
                command.entityId(),
                required(command.producer(), "producer"),
                command.correlationId(),
                command.ipAddress(),
                json(before),
                json(after),
                details,
                Timestamp.from(occurredAt),
                Timestamp.from(Instant.now()),
                state.hash(),
                integrityHash);
        jdbc.update(
                "UPDATE audit_chain_state SET last_sequence = ?, last_hash = ? WHERE id = 1",
                sequence,
                integrityHash);
        return id;
    }

    @Transactional(readOnly = true)
    public IntegrityResult verifyIntegrity() {
        String expectedPrevious = "0".repeat(64);
        long checked = 0;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT * FROM audit_logs ORDER BY sequence_no")) {
            String previous = String.valueOf(row.get("previous_hash"));
            String actual = String.valueOf(row.get("integrity_hash"));
            if (!expectedPrevious.equals(previous)) {
                return new IntegrityResult(false, checked, "previous-hash-mismatch");
            }
            AuditRecord value = new AuditRecord(
                    asUuid(row.get("source_event_id")),
                    asUuid(row.get("actor_id")),
                    String.valueOf(row.get("action")),
                    String.valueOf(row.get("entity_type")),
                    nullable(row.get("entity_id")),
                    String.valueOf(row.get("producer")),
                    nullable(row.get("correlation_id")),
                    nullable(row.get("ip_address")),
                    tree(row.get("before_state")),
                    tree(row.get("after_state")),
                    nullable(row.get("details")),
                    asInstant(row.get("occurred_at")));
            String computed = sha256(canonical(
                    ((Number) row.get("sequence_no")).longValue(),
                    asUuid(row.get("id")),
                    value,
                    value.occurredAt(),
                    value.beforeState(),
                    value.afterState(),
                    value.details(),
                    previous));
            if (!actual.equals(computed)) {
                return new IntegrityResult(false, checked, "record-hash-mismatch");
            }
            expectedPrevious = actual;
            checked++;
        }
        return new IntegrityResult(true, checked, null);
    }

    public JsonNode redact(JsonNode value) {
        return redactor.redact(value);
    }

    private String canonical(
            long sequence,
            UUID id,
            AuditRecord value,
            Instant occurredAt,
            JsonNode before,
            JsonNode after,
            String details,
            String previousHash) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sequence", sequence);
        fields.put("id", id.toString());
        fields.put("sourceEventId", value.sourceEventId() == null
                ? "" : value.sourceEventId().toString());
        fields.put("actorId", value.actorId() == null
                ? "" : value.actorId().toString());
        fields.put("action", required(value.action(), "action"));
        fields.put("entityType", required(value.entityType(), "entityType"));
        fields.put("entityId", value.entityId() == null ? "" : value.entityId());
        fields.put("producer", required(value.producer(), "producer"));
        fields.put("correlationId", value.correlationId() == null
                ? "" : value.correlationId());
        fields.put("ipAddress", value.ipAddress() == null ? "" : value.ipAddress());
        fields.put("before", canonicalJson(before, objectMapper));
        fields.put("after", canonicalJson(after, objectMapper));
        fields.put("details", details == null ? "" : details);
        fields.put("occurredAt", occurredAt.toString());
        fields.put("previousHash", previousHash);
        return json(objectMapper.valueToTree(fields));
    }

    static JsonNode canonicalJson(JsonNode value, ObjectMapper objectMapper) {
        if (value == null || value.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            var properties = new ArrayList<>(value.properties());
            properties.sort(Comparator.comparing(Map.Entry::getKey));
            properties.forEach(property -> result.set(
                    property.getKey(), canonicalJson(property.getValue(), objectMapper)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalJson(item, objectMapper)));
            return result;
        }
        return value.deepCopy();
    }

    private JsonNode tree(Object value) {
        try {
            if (value == null) return objectMapper.getNodeFactory().nullNode();
            return objectMapper.readTree(String.valueOf(value));
        } catch (Exception error) {
            throw new IllegalStateException("Stored audit JSON is invalid", error);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize audit value", error);
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("audit-field-required", field + " is required");
        }
        return value.trim();
    }

    private static UUID asUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(String.valueOf(value));
    }

    private record ChainState(long sequence, String hash) {}

    public record AuditRecord(
            UUID sourceEventId,
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
            Instant occurredAt) {}

    public record IntegrityResult(boolean valid, long checkedRecords, String failure) {}
}
