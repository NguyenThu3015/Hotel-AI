from datetime import UTC, date, datetime
from uuid import uuid4

from app.auth import _claim_values
from app.customer_analytics import _fallback_review_analysis, _risk
from app.revenue import _rate_for_date
from app.schemas import ChatRequest, ReviewAnalysisRequest


def test_review_fallback_detects_critical_negative_feedback():
    result = _fallback_review_analysis("Phòng rất bẩn, tôi bị mất cắp và cảm thấy không an toàn.")

    assert result["sentiment"] == "NEGATIVE"
    assert result["urgency"] == "CRITICAL"
    assert "CLEANLINESS" in result["topics"]
    assert result["draftResponse"]


def test_booking_risk_increases_for_unpaid_ota_booking_near_arrival():
    result = _risk(
        {
            "reservation_id": uuid4(),
            "reference": "BK-TEST-001",
            "channel": "OTA",
            "check_in": date.today(),
            "created_at": datetime.now(UTC),
            "total_amount": 2_000_000,
            "paid_amount": 0,
            "prior_bookings": 4,
            "prior_cancellations": 2,
        }
    )

    assert result["cancellationProbability"] > 0.4
    assert result["noShowProbability"] > 0.4
    assert result["explanations"]
    assert result["recommendedAction"]


def test_revenue_uses_latest_matching_base_rate_version():
    rates = [
        {
            "stayFrom": "2026-07-01",
            "stayTo": "2026-07-31",
            "amount": 1_000_000,
            "version": 1,
            "createdAt": "2026-06-01T00:00:00Z",
        },
        {
            "stayFrom": "2026-07-01",
            "stayTo": "2026-07-31",
            "amount": 1_200_000,
            "version": 2,
            "createdAt": "2026-06-15T00:00:00Z",
        },
    ]

    assert _rate_for_date(rates, "2026-07-20") == 1_200_000


def test_request_contract_accepts_frontend_and_internal_aliases():
    session = uuid4()
    assert ChatRequest.model_validate({"message": "xin chào", "sessionId": session}).session_id == session
    review = ReviewAnalysisRequest.model_validate({"review_id": uuid4(), "text": "Dịch vụ tốt"})
    assert review.review_id is not None


def test_jwt_claim_normalization_handles_lists_and_csv():
    assert _claim_values(["admin", "manager"], uppercase=True) == {
        "ADMIN",
        "MANAGER",
    }
    assert _claim_values("booking:read, customer:read", uppercase=False) == {
        "booking:read",
        "customer:read",
    }
