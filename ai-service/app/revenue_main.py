from contextlib import asynccontextmanager
from uuid import UUID

from fastapi import FastAPI, Header, HTTPException, Query

from app.auth import require_staff
from app.database import analytics_db, revenue_db
from app.revenue import (
    decide_proposal,
    generate_proposals,
    list_constraints,
    list_proposals,
    save_constraint,
)
from app.schemas import (
    RevenueConstraintRequest,
    RevenueDecisionRequest,
    RevenueGenerateRequest,
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await analytics_db.connect()
    await revenue_db.connect()
    yield
    await revenue_db.close()
    await analytics_db.close()


app = FastAPI(title="AI Revenue Optimization Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "UP", "service": "ai-revenue-optimization-service"}


@app.get("/api/v1/revenue/constraints")
async def constraints(authorization: str | None = Header(default=None)):
    await require_staff(authorization, {"MANAGER", "ADMIN"})
    return await list_constraints()


@app.put("/api/v1/revenue/constraints/{room_type_id}")
async def update_constraint(
    room_type_id: UUID,
    request: RevenueConstraintRequest,
    authorization: str | None = Header(default=None),
):
    await require_staff(authorization, {"MANAGER", "ADMIN"})
    if room_type_id != request.room_type_id:
        raise HTTPException(status_code=400, detail="roomTypeId không khớp đường dẫn.")
    return await save_constraint(request)


@app.post("/api/v1/revenue/proposals/generate")
async def generate(
    request: RevenueGenerateRequest,
    authorization: str | None = Header(default=None),
):
    await require_staff(authorization, {"MANAGER", "ADMIN"})
    return await generate_proposals(request, authorization)


@app.get("/api/v1/revenue/proposals")
async def proposals(
    status: str | None = Query(default=None),
    authorization: str | None = Header(default=None),
):
    await require_staff(authorization, {"MANAGER", "ADMIN"})
    return await list_proposals(status)


@app.post("/api/v1/revenue/proposals/{proposal_id}/approve")
async def approve(
    proposal_id: UUID,
    request: RevenueDecisionRequest,
    authorization: str | None = Header(default=None),
):
    identity = await require_staff(authorization, {"MANAGER", "ADMIN"})
    return await decide_proposal(proposal_id, True, request.comment, identity, authorization)


@app.post("/api/v1/revenue/proposals/{proposal_id}/reject")
async def reject(
    proposal_id: UUID,
    request: RevenueDecisionRequest,
    authorization: str | None = Header(default=None),
):
    identity = await require_staff(authorization, {"MANAGER", "ADMIN"})
    return await decide_proposal(proposal_id, False, request.comment, identity, authorization)
