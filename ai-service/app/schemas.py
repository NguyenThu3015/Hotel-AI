from datetime import date
from uuid import UUID

from pydantic import AliasChoices, BaseModel, Field


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    session_id: UUID | None = Field(default=None, validation_alias=AliasChoices("session_id", "sessionId"))
    destination_code: str | None = Field(
        default=None,
        validation_alias=AliasChoices("destination_code", "destinationCode"),
    )


class Source(BaseModel):
    title: str
    content: str
    score: float


class ChatResponse(BaseModel):
    session_id: UUID
    answer: str
    sources: list[Source] = []
    tools_used: list[str] = []
    grounded: bool = False
    provider: str = "fallback"
    degraded: bool = False


class RecommendationRequest(BaseModel):
    hotel_id: int = 1
    check_in: date
    check_out: date
    adults: int = Field(ge=1)
    children: int = Field(default=0, ge=0)
    rooms: int = Field(default=1, ge=1)
    budget: float | None = Field(default=None, gt=0)
    amenity_ids: list[int] = []
    preferred_amenities: list[str] = []
    destination_code: str | None = Field(
        default=None,
        validation_alias=AliasChoices("destination_code", "destinationCode"),
    )


class ReviewAnalysisRequest(BaseModel):
    text: str = Field(min_length=1, max_length=10000)
    review_id: UUID | None = None


class KnowledgeRequest(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1)
    source: str | None = None
    document_type: str = "POLICY"
    audience: str = Field(default="PUBLIC", pattern="^(PUBLIC|STAFF)$")
    allowed_roles: list[str] = []


class ForecastRequest(BaseModel):
    hotel_id: int = 1
    days: int = Field(default=30, ge=1, le=90)
    history_days: int = Field(default=90, ge=28, le=730)


class ForecastBacktestRequest(BaseModel):
    hotel_id: int = 1
    history_days: int = Field(default=90, ge=35, le=730)
    holdout_days: int = Field(default=14, ge=7, le=60)


class AnomalyRequest(BaseModel):
    hotel_id: int = 1
    lookback_days: int = Field(default=30, ge=1, le=365)
    z_threshold: float = Field(default=2.5, ge=1.5, le=5.0)


class RevenueConstraintRequest(BaseModel):
    room_type_id: UUID
    floor_price: float = Field(gt=0)
    ceiling_price: float = Field(gt=0)
    expected_version: int | None = Field(default=None, ge=0)


class RevenueGenerateRequest(BaseModel):
    hotel_id: int = 1
    date_from: date
    date_to: date


class RevenueDecisionRequest(BaseModel):
    comment: str = Field(min_length=3, max_length=1000)


class BookingRiskRequest(BaseModel):
    hotel_id: int = 1
    horizon_days: int = Field(default=30, ge=1, le=180)
