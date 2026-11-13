import json
from datetime import date
from uuid import UUID, uuid4

from fastapi import HTTPException

from app.analytics import forecast
from app.auth import StaffIdentity
from app.backend import backend
from app.database import revenue_db
from app.schemas import ForecastRequest, RevenueConstraintRequest, RevenueGenerateRequest

MODEL_VERSION = "revenue-occupancy-bounds-v1"


async def save_constraint(request: RevenueConstraintRequest) -> dict:
    if request.floor_price > request.ceiling_price:
        raise HTTPException(status_code=400, detail="Giá sàn không được cao hơn giá trần.")
    async with revenue_db.acquire() as connection:
        async with connection.transaction():
            current = await connection.fetchrow(
                "SELECT * FROM revenue_price_constraints WHERE room_type_id=$1 FOR UPDATE",
                request.room_type_id,
            )
            if current:
                if request.expected_version is None or current["version"] != request.expected_version:
                    raise HTTPException(status_code=409, detail="Ràng buộc giá đã thay đổi; hãy tải lại.")
                row = await connection.fetchrow(
                    "UPDATE revenue_price_constraints SET floor_price=$2,ceiling_price=$3,"
                    "version=version+1,updated_at=CURRENT_TIMESTAMP WHERE room_type_id=$1 RETURNING *",
                    request.room_type_id,
                    request.floor_price,
                    request.ceiling_price,
                )
            else:
                row = await connection.fetchrow(
                    "INSERT INTO revenue_price_constraints(room_type_id,floor_price,ceiling_price) VALUES($1,$2,$3) RETURNING *",
                    request.room_type_id,
                    request.floor_price,
                    request.ceiling_price,
                )
    return _constraint(row)


async def list_constraints() -> list[dict]:
    async with revenue_db.acquire() as connection:
        rows = await connection.fetch("SELECT * FROM revenue_price_constraints ORDER BY updated_at DESC")
    return [_constraint(row) for row in rows]


async def generate_proposals(request: RevenueGenerateRequest, authorization: str | None) -> dict:
    today = date.today()
    if request.date_from < today or request.date_to < request.date_from:
        raise HTTPException(status_code=400, detail="Khoảng ngày đề xuất giá không hợp lệ.")
    forecast_days = (request.date_to - today).days + 1
    if forecast_days > 90:
        raise HTTPException(status_code=400, detail="Chỉ có thể đề xuất giá trong 90 ngày tới.")
    forecast_result = await forecast(ForecastRequest(hotel_id=request.hotel_id, days=forecast_days, history_days=90))
    forecast_by_date = {
        item["date"]: item
        for item in forecast_result["items"]
        if request.date_from.isoformat() <= item["date"] <= request.date_to.isoformat()
    }
    async with revenue_db.acquire() as connection:
        constraint_rows = await connection.fetch("SELECT * FROM revenue_price_constraints ORDER BY room_type_id")
    proposals: list[dict] = []
    skipped: list[dict] = []
    for constraint in constraint_rows:
        room_type_id = str(constraint["room_type_id"])
        rates = await backend.base_rates(room_type_id, authorization)
        for stay_date, demand in forecast_by_date.items():
            base_rate = _rate_for_date(rates, stay_date)
            if base_rate is None:
                skipped.append(
                    {
                        "roomTypeId": room_type_id,
                        "stayDate": stay_date,
                        "reason": "Không có base rate hiệu lực",
                    }
                )
                continue
            raw_price = base_rate * float(demand["recommendedPriceMultiplier"])
            recommended = min(
                float(constraint["ceiling_price"]),
                max(float(constraint["floor_price"]), raw_price),
            )
            explanation = {
                "predictedOccupancy": demand["predictedOccupancy"],
                "forecastConfidence": demand["confidence"],
                "multiplier": demand["recommendedPriceMultiplier"],
                "baseRate": base_rate,
                "boundedByFloor": raw_price < float(constraint["floor_price"]),
                "boundedByCeiling": raw_price > float(constraint["ceiling_price"]),
            }
            proposals.append(
                await _insert_proposal(
                    UUID(room_type_id),
                    date.fromisoformat(stay_date),
                    base_rate,
                    recommended,
                    float(constraint["floor_price"]),
                    float(constraint["ceiling_price"]),
                    explanation,
                )
            )
    return {
        "modelVersion": MODEL_VERSION,
        "forecastModel": forecast_result["model"],
        "items": proposals,
        "skipped": skipped,
    }


async def list_proposals(status: str | None = None) -> list[dict]:
    async with revenue_db.acquire() as connection:
        if status:
            rows = await connection.fetch(
                "SELECT * FROM revenue_price_proposals WHERE status=$1 ORDER BY stay_date,created_at DESC",
                status.upper(),
            )
        else:
            rows = await connection.fetch("SELECT * FROM revenue_price_proposals ORDER BY stay_date,created_at DESC LIMIT 500")
    return [_proposal(row) for row in rows]


async def decide_proposal(
    proposal_id: UUID,
    approve: bool,
    comment: str,
    identity: StaffIdentity,
    authorization: str | None,
) -> dict:
    async with revenue_db.acquire() as connection:
        row = await connection.fetchrow("SELECT * FROM revenue_price_proposals WHERE id=$1", proposal_id)
    if not row:
        raise HTTPException(status_code=404, detail="Không tìm thấy đề xuất giá.")
    if row["status"] != "PENDING":
        raise HTTPException(status_code=409, detail="Đề xuất giá đã được quyết định.")
    applied_rule_id = None
    if approve:
        rule = await backend.publish_ai_rate_rule(
            str(proposal_id),
            str(row["room_type_id"]),
            row["stay_date"].isoformat(),
            float(row["recommended_price"]),
            authorization,
        )
        applied_rule_id = rule.get("id")
    async with revenue_db.acquire() as connection:
        updated = await connection.fetchrow(
            "UPDATE revenue_price_proposals SET status=$2,decided_by=$3,decision_comment=$4,"
            "decided_at=CURRENT_TIMESTAMP,applied_rule_id=$5,version=version+1 "
            "WHERE id=$1 AND status='PENDING' RETURNING *",
            proposal_id,
            "APPROVED" if approve else "REJECTED",
            identity.identity_id,
            comment,
            UUID(str(applied_rule_id)) if applied_rule_id else None,
        )
    if not updated:
        raise HTTPException(status_code=409, detail="Đề xuất vừa được người khác quyết định.")
    return _proposal(updated)


async def _insert_proposal(
    room_type_id: UUID,
    stay_date: date,
    current_price: float,
    recommended_price: float,
    floor_price: float,
    ceiling_price: float,
    explanation: dict,
) -> dict:
    async with revenue_db.acquire() as connection:
        async with connection.transaction():
            version = await connection.fetchval(
                "SELECT COALESCE(MAX(proposal_version),0)+1 FROM revenue_price_proposals WHERE room_type_id=$1 AND stay_date=$2",
                room_type_id,
                stay_date,
            )
            row = await connection.fetchrow(
                "INSERT INTO revenue_price_proposals("
                "id,room_type_id,stay_date,current_price,recommended_price,floor_price,ceiling_price,"
                "model_version,explanation,proposal_version) "
                "VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10) RETURNING *",
                uuid4(),
                room_type_id,
                stay_date,
                current_price,
                recommended_price,
                floor_price,
                ceiling_price,
                MODEL_VERSION,
                json.dumps(explanation),
                version,
            )
    return _proposal(row)


def _rate_for_date(rates: list[dict], stay_date: str) -> float | None:
    target = date.fromisoformat(stay_date)
    candidates = [rate for rate in rates if date.fromisoformat(rate["stayFrom"]) <= target <= date.fromisoformat(rate["stayTo"])]
    if not candidates:
        return None
    selected = max(candidates, key=lambda item: (item.get("version", 0), item.get("createdAt", "")))
    return float(selected["amount"])


def _constraint(row) -> dict:
    return {
        "roomTypeId": str(row["room_type_id"]),
        "floorPrice": float(row["floor_price"]),
        "ceilingPrice": float(row["ceiling_price"]),
        "version": row["version"],
        "updatedAt": row["updated_at"],
    }


def _proposal(row) -> dict:
    explanation = row["explanation"]
    if isinstance(explanation, str):
        explanation = json.loads(explanation)
    return {
        "id": str(row["id"]),
        "roomTypeId": str(row["room_type_id"]),
        "stayDate": row["stay_date"],
        "currentPrice": float(row["current_price"]),
        "recommendedPrice": float(row["recommended_price"]),
        "floorPrice": float(row["floor_price"]),
        "ceilingPrice": float(row["ceiling_price"]),
        "modelVersion": row["model_version"],
        "explanation": explanation,
        "proposalVersion": row["proposal_version"],
        "status": row["status"],
        "decidedBy": str(row["decided_by"]) if row["decided_by"] else None,
        "decisionComment": row["decision_comment"],
        "appliedRuleId": str(row["applied_rule_id"]) if row["applied_rule_id"] else None,
        "createdAt": row["created_at"],
        "decidedAt": row["decided_at"],
        "version": row["version"],
    }
