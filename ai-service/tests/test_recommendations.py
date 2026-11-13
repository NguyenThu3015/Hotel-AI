from datetime import date, timedelta

from app.recommendations import score_option
from app.schemas import RecommendationRequest


def test_score_rewards_budget_capacity_and_amenities():
    request = RecommendationRequest(
        check_in=date.today() + timedelta(days=1),
        check_out=date.today() + timedelta(days=2),
        adults=2,
        budget=1_500_000,
        preferred_amenities=["WiFi", "Bữa sáng"],
    )
    good = {"totalAmount": 1_400_000, "maxAdults": 2, "amenities": ["WiFi", "Bữa sáng"], "rating": 4.5}
    poor = {"totalAmount": 3_000_000, "maxAdults": 1, "amenities": [], "rating": 3}
    assert score_option(good, request)[0] > score_option(poor, request)[0]
