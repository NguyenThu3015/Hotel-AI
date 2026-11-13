import unicodedata
from typing import Any

from app.backend import backend
from app.schemas import RecommendationRequest


def normalized_label(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.lower().replace("đ", "d"))
    return "".join(character for character in decomposed if unicodedata.category(character) != "Mn" and character.isalnum())


def score_option(
    option: dict[str, Any],
    request: RecommendationRequest,
    preferred_room_types: set[str] | None = None,
) -> tuple[float, list[str]]:
    reasons: list[str] = []
    total = float(option.get("totalAmount", 0))
    budget_score = 1.0
    if request.budget:
        budget_score = max(0.0, 1 - abs(total - request.budget) / request.budget)
        if total <= request.budget:
            reasons.append("nằm trong ngân sách")
    capacity = int(option.get("maxAdults", 0)) * request.rooms
    capacity_score = min(1.0, capacity / max(request.adults, 1)) if capacity >= request.adults else 0.0
    if capacity_score == 1:
        reasons.append("sức chứa phù hợp")
    amenities = {normalized_label(str(item)) for item in option.get("amenities", [])}
    preferred = {normalized_label(item) for item in request.preferred_amenities}
    amenity_score = len(amenities & preferred) / len(preferred) if preferred else 0.75
    if preferred and amenities & preferred:
        reasons.append("có " + ", ".join(sorted(amenities & preferred)))
    rating_score = min(1.0, float(option.get("rating", 4.0)) / 5)
    history_score = 1.0 if str(option.get("roomTypeId", "")) in (preferred_room_types or set()) else 0.5
    if history_score == 1.0:
        reasons.append("phù hợp lịch sử lưu trú")
    requested_dest = (request.destination_code or "").strip().upper()
    option_dest = str(option.get("destinationCode") or "").strip().upper()
    dest_bonus = 0.0
    if requested_dest and requested_dest != "ALL" and option_dest == requested_dest:
        dest_name = option.get("destinationName") or requested_dest
        reasons.insert(0, f"thuộc khu nghỉ dưỡng HotelAI {dest_name}")
        dest_bonus = 0.08
    elif option.get("destinationName"):
        reasons.append(f"tọa lạc tại {option.get('destinationName')}")
    score = min(
        1.0,
        budget_score * 0.30
        + capacity_score * 0.25
        + amenity_score * 0.20
        + rating_score * 0.15
        + history_score * 0.10
        + dest_bonus,
    )
    return round(score, 4), reasons or ["phù hợp tốt với tiêu chí hiện tại"]


async def recommend(request: RecommendationRequest, authorization: str | None = None) -> list[dict[str, Any]]:
    options = await backend.search_rooms(request)
    history = await backend.booking_history(authorization)
    preferred_types = {str(booking["roomTypeId"]) for booking in history if booking.get("roomTypeId") is not None}
    summary = await backend.review_summary()
    average_rating = float(summary.get("averageRating") or 0) or 4.0
    ranked = []
    for option in options:
        option["rating"] = average_rating
        score, reasons = score_option(option, request, preferred_types)
        ranked.append({**option, "recommendationScore": score, "reasons": reasons})
    return sorted(ranked, key=lambda item: item["recommendationScore"], reverse=True)[:3]
