from datetime import date, timedelta

from app.analytics import amount_anomalies, build_forecast


def test_forecast_is_bounded_and_recommends_dynamic_multiplier():
    start = date(2026, 1, 1)
    history = [{"day": start + timedelta(days=index), "demand": 8 if index % 7 in (4, 5) else 3} for index in range(56)]
    result = build_forecast(history, room_count=10, days=14, start=date(2026, 3, 1))
    assert len(result) == 14
    assert all(0 <= item["predictedOccupancy"] <= 100 for item in result)
    assert max(item["recommendedPriceMultiplier"] for item in result) >= 1.15


def test_unusual_payment_amount_is_detected():
    payments = [
        {"id": 1, "amount": 1_000_000, "booking_code": "BK1"},
        {"id": 2, "amount": 1_100_000, "booking_code": "BK2"},
        {"id": 3, "amount": 950_000, "booking_code": "BK3"},
        {"id": 4, "amount": 15_000_000, "booking_code": "BK4"},
    ]
    result = amount_anomalies(payments, threshold=1.5)
    assert result[0]["entityId"] == 4


def test_forecast_uses_conservative_baseline_when_history_has_no_demand():
    start = date(2026, 1, 1)
    history = [{"day": start + timedelta(days=index), "demand": 0} for index in range(56)]

    result = build_forecast(history, room_count=4, days=2, start=date(2026, 3, 1))

    assert all(item["predictedRooms"] == 1 for item in result)
    assert all(item["predictedOccupancy"] == 25 for item in result)
    assert all(item["confidence"] == 0.45 for item in result)
