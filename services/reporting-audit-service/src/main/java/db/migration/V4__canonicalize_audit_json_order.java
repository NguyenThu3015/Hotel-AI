package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Rebuilds the chain using order-independent canonical JSON for JSONB columns. */
public class V4__canonicalize_audit_json_order extends BaseJavaMigration {
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
                UPDATE audit_logs SET previous_hash = ?, integrity_hash = ? WHERE id = ?
                """);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long sequence = rows.getLong("sequence_no");
                UUID id = rows.getObject("id", UUID.class);
                String hash = sha256(canonical(
                        objectMapper, rows, sequence, id, previousHash));
                update.setString(1, previousHash);
                update.setString(2, hash);
                update.setObject(3, id);
                update.addBatch();
                previousHash = hash;
                lastSequence = sequence;
            }
            update.executeBatch();
        }

        try (PreparedStatement state = context.getConnection().prepareStatement("""
                UPDATE audit_chain_state SET last_sequence = ?, last_hash = ? WHERE id = 1
                """)) {
            state.setLong(1, lastSequence);
            state.setString(2, previousHash);
            state.executeUpdate();
        }
    }

    private static String canonical(
            ObjectMapper mapper,
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
        fields.put("before", canonicalJson(jsonNode(mapper, row.getString("before_state")), mapper));
        fields.put("after", canonicalJson(jsonNode(mapper, row.getString("after_state")), mapper));
        fields.put("details", nullableText(row, "details"));
        Timestamp occurredAt = row.getTimestamp("occurred_at");
        fields.put("occurredAt", occurredAt.toInstant().toString());
        fields.put("previousHash", previousHash);
        return mapper.writeValueAsString(mapper.valueToTree(fields));
    }

    private static JsonNode canonicalJson(JsonNode value, ObjectMapper mapper) {
        if (value == null || value.isNull()) return mapper.getNodeFactory().nullNode();
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            var properties = new ArrayList<>(value.properties());
            properties.sort(Comparator.comparing(Map.Entry::getKey));
            properties.forEach(property -> result.set(
                    property.getKey(), canonicalJson(property.getValue(), mapper)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonicalJson(item, mapper)));
            return result;
        }
        return value.deepCopy();
    }

    private static UUID uuid(ResultSet row, String column) throws Exception {
        return row.getObject(column, UUID.class);
    }

    private static String uuidText(ResultSet row, String column) throws Exception {
        UUID value = uuid(row, column);
        return value == null ? "" : value.toString();
    }

    private static String nullableText(ResultSet row, String column) throws Exception {
        String value = row.getString(column);
        return value == null ? "" : value;
    }

    private static JsonNode jsonNode(ObjectMapper mapper, String value) throws Exception {
        return value == null ? mapper.getNodeFactory().nullNode() : mapper.readTree(value);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
