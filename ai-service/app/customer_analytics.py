import json
import math
from datetime import date
from uuid import uuid4

from google import genai
from google.genai import types

from app.config import get_settings
from app.database import analytics_db, customer_analytics_db
from app.privacy import redact_external_text
from app.schemas import BookingRiskRequest, ReviewAnalysisRequest
from app.sentiment import analyze

REVIEW_MODEL_VERSION = "gemini-review-v1"
RISK_MODEL_VERSION = "booking-risk-logistic-v1"


async def analyze_review(request: ReviewAnalysisRequest) -> dict:
    settings = get_settings()
    result = None
    if settings.active_provider == "gemini" and settings.gemini_api_key:
        client = genai.Client(api_key=settings.gemini_api_key)
        try:
            response = await client.aio.models.generate_content(
                model=settings.gemini_chat_model,
                contents=(
                    "Phân tích đánh giá khách sạn bằng tiếng Việt. Không lặp lại dữ liệu cá nhân. "
                    "Chỉ trả JSON theo schema. Topics chỉ dùng ROOM, CLEANLINESS, SERVICE, STAFF, "
                    "LOCATION, VALUE, AMENITIES, OTHER. Urgency dùng LOW, MEDIUM, HIGH, CRITICAL.\n" + redact_external_text(request.text)
                ),
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_json_schema={
                        "type": "object",
                        "properties": {
                            "sentiment": {
                                "type": "string",
                                "enum": ["POSITIVE", "NEUTRAL", "NEGATIVE"],
                            },
                            "score": {"type": "number", "minimum": -1, "maximum": 1},
                            "topics": {
                                "type": "array",
                                "items": {
                                    "type": "string",
                                    "enum": [
                                        "ROOM",
                                        "CLEANLINESS",
                                        "SERVICE",
                                        "STAFF",
                                        "LOCATION",
                                        "VALUE",
                                        "AMENITIES",
                                        "OTHER",
                                    ],
                                },
                            },
                            "urgency": {
                                "type": "string",
                                "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
                            },
                            "confidence": {"type": "number", "minimum": 0, "maximum": 1},
                            "draftResponse": {"type": "string"},
                        },
                        "required": [
                            "sentiment",
                            "score",
                            "topics",
                            "urgency",
                            "confidence",
                            "draftResponse",
                        ],
                    },
                    temperature=0.2,
                    max_output_tokens=600,
                ),
            )
            result = json.loads(response.text)
            result["modelVersion"] = REVIEW_MODEL_VERSION
            result["provider"] = "gemini"
        except Exception:
            result = None
    if result is None:
        result = _fallback_review_analysis(request.text)
    if request.review_id:
        await _store_review_analysis(request.review_id, result)
    return result


async def score_booking_risks(request: BookingRiskRequest) -> dict:
    async with analytics_db.acquire() as connection:
        rows = await connection.fetch(
            """
            SELECT r.reservation_id,r.reference,r.customer_id,r.channel,r.status,
                   r.check_in,r.check_out,r.total_amount,r.paid_amount,r.created_at,
                   COALESCE(history.total_bookings,0) AS prior_bookings,
                   COALESCE(history.cancelled_bookings,0) AS prior_cancellations
            FROM reservation_facts r
            LEFT JOIN (
                SELECT customer_id,count(*) AS total_bookings,
                       count(*) FILTER (WHERE status='CANCELLED') AS cancelled_bookings
                FROM reservation_facts WHERE customer_id IS NOT NULL
                GROUP BY customer_id
            ) history ON history.customer_id=r.customer_id
            WHERE r.status IN ('PENDING_PAYMENT','PENDING_CONFIRMATION','CONFIRMED')
              AND r.check_in BETWEEN current_date AND current_date+$1::int
            ORDER BY r.check_in,r.created_at
            """,
            request.horizon_days,
        )
    items = [_risk(dict(row)) for row in rows]
    async with customer_analytics_db.acquire() as connection:
        async with connection.transaction():
            for item in items:
                await connection.execute(
                    "INSERT INTO booking_risk_scores("
                    "id,reservation_id,reservation_reference,cancellation_probability,"
                    "no_show_probability,risk_level,features,explanations,recommended_action,model_version) "
                    "VALUES($1,$2,$3,$4,$5,$6,$7::jsonb,$8::jsonb,$9,$10)",
                    uuid4(),
                    item["reservationId"],
                    item["reservationReference"],
                    item["cancellationProbability"],
                    item["noShowProbability"],
                    item["riskLevel"],
                    json.dumps(item["features"]),
                    json.dumps(item["explanations"], ensure_ascii=False),
                    item["recommendedAction"],
                    RISK_MODEL_VERSION,
                )
    return {"modelVersion": RISK_MODEL_VERSION, "items": items}


async def latest_booking_risks(min_probability: float = 0.0) -> list[dict]:
    async with customer_analytics_db.acquire() as connection:
        rows = await connection.fetch(
            """
            SELECT DISTINCT ON (reservation_id) * FROM booking_risk_scores
            WHERE GREATEST(cancellation_probability,no_show_probability)>=$1
            ORDER BY reservation_id,scored_at DESC
            """,
            min_probability,
        )
    result = []
    for row in rows:
        features = row["features"]
        explanations = row["explanations"]
        result.append(
            {
                "id": str(row["id"]),
                "reservationId": str(row["reservation_id"]),
                "reservationReference": row["reservation_reference"],
                "cancellationProbability": float(row["cancellation_probability"]),
                "noShowProbability": float(row["no_show_probability"]),
                "riskLevel": row["risk_level"],
                "features": json.loads(features) if isinstance(features, str) else features,
                "explanations": json.loads(explanations) if isinstance(explanations, str) else explanations,
                "recommendedAction": row["recommended_action"],
                "modelVersion": row["model_version"],
                "scoredAt": row["scored_at"],
            }
        )
    result.sort(
        key=lambda item: max(item["cancellationProbability"], item["noShowProbability"]),
        reverse=True,
    )
    return result


def _fallback_review_analysis(text: str) -> dict:
    basic = analyze(text)
    lowered = text.lower()
    topic_map = {
        "vệ sinh": "CLEANLINESS",
        "nhân viên": "STAFF",
        "đồ ăn": "SERVICE",
        "tiếng ồn": "ROOM",
        "vị trí": "LOCATION",
        "giá": "VALUE",
        "tiện nghi": "AMENITIES",
    }
    topics = [topic_map.get(topic, "OTHER") for topic in basic["topics"]] or ["OTHER"]
    critical = any(phrase in lowered for phrase in ("cháy", "ngộ độc", "mất cắp", "bị thương", "quấy rối", "không an toàn"))
    high = any(phrase in lowered for phrase in ("rất bẩn", "không thể ở", "yêu cầu hoàn tiền", "khiếu nại", "hỏng"))
    urgency = "CRITICAL" if critical else "HIGH" if high else "MEDIUM" if basic["sentiment"] == "NEGATIVE" else "LOW"
    if basic["sentiment"] == "NEGATIVE":
        draft = (
            "Khách sạn thành thật xin lỗi vì trải nghiệm chưa đáp ứng mong đợi. "
            "Chúng tôi đã chuyển nội dung đến quản lý phụ trách để kiểm tra và sẽ liên hệ qua kênh bảo mật."
        )
    else:
        draft = "Cảm ơn bạn đã chia sẻ trải nghiệm. Khách sạn rất trân trọng phản hồi của bạn."
    return {
        "sentiment": basic["sentiment"],
        "score": basic["score"],
        "topics": list(dict.fromkeys(topics)),
        "urgency": urgency,
        "confidence": 0.62,
        "draftResponse": draft,
        "modelVersion": "review-rules-v1",
        "provider": "fallback",
    }


def _risk(row: dict) -> dict:
    today = date.today()
    days_to_check_in = max(0, (row["check_in"] - today).days)
    lead_days = max(0, (row["check_in"] - row["created_at"].date()).days)
    total = float(row["total_amount"] or 0)
    paid = float(row["paid_amount"] or 0)
    paid_ratio = min(1.0, paid / total) if total > 0 else 0.0
    prior_bookings = int(row["prior_bookings"] or 0)
    prior_cancellations = int(row["prior_cancellations"] or 0)
    prior_cancel_ratio = prior_cancellations / prior_bookings if prior_bookings else 0.0
    channel_risk = 0.25 if row["channel"] in ("OTA", "UNKNOWN") else 0.0
    cancel_logit = -2.0 + (1 - paid_ratio) * 1.1 + min(lead_days, 90) / 90 * 0.55 + prior_cancel_ratio * 1.8 + channel_risk
    no_show_logit = -2.6 + (1 - paid_ratio) * 1.25 + (0.45 if days_to_check_in <= 2 else 0.0) + prior_cancel_ratio * 0.9 + channel_risk
    cancellation = round(_sigmoid(cancel_logit), 4)
    no_show = round(_sigmoid(no_show_logit), 4)
    maximum = max(cancellation, no_show)
    level = "HIGH" if maximum >= 0.7 else "MEDIUM" if maximum >= 0.4 else "LOW"
    explanations = []
    if paid_ratio < 0.5:
        explanations.append("Booking chưa thanh toán đủ 50%")
    if prior_cancel_ratio >= 0.3:
        explanations.append("Khách có tỷ lệ hủy lịch sử cao")
    if lead_days >= 30:
        explanations.append("Booking được tạo trước ngày đến khá xa")
    if days_to_check_in <= 2 and paid_ratio < 1:
        explanations.append("Sắp đến ngày nhận phòng nhưng còn số dư")
    if not explanations:
        explanations.append("Không có tín hiệu rủi ro nổi bật")
    action = (
        "Nhân viên gọi xác nhận và kiểm tra đặt cọc"
        if level == "HIGH"
        else "Gửi nhắc lịch tự động"
        if level == "MEDIUM"
        else "Theo dõi thông thường"
    )
    return {
        "reservationId": str(row["reservation_id"]),
        "reservationReference": row["reference"],
        "cancellationProbability": cancellation,
        "noShowProbability": no_show,
        "riskLevel": level,
        "features": {
            "daysToCheckIn": days_to_check_in,
            "leadDays": lead_days,
            "paidRatio": round(paid_ratio, 4),
            "priorCancellationRatio": round(prior_cancel_ratio, 4),
            "channel": row["channel"],
        },
        "explanations": explanations,
        "recommendedAction": action,
    }


def _sigmoid(value: float) -> float:
    return 1 / (1 + math.exp(-value))


async def _store_review_analysis(review_id, result: dict) -> None:
    async with customer_analytics_db.acquire() as connection:
        await connection.execute(
            "INSERT INTO review_analyses("
            "review_id,sentiment,sentiment_score,topics,urgency,confidence,draft_response,model_version) "
            "VALUES($1,$2,$3,$4::jsonb,$5,$6,$7,$8) "
            "ON CONFLICT(review_id) DO UPDATE SET sentiment=EXCLUDED.sentiment,"
            "sentiment_score=EXCLUDED.sentiment_score,topics=EXCLUDED.topics,urgency=EXCLUDED.urgency,"
            "confidence=EXCLUDED.confidence,draft_response=EXCLUDED.draft_response,"
            "model_version=EXCLUDED.model_version,analyzed_at=CURRENT_TIMESTAMP",
            review_id,
            result["sentiment"],
            result["score"],
            json.dumps(result["topics"]),
            result["urgency"],
            result["confidence"],
            result["draftResponse"],
            result["modelVersion"],
        )
