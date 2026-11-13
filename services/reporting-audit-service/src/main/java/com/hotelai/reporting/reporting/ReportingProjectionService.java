package com.hotelai.reporting.reporting;

import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditLedgerService.AuditRecord;
import com.hotelai.reporting.messaging.DomainEventConsumer.EventEnvelope;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReportingProjectionService {
    private static final List<String> ACTOR_FIELDS = List.of(
            "actorId", "createdBy", "createdByIdentityId", "approvedBy",
            "decidedBy", "recordedBy", "changedBy", "respondedBy", "requestedBy");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditLedgerService audit;

    public ReportingProjectionService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, AuditLedgerService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public void ingest(EventEnvelope envelope) {
        Integer seen = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class,
                envelope.eventId());
        if (seen != null && seen > 0) return;

        Instant occurredAt = envelope.occurredAt() == null
                ? Instant.now() : envelope.occurredAt();
        JsonNode safePayload = audit.redact(envelope.payload());
        jdbc.update("""
                INSERT INTO domain_event_facts(
                    event_id, event_type, producer, aggregate_type,
                    aggregate_id, correlation_id, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                envelope.eventId(),
                envelope.eventType(),
                envelope.producer(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.correlationId(),
                timestamp(occurredAt),
                json(safePayload));

        project(envelope, occurredAt);
        JsonNode beforeState = child(envelope.payload(), "before", "previousState");
        JsonNode afterState = child(envelope.payload(), "after", "currentState", "snapshot");
        audit.append(new AuditRecord(
                envelope.eventId(),
                actor(envelope.payload()),
                action(envelope.eventType()),
                value(envelope.aggregateType(), firstSegment(envelope.eventType())),
                envelope.aggregateId() == null ? null : envelope.aggregateId().toString(),
                envelope.producer(),
                envelope.correlationId(),
                null,
                beforeState,
                afterState == null ? envelope.payload() : afterState,
                text(envelope.payload(), "reason", "message", "status"),
                occurredAt));

        jdbc.update(
                "INSERT INTO processed_events(event_id, event_type, producer, processed_at) VALUES (?, ?, ?, ?)",
                envelope.eventId(),
                envelope.eventType(),
                envelope.producer(),
                timestamp(Instant.now()));
    }

    private void project(EventEnvelope envelope, Instant occurredAt) {
        String type = envelope.eventType();
        JsonNode payload = envelope.payload();
        if (type.startsWith("reservation.")) {
            projectReservation(payload, occurredAt);
        } else if (type.startsWith("payment.refund.")
                || type.startsWith("payment.refund-")
                || type.startsWith("refund.")) {
            projectRefund(payload, occurredAt);
        } else if (type.startsWith("payment.")) {
            projectPayment(payload, occurredAt);
        } else if (type.startsWith("inventory.room-type.")) {
            projectRoomType(payload, occurredAt);
        } else if (type.startsWith("inventory.room.")) {
            projectRoom(payload, occurredAt);
        } else if (type.startsWith("hotel-service.usage-")) {
            projectServiceUsage(payload, occurredAt);
        } else if (type.startsWith("review.")) {
            projectReview(payload, occurredAt);
        }
    }

    private void projectReservation(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "reservationId");
        if (id == null) return;
        String status = text(value, "status");
        if (status == null) status = "UNKNOWN";
        Instant cancelledAt = instant(value, "cancelledAt");
        if (cancelledAt == null && List.of("CANCELLED", "REJECTED", "NO_SHOW")
                .contains(status.toUpperCase(Locale.ROOT))) {
            cancelledAt = occurredAt;
        }
        jdbc.update("""
                INSERT INTO reservation_facts(
                    reservation_id, reference, customer_id, channel, status,
                    room_type_id, check_in, check_out, room_count, currency,
                    total_amount, paid_amount, created_at, cancelled_at,
                    updated_at, source_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (reservation_id) DO UPDATE SET
                    reference = COALESCE(EXCLUDED.reference, reservation_facts.reference),
                    customer_id = COALESCE(EXCLUDED.customer_id, reservation_facts.customer_id),
                    channel = CASE WHEN EXCLUDED.channel = 'UNKNOWN'
                        THEN reservation_facts.channel ELSE EXCLUDED.channel END,
                    status = EXCLUDED.status,
                    room_type_id = COALESCE(EXCLUDED.room_type_id, reservation_facts.room_type_id),
                    check_in = COALESCE(EXCLUDED.check_in, reservation_facts.check_in),
                    check_out = COALESCE(EXCLUDED.check_out, reservation_facts.check_out),
                    room_count = CASE WHEN EXCLUDED.room_count = 0
                        THEN reservation_facts.room_count ELSE EXCLUDED.room_count END,
                    currency = COALESCE(EXCLUDED.currency, reservation_facts.currency),
                    total_amount = EXCLUDED.total_amount,
                    paid_amount = EXCLUDED.paid_amount,
                    cancelled_at = COALESCE(EXCLUDED.cancelled_at, reservation_facts.cancelled_at),
                    updated_at = EXCLUDED.updated_at,
                    source_version = EXCLUDED.source_version
                WHERE reservation_facts.source_version <= EXCLUDED.source_version
                """,
                id,
                text(value, "reference", "reservationReference"),
                uuid(value, "customerId"),
                value(text(value, "channel", "source"), "UNKNOWN"),
                status,
                uuid(value, "roomTypeId"),
                date(value, "checkIn", "scheduledCheckIn"),
                date(value, "checkOut", "scheduledCheckOut"),
                integer(value, "roomCount"),
                text(value, "currency"),
                decimal(value, "totalAmount"),
                decimal(value, "paidAmount"),
                timestamp(value(instant(value, "createdAt"), occurredAt)),
                timestamp(cancelledAt),
                timestamp(value(instant(value, "updatedAt"), occurredAt)),
                longValue(value, "version"));
    }

    private void projectPayment(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "paymentId");
        if (id == null) return;
        jdbc.update("""
                INSERT INTO payment_facts(
                    payment_id, reservation_id, amount, refunded_amount,
                    currency, status, provider, occurred_at, updated_at)
                VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?)
                ON CONFLICT (payment_id) DO UPDATE SET
                    reservation_id = COALESCE(EXCLUDED.reservation_id, payment_facts.reservation_id),
                    amount = EXCLUDED.amount,
                    currency = COALESCE(EXCLUDED.currency, payment_facts.currency),
                    status = EXCLUDED.status,
                    provider = COALESCE(EXCLUDED.provider, payment_facts.provider),
                    occurred_at = EXCLUDED.occurred_at,
                    updated_at = EXCLUDED.updated_at
                """,
                id,
                uuid(value, "reservationId"),
                decimal(value, "amount"),
                text(value, "currency"),
                value(text(value, "status"), "UNKNOWN"),
                text(value, "provider"),
                timestamp(value(instant(value, "completedAt"), occurredAt)),
                timestamp(occurredAt));
    }

    private void projectRefund(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "refundId");
        if (id == null) return;
        jdbc.update("""
                INSERT INTO refund_facts(
                    refund_id, payment_id, reservation_id, amount, currency,
                    status, occurred_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (refund_id) DO UPDATE SET
                    payment_id = COALESCE(EXCLUDED.payment_id, refund_facts.payment_id),
                    reservation_id = COALESCE(EXCLUDED.reservation_id, refund_facts.reservation_id),
                    amount = EXCLUDED.amount,
                    currency = COALESCE(EXCLUDED.currency, refund_facts.currency),
                    status = EXCLUDED.status,
                    occurred_at = EXCLUDED.occurred_at,
                    updated_at = EXCLUDED.updated_at
                """,
                id,
                uuid(value, "paymentId"),
                uuid(value, "reservationId"),
                decimal(value, "amount"),
                text(value, "currency"),
                value(text(value, "status"), "UNKNOWN"),
                timestamp(occurredAt),
                timestamp(occurredAt));
    }

    private void projectRoomType(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "roomTypeId");
        if (id == null) return;
        jdbc.update("""
                INSERT INTO room_type_facts(
                    room_type_id, code, name, active, source_version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (room_type_id) DO UPDATE SET
                    code = COALESCE(EXCLUDED.code, room_type_facts.code),
                    name = COALESCE(EXCLUDED.name, room_type_facts.name),
                    active = EXCLUDED.active,
                    source_version = EXCLUDED.source_version,
                    updated_at = EXCLUDED.updated_at
                WHERE room_type_facts.source_version <= EXCLUDED.source_version
                """,
                id,
                text(value, "code"),
                text(value, "name"),
                bool(value, "active", true),
                longValue(value, "version"),
                timestamp(occurredAt));
    }

    private void projectRoom(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "roomId");
        if (id == null) return;
        jdbc.update("""
                INSERT INTO room_facts(
                    room_id, room_type_id, room_number, status, active,
                    source_version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (room_id) DO UPDATE SET
                    room_type_id = COALESCE(EXCLUDED.room_type_id, room_facts.room_type_id),
                    room_number = COALESCE(EXCLUDED.room_number, room_facts.room_number),
                    status = COALESCE(EXCLUDED.status, room_facts.status),
                    active = EXCLUDED.active,
                    source_version = EXCLUDED.source_version,
                    updated_at = EXCLUDED.updated_at
                WHERE room_facts.source_version <= EXCLUDED.source_version
                """,
                id,
                uuid(value, "roomTypeId"),
                text(value, "roomNumber"),
                text(value, "status", "newStatus"),
                bool(value, "active", true),
                longValue(value, "version"),
                timestamp(occurredAt));
    }

    private void projectServiceUsage(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "usageId");
        if (id == null) return;
        int revision = integer(value, "revision");
        jdbc.update("""
                INSERT INTO service_usage_facts(
                    usage_id, stay_id, reservation_id, service_id, service_code,
                    service_name, quantity, gross_amount, currency, status,
                    occurred_at, source_revision, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (usage_id) DO UPDATE SET
                    stay_id = COALESCE(EXCLUDED.stay_id, service_usage_facts.stay_id),
                    reservation_id = COALESCE(EXCLUDED.reservation_id, service_usage_facts.reservation_id),
                    service_id = COALESCE(EXCLUDED.service_id, service_usage_facts.service_id),
                    service_code = COALESCE(EXCLUDED.service_code, service_usage_facts.service_code),
                    service_name = COALESCE(EXCLUDED.service_name, service_usage_facts.service_name),
                    quantity = EXCLUDED.quantity,
                    gross_amount = EXCLUDED.gross_amount,
                    currency = COALESCE(EXCLUDED.currency, service_usage_facts.currency),
                    status = EXCLUDED.status,
                    occurred_at = EXCLUDED.occurred_at,
                    source_revision = EXCLUDED.source_revision,
                    updated_at = EXCLUDED.updated_at
                WHERE service_usage_facts.source_revision <= EXCLUDED.source_revision
                """,
                id,
                uuid(value, "stayId"),
                uuid(value, "reservationId"),
                uuid(value, "serviceId"),
                text(value, "serviceCode"),
                text(value, "serviceName"),
                decimal(value, "quantity"),
                decimal(value, "grossAmount"),
                text(value, "currency"),
                value(text(value, "status"), "UNKNOWN"),
                timestamp(value(instant(value, "occurredAt"), occurredAt)),
                revision,
                timestamp(occurredAt));
    }

    private void projectReview(JsonNode value, Instant occurredAt) {
        UUID id = uuid(value, "reviewId");
        if (id == null) return;
        JsonNode topics = value.get("topics");
        if (topics == null || !topics.isArray()) topics = objectMapper.createArrayNode();
        jdbc.update("""
                INSERT INTO review_facts(
                    review_id, rating, status, topics, created_at,
                    published_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                ON CONFLICT (review_id) DO UPDATE SET
                    rating = EXCLUDED.rating,
                    status = EXCLUDED.status,
                    topics = EXCLUDED.topics,
                    published_at = COALESCE(EXCLUDED.published_at, review_facts.published_at),
                    updated_at = EXCLUDED.updated_at
                """,
                id,
                integer(value, "rating"),
                value(text(value, "status"), "UNKNOWN"),
                json(topics),
                timestamp(value(instant(value, "createdAt"), occurredAt)),
                timestamp(instant(value, "publishedAt")),
                timestamp(occurredAt));
    }

    private UUID actor(JsonNode value) {
        for (String field : ACTOR_FIELDS) {
            UUID actor = uuid(value, field);
            if (actor != null) return actor;
        }
        return null;
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize reporting payload", error);
        }
    }

    private static JsonNode child(JsonNode value, String... fields) {
        for (String field : fields) {
            JsonNode child = value.get(field);
            if (child != null && !child.isNull()) return child;
        }
        return null;
    }

    private static UUID uuid(JsonNode value, String field) {
        String text = text(value, field);
        if (text == null || text.isBlank()) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(JsonNode value, String... fields) {
        for (String field : fields) {
            JsonNode child = value.get(field);
            if (child != null && !child.isNull() && child.isValueNode()) {
                String text = child.asText();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private static Instant instant(JsonNode value, String field) {
        String text = text(value, field);
        if (text == null) return null;
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Date date(JsonNode value, String... fields) {
        String text = text(value, fields);
        if (text == null) return null;
        try {
            return Date.valueOf(LocalDate.parse(text));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode value, String field) {
        JsonNode child = value.get(field);
        if (child == null || child.isNull()) return BigDecimal.ZERO;
        try {
            return child.decimalValue();
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static int integer(JsonNode value, String field) {
        JsonNode child = value.get(field);
        return child == null || !child.canConvertToInt() ? 0 : child.asInt();
    }

    private static long longValue(JsonNode value, String field) {
        JsonNode child = value.get(field);
        return child == null || !child.canConvertToLong() ? 0 : child.asLong();
    }

    private static boolean bool(JsonNode value, String field, boolean fallback) {
        JsonNode child = value.get(field);
        return child == null || !child.isBoolean() ? fallback : child.asBoolean();
    }

    private static String action(String eventType) {
        return eventType.toUpperCase(Locale.ROOT)
                .replaceAll("\\.V\\d+$", "")
                .replace('.', '_')
                .replace('-', '_');
    }

    private static String firstSegment(String eventType) {
        int dot = eventType.indexOf('.');
        return dot < 0 ? eventType : eventType.substring(0, dot);
    }

    private static <T> T value(T candidate, T fallback) {
        return candidate == null ? fallback : candidate;
    }
}
