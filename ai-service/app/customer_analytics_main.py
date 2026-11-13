from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, Query

from app.auth import require_staff
from app.customer_analytics import (
    analyze_review,
    latest_booking_risks,
    score_booking_risks,
)
from app.database import analytics_db, customer_analytics_db
from app.schemas import BookingRiskRequest, ReviewAnalysisRequest


@asynccontextmanager
async def lifespan(_: FastAPI):
    await analytics_db.connect()
    await customer_analytics_db.connect()
    yield
    await customer_analytics_db.close()
    await analytics_db.close()


app = FastAPI(title="AI Customer Analytics Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "UP", "service": "ai-customer-analytics-service"}


@app.post("/api/v1/reviews/analyze")
async def review_analysis(request: ReviewAnalysisRequest):
    return await analyze_review(request)


@app.post("/api/v1/risks/bookings/score")
async def score_risks(request: BookingRiskRequest, authorization: str | None = Header(default=None)):
    await require_staff(authorization, {"MANAGER", "ADMIN", "RECEPTIONIST"})
    return await score_booking_risks(request)


@app.get("/api/v1/risks/bookings")
async def risks(
    min_probability: float = Query(default=0.0, ge=0, le=1),
    authorization: str | None = Header(default=None),
):
    await require_staff(authorization, {"MANAGER", "ADMIN", "RECEPTIONIST"})
    return await latest_booking_risks(min_probability)


@app.get("/api/v1/risks/bookings/model-info")
async def risk_model_info(authorization: str | None = Header(default=None)):
    await require_staff(authorization, {"MANAGER", "ADMIN", "RECEPTIONIST"})
    return {
        "modelVersion": "booking-risk-logistic-v1",
        "calibrated": False,
        "status": "baseline",
        "features": ["paidRatio", "leadDays", "priorCancellationRatio", "channel", "daysToCheckIn"],
        "thresholds": {"medium": 0.4, "high": 0.7},
        "nextCalibration": "Khi có đủ booking đã CHECKED_OUT/CANCELLED/NO_SHOW để đánh giá nhãn thực tế.",
    }
