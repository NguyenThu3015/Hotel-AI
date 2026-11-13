import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header

from app.auth import require_staff
from app.chat import chat
from app.config import Settings, get_settings
from app.database import db
from app.rag import rag
from app.schemas import ChatRequest, ChatResponse, KnowledgeRequest
from app.staff import staff_assistant

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await db.connect()
    try:
        await rag.index_missing_documents()
    except Exception as error:
        logger.warning("Knowledge indexing deferred: %s", type(error).__name__)
    yield
    await db.close()


app = FastAPI(title="AI Conversation Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health(settings: Settings = Depends(get_settings)):
    return {
        "status": "UP",
        "service": "ai-conversation-service",
        "provider": settings.active_provider,
        "chatModel": settings.gemini_chat_model if settings.active_provider == "gemini" else settings.openai_chat_model,
    }


@app.post("/api/v1/chat", response_model=ChatResponse)
async def guest_chat(request: ChatRequest, authorization: str | None = Header(default=None)):
    return await chat.answer(request.message, request.session_id, authorization)


@app.post("/api/v1/staff-assistant/chat", response_model=ChatResponse)
async def staff_chat(request: ChatRequest, authorization: str | None = Header(default=None)):
    identity = await require_staff(authorization)
    return await staff_assistant.answer(request.message, request.session_id, authorization, identity)


@app.post("/api/v1/knowledge", status_code=201)
async def add_knowledge(request: KnowledgeRequest, authorization: str | None = Header(default=None)):
    await require_staff(authorization, {"MANAGER", "ADMIN"})
    document_id = await rag.index_document(
        request.title,
        request.content,
        request.source,
        request.document_type,
        request.audience,
        request.allowed_roles,
    )
    return {"id": document_id, "chunksIndexed": True}
