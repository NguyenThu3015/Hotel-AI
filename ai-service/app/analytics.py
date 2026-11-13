from __future__ import annotations

from collections import defaultdict
from datetime import date, timedelta
from math import sqrt
from typing import Any

from app.database import analytics_db
from app.schemas import AnomalyRequest, ForecastRequest


def build_forecast(history: list[dict[str, Any]], room_count: int, days: int, start: date | None = None) -> list[dict[str, Any]]:
    start = start or date.today()
    by_weekday: dict[int, list[float]] = defaultdict(list)
    values: list[float] = []
    for row in history:
        value = float(row["demand"])
        values.append(value)
        by_weekday[row["day"].weekday()].append(value)

    has_demand = any(value > 0 for value in values)
    overall = sum(values) / len(values) if has_demand else max(1.0, room_count * 0.25)
    recent = sum(values[-14:]) / max(1, len(values[-14:])) if has_demand else overall
    previous = sum(values[-28:-14]) / max(1, len(values[-28:-14])) if has_demand and len(values) > 14 else overall
    trend = max(0.75, min(1.25, recent / previous if previous else 1.0))
    confidence = min(0.92, 0.45 + sum(value > 0 for value in values) / 300)

    result = []
    for offset in range(days):
        target = start + timedelta(days=offset)
        weekday_values = by_weekday.get(target.weekday(), [])
        weekday_average = sum(weekday_values) / len(weekday_values) if any(weekday_values) else overall
        predicted = min(float(room_count), max(0.0, weekday_average * trend))
        occupancy = predicted / max(room_count, 1)
        multiplier = 1.30 if occupancy >= 0.85 else 1.15 if occupancy >= 0.65 else 0.90 if occupancy < 0.30 else 1.0
        result.append(
            {
                "date": target.isoformat(),
                "predictedRooms": round(predicted, 2),
                "predictedOccupancy": round(occupancy * 100, 2),
                "recommendedPriceMultiplier": multiplier,
                "confidence": round(confidence, 2),
            }
        )
    return result


def amount_anomalies(payments: list[dict[str, Any]], threshold: float) -> list[dict[str, Any]]:
    amounts = [float(payment["amount"]) for payment in payments]
    if len(amounts) < 3:
        return []
    average = sum(amounts) / len(amounts)
    deviation = sqrt(sum((amount - average) ** 2 for amount in amounts) / len(amounts))
    if deviation == 0:
        return []
    result = []
    for payment, amount in zip(payments, amounts, strict=True):
        z_score = abs(amount - average) / deviation
        if z_score >= threshold:
            result.append(
                {
                    "type": "UNUSUAL_PAYMENT_AMOUNT",
                    "severity": "HIGH" if z_score >= threshold + 1 else "MEDIUM",
                    "entityId": payment["id"],
                    "bookingCode": payment.get("booking_code"),
                    "message": "Giá trị thanh toán lệch đáng kể so với giao dịch thông thường",
                    "score": round(min(1.0, z_score / 5), 4),
                    "detectedValue": amount,
                }
            )
    return result


def evaluate_forecast(history: list[dict[str, Any]], room_count: int, holdout_days: int) -> dict[str, Any]:
    """Small deterministic rolling holdout used to expose model quality in operations."""
    if len(history) <= holdout_days + 14:
        return {"evaluated": False, "reason": "not-enough-history", "samples": 0}
    train = history[:-holdout_days]
    holdout = history[-holdout_days:]
    start = holdout[0]["day"]
    predictions = build_forecast(train, room_count, holdout_days, start=start)
    actual = [float(row["demand"]) for row in holdout]
    predicted = [float(row["predictedRooms"]) for row in predictions]
    errors = [abs(a - p) for a, p in zip(actual, predicted, strict=True)]
    non_zero = [abs(a) for a in actual if a > 0]
    mape = sum(e / a for e, a in zip(errors, actual, strict=True) if a > 0) / max(len(non_zero), 1)
    return {
        "evaluated": True,
        "samples": len(actual),
        "maeRooms": round(sum(errors) / len(errors), 4),
        "mape": round(mape, 4),
        "actualAverageRooms": round(sum(actual) / len(actual), 4),
        "predictedAverageRooms": round(sum(predicted) / len(predicted), 4),
    }


async def forecast(request: ForecastRequest) -> dict[str, Any]:
    async with analytics_db.acquire() as connection:
        room_count = await connection.fetchval("SELECT count(*) FROM room_facts WHERE active AND status<>'MAINTENANCE'")
        rows = await connection.fetch(
            """
            SELECT series.day::date AS day,
                   COALESCE(sum(CASE WHEN reservation_id IS NULL THEN 0 ELSE room_count END), 0)::int AS demand
            FROM generate_series(
                current_date-$1::int, current_date-1, interval '1 day') AS series(day)
            LEFT JOIN reservation_facts ON check_in<=series.day AND check_out>series.day
              AND status IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT')
            GROUP BY series.day ORDER BY series.day
            """,
            request.history_days,
        )
    items = build_forecast([dict(row) for row in rows], int(room_count or 0), request.days)
    peak = max(items, key=lambda item: item["predictedOccupancy"]) if items else None
    return {
        "hotelId": request.hotel_id,
        "model": "weekday-moving-average-v1",
        "items": items,
        "peakDate": peak["date"] if peak else None,
        "explanation": "Dự báo dùng nhu cầu theo thứ trong tuần, xu hướng 14 ngày và giới hạn tồn phòng thực tế.",
    }


async def forecast_backtest(request) -> dict[str, Any]:
    async with analytics_db.acquire() as connection:
        room_count = await connection.fetchval("SELECT count(*) FROM room_facts WHERE active AND status<>'MAINTENANCE'")
        rows = await connection.fetch(
            """
            SELECT series.day::date AS day,
                   COALESCE(sum(CASE WHEN reservation_id IS NULL THEN 0 ELSE room_count END), 0)::int AS demand
            FROM generate_series(current_date-$1::int, current_date-1, interval '1 day') AS series(day)
            LEFT JOIN reservation_facts ON check_in<=series.day AND check_out>series.day
              AND status IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT')
            GROUP BY series.day ORDER BY series.day
            """, request.history_days)
    return {
        "hotelId": request.hotel_id,
        "modelVersion": "weekday-moving-average-v1",
        **evaluate_forecast([dict(row) for row in rows], int(room_count or 0), request.holdout_days),
    }


async def detect_anomalies(request: AnomalyRequest) -> dict[str, Any]:
    async with analytics_db.acquire() as connection:
        payment_rows = await connection.fetch(
            """
            SELECT p.payment_id AS id, p.amount, p.status, p.occurred_at,
                   r.reference AS booking_code
            FROM payment_facts p
            LEFT JOIN reservation_facts r ON r.reservation_id=p.reservation_id
            WHERE p.occurred_at>=current_timestamp-($1::int * interval '1 day')
            ORDER BY p.occurred_at
            """,
            request.lookback_days,
        )
        failed_rows = await connection.fetch(
            """
            SELECT coalesce(r.reference, p.payment_id::text) AS booking_code,
                   count(*)::int AS attempts
            FROM payment_facts p
            LEFT JOIN reservation_facts r ON r.reservation_id=p.reservation_id
            WHERE p.status='FAILED'
              AND p.occurred_at>=current_timestamp-($1::int * interval '1 day')
            GROUP BY coalesce(r.reference, p.payment_id::text) HAVING count(*)>=3
            """,
            request.lookback_days,
        )
        cancellation_rows = await connection.fetch(
            """
            SELECT date(cancelled_at) AS day, count(*)::int AS total
            FROM reservation_facts WHERE status='CANCELLED'
              AND cancelled_at>=current_timestamp-($1::int * interval '1 day')
            GROUP BY date(cancelled_at) HAVING count(*)>=3
            """,
            request.lookback_days,
        )
        high_velocity_rows = await connection.fetch(
            """
            SELECT customer_id AS user_id, count(*)::int AS total
            FROM reservation_facts WHERE customer_id IS NOT NULL
              AND created_at>=current_timestamp-interval '24 hours'
            GROUP BY customer_id HAVING count(*)>=5
            """
        )
        repeated_cancel_rows = await connection.fetch(
            """
            SELECT customer_id AS user_id, count(*)::int AS total
            FROM reservation_facts WHERE customer_id IS NOT NULL AND status='CANCELLED'
              AND cancelled_at>=current_timestamp-($1::int * interval '1 day')
            GROUP BY customer_id HAVING count(*)>=3
            """,
            request.lookback_days,
        )

    anomalies = amount_anomalies([dict(row) for row in payment_rows], request.z_threshold)
    anomalies.extend(
        {
            "type": "REPEATED_PAYMENT_FAILURE",
            "severity": "HIGH",
            "entityId": row["booking_code"],
            "bookingCode": row["booking_code"],
            "message": f"Đặt phòng có {row['attempts']} lần thanh toán thất bại",
            "score": min(1.0, row["attempts"] / 6),
        }
        for row in failed_rows
    )
    anomalies.extend(
        {
            "type": "CANCELLATION_SPIKE",
            "severity": "MEDIUM",
            "entityId": row["day"].isoformat(),
            "message": f"Có {row['total']} lượt đặt phòng bị hủy trong ngày",
            "score": min(1.0, row["total"] / 10),
        }
        for row in cancellation_rows
    )
    anomalies.extend(
        {
            "type": "HIGH_BOOKING_VELOCITY",
            "severity": "HIGH",
            "entityId": row["user_id"],
            "message": f"Tài khoản tạo {row['total']} lượt đặt phòng trong 24 giờ",
            "score": min(1.0, row["total"] / 10),
        }
        for row in high_velocity_rows
    )
    anomalies.extend(
        {
            "type": "REPEATED_CANCELLATION",
            "severity": "MEDIUM",
            "entityId": row["user_id"],
            "message": f"Tài khoản đặt rồi hủy {row['total']} lần trong kỳ theo dõi",
            "score": min(1.0, row["total"] / 6),
        }
        for row in repeated_cancel_rows
    )
    anomalies.sort(key=lambda item: item["score"], reverse=True)
    return {
        "hotelId": request.hotel_id,
        "lookbackDays": request.lookback_days,
        "riskLevel": "HIGH" if any(item["severity"] == "HIGH" for item in anomalies) else "MEDIUM" if anomalies else "LOW",
        "items": anomalies,
    }
