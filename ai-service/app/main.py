import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException

from app.analytics import detect_anomalies, forecast
from app.chat import chat
from app.config import Settings, get_settings
from app.database import analytics_db, db
from app.rag import rag
from app.recommendations import recommend
from app.schemas import (
    AnomalyRequest,
    ChatRequest,
    ChatResponse,
    ForecastRequest,
    KnowledgeRequest,
    RecommendationRequest,
    ReviewAnalysisRequest,
)
from app.sentiment import analyze

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await db.connect()
    await analytics_db.connect()
    try:
        await rag.index_missing_documents()
    except Exception as error:
        logger.warning("Knowledge re-indexing failed; service will start without refreshed vectors: %s", type(error).__name__)
    yield
    await analytics_db.close()
    await db.close()


app = FastAPI(title="Hotel AI Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health(settings: Settings = Depends(get_settings)):
    return {
        "status": "UP",
        "service": settings.app_name,
        "provider": settings.active_provider,
        "gemini_configured": bool(settings.gemini_api_key),
        "openai_configured": bool(settings.openai_api_key),
        "chat_model": (
            settings.gemini_chat_model
            if settings.active_provider == "gemini"
            else settings.openai_chat_model
            if settings.active_provider == "openai"
            else "deterministic-fallback"
        ),
    }


@app.post("/api/v1/chat", response_model=ChatResponse)
async def create_chat(request: ChatRequest, authorization: str | None = Header(default=None)):
    try:
        return await chat.answer(
            request.message,
            request.session_id,
            authorization,
            destination_code=request.destination_code,
        )
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"AI service không thể xử lý yêu cầu: {error}") from error


@app.post("/api/v1/recommendations")
async def recommendations(request: RecommendationRequest, authorization: str | None = Header(default=None)):
    try:
        return {
            "items": await recommend(request, authorization),
            "weights": {"budget": 0.30, "capacity": 0.25, "amenities": 0.20, "rating": 0.15, "history": 0.10},
        }
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"Không thể lấy gợi ý: {error}") from error


@app.post("/api/v1/reviews/analyze")
async def analyze_review(request: ReviewAnalysisRequest):
    return analyze(request.text)


@app.post("/api/v1/knowledge", status_code=201)
async def add_knowledge(request: KnowledgeRequest):
    document_id = await rag.index_document(
        request.title,
        request.content,
        request.source,
        request.document_type,
        request.audience,
        request.allowed_roles,
    )
    return {"id": document_id, "chunks_indexed": True}


@app.post("/api/v1/forecast")
async def demand_forecast(request: ForecastRequest):
    return await forecast(request)


@app.post("/api/v1/anomalies")
async def anomaly_detection(request: AnomalyRequest):
    return await detect_anomalies(request)
