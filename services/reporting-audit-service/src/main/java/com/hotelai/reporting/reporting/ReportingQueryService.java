package com.hotelai.reporting.reporting;

import com.hotelai.reporting.common.ApiException;
import com.hotelai.reporting.config.ReportingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingQueryService {
    private static final List<String> OCCUPIED_STATUSES =
            List.of("CONFIRMED", "CHECKED_IN", "CHECKED_OUT");
    private final JdbcTemplate jdbc;
    private final ReportingProperties properties;

    public ReportingQueryService(JdbcTemplate jdbc, ReportingProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(
            LocalDate from, LocalDate to, UUID roomTypeId, String channel) {
        Range range = range(from, to);
        Kpi current = kpi(range, roomTypeId, channel);
        long days = ChronoUnit.DAYS.between(from, to.plusDays(1));
        Range previousRange = range(from.minusDays(days), from.minusDays(1));
        Kpi previous = kpi(previousRange, roomTypeId, channel);
        boolean empty = current.bookings() == 0
                && current.revenue().signum() == 0
                && current.soldRoomNights() == 0;

        return new Dashboard(
                current.revenue(),
                current.serviceRevenue(),
                current.bookings(),
                current.cancellationRate(),
                current.occupancyRate(),
                current.adr(),
                current.revpar(),
                dailyRevenue(range, roomTypeId, channel),
                popularServices(range, roomTypeId, channel),
                popularRoomTypes(range, channel),
                bookingSources(range, roomTypeId),
                new PeriodComparison(
                        previous.revenue(),
                        previous.bookings(),
                        previous.occupancyRate(),
                        percentChange(current.revenue(), previous.revenue()),
                        percentChange(
                                BigDecimal.valueOf(current.bookings()),
                                BigDecimal.valueOf(previous.bookings()))),
                new ReportContext(
                        from,
                        to,
                        properties.timeZone(),
                        properties.currency(),
                        roomTypeId,
                        normalizedChannel(channel),
                        Instant.now()),
                !empty,
                empty ? "Không có giao dịch trong phạm vi đã chọn." : null,
                Map.of(
                        "revenue", "Thanh toán thành công trừ hoàn tiền hoàn tất trong kỳ.",
                        "occupancyRate", "Đêm phòng đã bán / đêm phòng có thể bán.",
                        "adr", "Doanh thu / đêm phòng đã bán.",
                        "revpar", "Doanh thu / đêm phòng có thể bán."));
    }

    public Range range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest(
                    "report-range-required", "from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest(
                    "invalid-report-range", "to must not be before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > properties.maxRangeDays()) {
            throw ApiException.badRequest(
                    "report-range-too-large",
                    "Report range cannot exceed " + properties.maxRangeDays() + " days");
        }
        return new Range(
                from,
                to,
                from.atStartOfDay(properties.zoneId()).toInstant(),
                to.plusDays(1).atStartOfDay(properties.zoneId()).toInstant());
    }

    private Kpi kpi(Range range, UUID roomTypeId, String channel) {
        List<Object> bookingParams = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String bookingFilter = reservationFilters("r", roomTypeId, channel, bookingParams);
        long bookings = number("""
                SELECT COUNT(*) FROM reservation_facts r
                WHERE r.created_at >= ? AND r.created_at < ?
                """ + bookingFilter, bookingParams.toArray()).longValue();
        long cancelled = number("""
                SELECT COUNT(*) FROM reservation_facts r
                WHERE r.created_at >= ? AND r.created_at < ?
                  AND r.status IN ('CANCELLED', 'REJECTED', 'NO_SHOW')
                """ + bookingFilter, bookingParams.toArray()).longValue();

        List<Object> revenueParams = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String revenueFilter = reservationFilters("r", roomTypeId, channel, revenueParams);
        BigDecimal payments = decimal("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM payment_facts p
                LEFT JOIN reservation_facts r ON r.reservation_id = p.reservation_id
                WHERE p.status = 'SUCCEEDED'
                  AND p.occurred_at >= ? AND p.occurred_at < ?
                """ + revenueFilter, revenueParams.toArray());
        List<Object> refundParams = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String refundFilter = reservationFilters("r", roomTypeId, channel, refundParams);
        BigDecimal refunds = decimal("""
                SELECT COALESCE(SUM(f.amount), 0)
                FROM refund_facts f
                LEFT JOIN reservation_facts r ON r.reservation_id = f.reservation_id
                WHERE f.status IN ('COMPLETED', 'SUCCEEDED')
                  AND f.occurred_at >= ? AND f.occurred_at < ?
                """ + refundFilter, refundParams.toArray());
        BigDecimal revenue = payments.subtract(refunds).setScale(2, RoundingMode.HALF_UP);

        List<Object> stayParams = new ArrayList<>(List.of(
                Date.valueOf(range.to().plusDays(1)),
                Date.valueOf(range.from()),
                Date.valueOf(range.to().plusDays(1)),
                Date.valueOf(range.from())));
        String stayFilter = reservationFilters("r", roomTypeId, channel, stayParams);
        long soldRoomNights = number("""
                SELECT COALESCE(SUM(
                    GREATEST(0, LEAST(r.check_out, ?) - GREATEST(r.check_in, ?))
                    * r.room_count), 0)
                FROM reservation_facts r
                WHERE r.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT')
                  AND r.check_in < ? AND r.check_out > ?
                """ + stayFilter,
                stayParams.toArray()).longValue();

        List<Object> roomParams = new ArrayList<>();
        String roomTypeFilter = "";
        if (roomTypeId != null) {
            roomTypeFilter = " AND room_type_id = ?";
            roomParams.add(roomTypeId);
        }
        long activeRooms = number("""
                SELECT COUNT(*) FROM room_facts
                WHERE active = TRUE
                  AND COALESCE(status, 'AVAILABLE') <> 'MAINTENANCE'
                """ + roomTypeFilter, roomParams.toArray()).longValue();
        long capacity = activeRooms
                * Math.max(1, ChronoUnit.DAYS.between(
                        range.from(), range.to().plusDays(1)));

        List<Object> serviceParams = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String serviceFilter = reservationFilters("r", roomTypeId, channel, serviceParams);
        BigDecimal serviceRevenue = decimal("""
                SELECT COALESCE(SUM(s.gross_amount), 0)
                FROM service_usage_facts s
                LEFT JOIN reservation_facts r ON r.reservation_id = s.reservation_id
                WHERE s.status <> 'CANCELLED'
                  AND s.occurred_at >= ? AND s.occurred_at < ?
                """ + serviceFilter, serviceParams.toArray());

        BigDecimal occupancy = percentage(soldRoomNights, capacity);
        BigDecimal adr = soldRoomNights == 0
                ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(
                        BigDecimal.valueOf(soldRoomNights), 2, RoundingMode.HALF_UP);
        BigDecimal revpar = capacity == 0
                ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(
                        BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
        return new Kpi(
                revenue,
                serviceRevenue,
                bookings,
                percentage(cancelled, bookings),
                occupancy,
                adr,
                revpar,
                soldRoomNights,
                capacity);
    }

    private List<Map<String, Object>> dailyRevenue(
            Range range, UUID roomTypeId, String channel) {
        String paymentFilter = reservationFilters(
                "r", roomTypeId, channel, new ArrayList<>());
        String refundFilter = reservationFilters(
                "r", roomTypeId, channel, new ArrayList<>());
        return jdbc.query("""
                SELECT day, SUM(revenue) AS revenue
                FROM (
                    SELECT DATE(p.occurred_at AT TIME ZONE ?) AS day,
                           SUM(p.amount) AS revenue
                    FROM payment_facts p
                    LEFT JOIN reservation_facts r ON r.reservation_id = p.reservation_id
                    WHERE p.status = 'SUCCEEDED'
                      AND p.occurred_at >= ? AND p.occurred_at < ?
                    """ + paymentFilter + """
                    GROUP BY day
                    UNION ALL
                    SELECT DATE(f.occurred_at AT TIME ZONE ?) AS day,
                           -SUM(f.amount) AS revenue
                    FROM refund_facts f
                    LEFT JOIN reservation_facts r ON r.reservation_id = f.reservation_id
                    WHERE f.status IN ('COMPLETED', 'SUCCEEDED')
                      AND f.occurred_at >= ? AND f.occurred_at < ?
                    """ + refundFilter + """
                    GROUP BY day
                ) values_by_day
                GROUP BY day
                ORDER BY day
                """,
                (result, row) -> map(
                        "day", result.getDate("day").toLocalDate(),
                        "revenue", result.getBigDecimal("revenue")),
                dailyParameters(range, roomTypeId, channel).toArray());
    }

    private List<Object> dailyParameters(
            Range range, UUID roomTypeId, String channel) {
        List<Object> result = new ArrayList<>();
        result.add(properties.timeZone());
        result.add(Timestamp.from(range.start()));
        result.add(Timestamp.from(range.end()));
        addReservationFilterValues(result, roomTypeId, channel);
        result.add(properties.timeZone());
        result.add(Timestamp.from(range.start()));
        result.add(Timestamp.from(range.end()));
        addReservationFilterValues(result, roomTypeId, channel);
        return result;
    }

    private List<Map<String, Object>> popularServices(
            Range range, UUID roomTypeId, String channel) {
        List<Object> params = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String filter = reservationFilters("r", roomTypeId, channel, params);
        return jdbc.query("""
                SELECT COALESCE(s.service_name, s.service_code, 'Dịch vụ') AS name,
                       SUM(s.quantity) AS quantity,
                       SUM(s.gross_amount) AS revenue
                FROM service_usage_facts s
                LEFT JOIN reservation_facts r ON r.reservation_id = s.reservation_id
                WHERE s.status <> 'CANCELLED'
                  AND s.occurred_at >= ? AND s.occurred_at < ?
                """ + filter + """
                GROUP BY COALESCE(s.service_name, s.service_code, 'Dịch vụ')
                ORDER BY revenue DESC
                LIMIT 10
                """,
                (result, row) -> map(
                        "name", result.getString("name"),
                        "quantity", result.getBigDecimal("quantity"),
                        "revenue", result.getBigDecimal("revenue")),
                params.toArray());
    }

    private List<Map<String, Object>> popularRoomTypes(Range range, String channel) {
        List<Object> params = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String filter = reservationFilters("r", null, channel, params);
        return jdbc.query("""
                SELECT COALESCE(t.name, t.code, 'Chưa phân loại') AS name,
                       COUNT(*) AS bookings,
                       COALESCE(SUM(r.total_amount), 0) AS revenue
                FROM reservation_facts r
                LEFT JOIN room_type_facts t ON t.room_type_id = r.room_type_id
                WHERE r.created_at >= ? AND r.created_at < ?
                """ + filter + """
                GROUP BY COALESCE(t.name, t.code, 'Chưa phân loại')
                ORDER BY bookings DESC, revenue DESC
                LIMIT 10
                """,
                (result, row) -> map(
                        "name", result.getString("name"),
                        "bookings", result.getLong("bookings"),
                        "revenue", result.getBigDecimal("revenue")),
                params.toArray());
    }

    private List<Map<String, Object>> bookingSources(Range range, UUID roomTypeId) {
        List<Object> params = new ArrayList<>(
                List.of(Timestamp.from(range.start()), Timestamp.from(range.end())));
        String filter = reservationFilters("r", roomTypeId, null, params);
        return jdbc.query("""
                SELECT r.channel AS source, COUNT(*) AS bookings
                FROM reservation_facts r
                WHERE r.created_at >= ? AND r.created_at < ?
                """ + filter + """
                GROUP BY r.channel
                ORDER BY bookings DESC
                """,
                (result, row) -> map(
                        "source", result.getString("source"),
                        "bookings", result.getLong("bookings")),
                params.toArray());
    }

    private Number number(String sql, Object... params) {
        Number value = jdbc.queryForObject(sql, Number.class, params);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql, Object... params) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, params);
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private static String reservationFilters(
            String alias,
            UUID roomTypeId,
            String channel,
            List<Object> parameters) {
        StringBuilder sql = new StringBuilder();
        if (roomTypeId != null) {
            sql.append(" AND ").append(alias).append(".room_type_id = ?");
            parameters.add(roomTypeId);
        }
        String normalizedChannel = normalizedChannel(channel);
        if (normalizedChannel != null) {
            sql.append(" AND ").append(alias).append(".channel = ?");
            parameters.add(normalizedChannel);
        }
        return sql.toString();
    }

    private static void addReservationFilterValues(
            List<Object> values, UUID roomTypeId, String channel) {
        if (roomTypeId != null) values.add(roomTypeId);
        String normalized = normalizedChannel(channel);
        if (normalized != null) values.add(normalized);
    }

    private static String normalizedChannel(String channel) {
        return channel == null || channel.isBlank()
                ? null : channel.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        return denominator == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(numerator)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0
                ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(100).setScale(2);
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> map(
            String key1, Object value1, String key2, Object value2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static Map<String, Object> map(
            String key1,
            Object value1,
            String key2,
            Object value2,
            String key3,
            Object value3) {
        Map<String, Object> result = map(key1, value1, key2, value2);
        result.put(key3, value3);
        return result;
    }

    private record Kpi(
            BigDecimal revenue,
            BigDecimal serviceRevenue,
            long bookings,
            BigDecimal cancellationRate,
            BigDecimal occupancyRate,
            BigDecimal adr,
            BigDecimal revpar,
            long soldRoomNights,
            long capacityRoomNights) {}

    public record Range(
            LocalDate from, LocalDate to, Instant start, Instant end) {}

    public record Dashboard(
            BigDecimal revenue,
            BigDecimal serviceRevenue,
            long bookings,
            BigDecimal cancellationRate,
            BigDecimal occupancyRate,
            BigDecimal adr,
            BigDecimal revpar,
            List<Map<String, Object>> dailyRevenue,
            List<Map<String, Object>> popularServices,
            List<Map<String, Object>> popularRoomTypes,
            List<Map<String, Object>> bookingSources,
            PeriodComparison previousPeriod,
            ReportContext context,
            boolean dataAvailable,
            String emptyReason,
            Map<String, String> metricDefinitions) {}

    public record PeriodComparison(
            BigDecimal revenue,
            long bookings,
            BigDecimal occupancyRate,
            BigDecimal revenueChangePercent,
            BigDecimal bookingChangePercent) {}

    public record ReportContext(
            LocalDate from,
            LocalDate to,
            String timeZone,
            String currency,
            UUID roomTypeId,
            String channel,
            Instant generatedAt) {}
}
