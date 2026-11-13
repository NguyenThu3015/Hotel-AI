package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Rebuilds the existing audit chain from the persisted representation.
 *
 * <p>Versions before V3 hashed nanosecond timestamps and then stored them in PostgreSQL, which
 * retains microsecond precision. Records created directly by the reporting service therefore
 * could not be verified after being read back. Rebuilding is safe here because the row contents
 * stay unchanged; only the derived previous/integrity hashes are replaced.
 */
public class V3__normalize_audit_hash_timestamps extends BaseJavaMigration {
    private static final String ZERO_HASH = "0".repeat(64);

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String previousHash = ZERO_HASH;
        long lastSequence = 0;

        try (PreparedStatement select = context.getConnection().prepareStatement("""
                SELECT id, sequence_no, source_event_id, actor_id, action,
                       entity_type, entity_id, producer, correlation_id,
                       ip_address, before_state, after_state, details, occurred_at
                FROM audit_logs
                ORDER BY sequence_no
                FOR UPDATE
                """);
             PreparedStatement update = context.getConnection().prepareStatement("""
                UPDATE audit_logs
                SET previous_hash = ?, integrity_hash = ?
                WHERE id = ?
                """);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long sequence = rows.getLong("sequence_no");
                UUID id = rows.getObject("id", UUID.class);
                String canonical = canonical(objectMapper, rows, sequence, id, previousHash);
                String integrityHash = sha256(canonical);

                update.setString(1, previousHash);
                update.setString(2, integrityHash);
                update.setObject(3, id);
                update.addBatch();

                previousHash = integrityHash;
                lastSequence = sequence;
            }
            update.executeBatch();
        }

        try (PreparedStatement updateState = context.getConnection().prepareStatement("""
                UPDATE audit_chain_state
                SET last_sequence = ?, last_hash = ?
                WHERE id = 1
                """)) {
            updateState.setLong(1, lastSequence);
            updateState.setString(2, previousHash);
            updateState.executeUpdate();
        }
    }

    private static String canonical(
            ObjectMapper objectMapper,
            ResultSet row,
            long sequence,
            UUID id,
            String previousHash) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sequence", sequence);
        fields.put("id", id.toString());
        fields.put("sourceEventId", uuidText(row, "source_event_id"));
        fields.put("actorId", uuidText(row, "actor_id"));
        fields.put("action", row.getString("action").trim());
        fields.put("entityType", row.getString("entity_type").trim());
        fields.put("entityId", nullableText(row, "entity_id"));
        fields.put("producer", row.getString("producer").trim());
        fields.put("correlationId", nullableText(row, "correlation_id"));
        fields.put("ipAddress", nullableText(row, "ip_address"));
        fields.put("before", jsonNode(objectMapper, row.getString("before_state")));
        fields.put("after", jsonNode(objectMapper, row.getString("after_state")));
        fields.put("details", nullableText(row, "details"));
        Timestamp occurredAt = row.getTimestamp("occurred_at");
        fields.put("occurredAt", occurredAt.toInstant().toString());
        fields.put("previousHash", previousHash);
        return objectMapper.writeValueAsString(objectMapper.valueToTree(fields));
    }

    private static String uuidText(ResultSet row, String column) throws Exception {
        UUID value = row.getObject(column, UUID.class);
        return value == null ? "" : value.toString();
    }

    private static String nullableText(ResultSet row, String column) throws Exception {
        String value = row.getString(column);
        return value == null ? "" : value;
    }

    private static JsonNode jsonNode(ObjectMapper objectMapper, String value) throws Exception {
        return value == null
                ? objectMapper.getNodeFactory().nullNode()
                : objectMapper.readTree(value);
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(
                digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
