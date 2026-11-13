from fastapi import FastAPI, Header, HTTPException

from app.recommendations import recommend
from app.schemas import RecommendationRequest

app = FastAPI(title="AI Recommendation Service", version="1.0.0")


@app.get("/health")
async def health():
    return {"status": "UP", "service": "ai-recommendation-service"}


@app.get("/api/v1/recommendations/model-info")
async def model_info():
    return {
        "modelVersion": "room-ranking-personalized-v2",
        "online": True,
        "signals": ["budget", "capacity", "amenities", "rating", "bookingHistory"],
        "weights": {"budget": 0.30, "capacity": 0.25, "amenities": 0.20, "rating": 0.15, "history": 0.10},
        "explainability": "Mỗi kết quả trả về reasons và recommendationScore; không dùng thuộc tính nhạy cảm.",
    }


@app.post("/api/v1/recommendations")
async def recommendations(request: RecommendationRequest, authorization: str | None = Header(default=None)):
    try:
        return {
            "items": await recommend(request, authorization),
            "modelVersion": "room-ranking-personalized-v2",
            "weights": {
                "budget": 0.30,
                "capacity": 0.25,
                "amenities": 0.20,
                "rating": 0.15,
                "history": 0.10,
            },
        }
    except Exception as error:
        raise HTTPException(status_code=502, detail="Không thể lấy gợi ý phòng.") from error
