from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

import httpx

from app.config import get_settings
from app.schemas import RecommendationRequest


class BackendClient:
    def __init__(self) -> None:
        settings = get_settings()
        self.base_url = settings.backend_url
        self.timeout = settings.request_timeout_seconds

    async def search_rooms(self, request: RecommendationRequest | dict[str, Any]) -> list[dict[str, Any]]:
        data = request.model_dump() if isinstance(request, RecommendationRequest) else request
        params = {
            "hotelId": data.get("hotel_id", data.get("hotelId", 1)),
            "checkIn": str(data.get("check_in", data.get("checkIn"))),
            "checkOut": str(data.get("check_out", data.get("checkOut"))),
            "adults": data.get("adults", 1),
            "children": data.get("children", 0),
            "rooms": data.get("rooms", 1),
        }
        amenity_ids = data.get("amenity_ids") or data.get("amenityIds")
        if amenity_ids:
            params["amenityIds"] = amenity_ids
        destination_code = data.get("destination_code") or data.get("destinationCode")
        if destination_code and str(destination_code).strip().upper() != "ALL":
            params["destinationCode"] = str(destination_code).strip().upper()
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(f"{self.base_url}/availability/search", params=params)
            response.raise_for_status()
            payload = response.json()
            options = payload.get("content", []) if isinstance(payload, dict) else payload
            return [self._normalize_room(option) for option in options]

    async def booking_status(self, booking_code: str, authorization: str | None) -> dict[str, Any]:
        bookings = await self.booking_history(authorization)
        normalized = booking_code.strip().upper()
        for booking in bookings:
            if str(booking.get("id", "")).upper() == normalized or str(booking.get("reference", "")).upper() == normalized:
                return booking
        return {"error": "BOOKING_NOT_FOUND", "message": "Không tìm thấy booking thuộc tài khoản này."}

    async def booking_history(self, authorization: str | None) -> list[dict[str, Any]]:
        if not authorization:
            return []
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(f"{self.base_url}/bookings", headers={"Authorization": authorization})
            if response.status_code in (401, 403):
                return []
            response.raise_for_status()
            payload = response.json()
            return payload.get("content", []) if isinstance(payload, dict) else payload

    async def review_summary(self) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(f"{self.base_url}/reviews/summary")
            response.raise_for_status()
            return response.json()

    async def base_rates(self, room_type_id: str, authorization: str | None) -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(
                f"{self.base_url}/admin/pricing/base-rates",
                params={"roomTypeId": room_type_id},
                headers=self._authorization(authorization),
            )
            response.raise_for_status()
            return response.json()

    async def publish_ai_rate_rule(
        self,
        proposal_id: str,
        room_type_id: str,
        stay_date: str,
        amount: float,
        authorization: str | None,
    ) -> dict[str, Any]:
        payload = {
            "ruleKey": proposal_id,
            "roomTypeId": room_type_id,
            "name": f"AI approved price {stay_date}",
            "priceMode": "FIXED_NIGHTLY",
            "value": amount,
            "priority": 9000,
            "stayFrom": stay_date,
            "stayTo": stay_date,
            "daysOfWeek": [],
            "minGuests": None,
            "maxGuests": None,
            "bookingEffectiveFrom": datetime.now(UTC).isoformat(),
            "bookingEffectiveTo": None,
            "outlierConfirmed": True,
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/admin/pricing/rules",
                json=payload,
                headers={
                    **self._authorization(authorization),
                    "Idempotency-Key": f"ai-revenue-{proposal_id}",
                },
            )
            response.raise_for_status()
            return response.json()

    async def search_customers(self, query: str, authorization: str | None) -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(
                f"{self.base_url}/admin/customers",
                params={"query": query, "size": 10},
                headers=self._authorization(authorization),
            )
            response.raise_for_status()
            payload = response.json()
            return payload.get("content", [])

    async def search_reservations(self, query: str, authorization: str | None) -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(
                f"{self.base_url}/admin/stay-reservations",
                params={"query": query},
                headers=self._authorization(authorization),
            )
            response.raise_for_status()
            return response.json()

    async def create_booking_draft(self, data: dict[str, Any], authorization: str | None) -> dict[str, Any]:
        if not authorization:
            return {"error": "AUTHENTICATION_REQUIRED", "message": "Khách cần đăng nhập trước khi giữ phòng."}
        if not data.pop("confirmed", False):
            return {"error": "CONFIRMATION_REQUIRED", "message": "Cần khách xác nhận rõ trước khi tạo giữ chỗ."}
        payload = {
            "roomTypeId": data["room_type_id"],
            "checkIn": data["check_in"],
            "checkOut": data["check_out"],
            "adults": data["adults"],
            "children": data.get("children", 0),
            "roomCount": data.get("quantity", 1),
            "contactName": data["contact_name"],
            "contactEmail": data["contact_email"],
            "contactPhone": data.get("contact_phone"),
            "specialRequests": data.get("special_requests"),
            "promotionCode": data.get("promotion_code"),
            "guests": [
                {
                    "fullName": data["contact_name"],
                    "documentType": None,
                    "documentNumber": None,
                    "nationality": "VN",
                    "primary": True,
                }
            ],
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/bookings",
                json=payload,
                headers={"Authorization": authorization, "Idempotency-Key": str(uuid4())},
            )
            response.raise_for_status()
            return response.json()

    async def cancel_booking(self, data: dict[str, Any], authorization: str | None) -> dict[str, Any]:
        if not authorization:
            return {"error": "AUTHENTICATION_REQUIRED", "message": "Khách cần đăng nhập để hủy booking."}
        if not data.get("confirmed", False):
            return {"error": "CONFIRMATION_REQUIRED", "message": "Cần khách xác nhận rõ phí và việc hủy."}
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/bookings/{data['booking_id']}/cancel",
                json={"expectedVersion": data["expected_version"], "reason": data["reason"]},
                headers={"Authorization": authorization, "Idempotency-Key": str(uuid4())},
            )
            response.raise_for_status()
            return response.json()

    @staticmethod
    def _normalize_room(option: dict[str, Any]) -> dict[str, Any]:
        price = option.get("price") or {}
        amenities = [item.get("name", "") if isinstance(item, dict) else str(item) for item in option.get("amenities", [])]
        images = option.get("images") or []
        image = next(
            (item.get("url") for item in images if isinstance(item, dict) and item.get("active", True) and item.get("url")),
            None,
        )
        return {
            **option,
            "roomTypeId": str(option.get("roomTypeId")),
            "roomSubtotal": float(price.get("subtotal") or option.get("roomSubtotal") or 0),
            "taxAmount": float(price.get("tax") or option.get("taxAmount") or 0),
            "serviceFee": float(price.get("serviceFee") or option.get("serviceFee") or 0),
            "totalAmount": float(price.get("total") or option.get("totalAmount") or 0),
            "amenities": amenities,
            "imageUrl": image or option.get("imageUrl"),
            "cancellationPolicy": option.get("policySummary")
            or option.get("cancellationPolicy")
            or "Chính sách sẽ được chốt trong báo giá đặt phòng.",
        }

    @staticmethod
    def _authorization(value: str | None) -> dict[str, str]:
        return {"Authorization": value} if value else {}


backend = BackendClient()
