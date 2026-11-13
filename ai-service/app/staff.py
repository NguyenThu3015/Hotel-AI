import json
import logging
import re
from datetime import date
from uuid import UUID, uuid4

from google import genai
from google.genai import types

from app.auth import StaffIdentity
from app.backend import backend
from app.config import get_settings
from app.database import db
from app.privacy import redact_external_text
from app.rag import rag
from app.schemas import ChatResponse

logger = logging.getLogger(__name__)


class StaffAssistant:
    def __init__(self) -> None:
        settings = get_settings()
        self.provider = settings.active_provider
        self.model = settings.gemini_chat_model
        self.client = genai.Client(api_key=settings.gemini_api_key) if self.provider == "gemini" and settings.gemini_api_key else None

    async def answer(
        self,
        message: str,
        session_id: UUID | None,
        authorization: str | None,
        identity: StaffIdentity,
    ) -> ChatResponse:
        session_id = session_id or uuid4()
        await self._store(session_id, "user", message)
        history = await self._history(session_id)
        roles = sorted(identity.roles)
        try:
            sources = await rag.search(message, audience="STAFF", roles=roles)
        except Exception as error:
            logger.warning("Staff vector search failed: %s", type(error).__name__)
            sources = await rag.keyword_search(message, audience="STAFF", roles=roles)
        live_context, tools = await self._live_context(message, authorization, roles)
        degraded = False
        if self.client:
            try:
                answer = await self._gemini(history, sources, live_context, roles)
                provider = "gemini"
            except Exception as error:
                logger.warning("Staff Gemini failed: %s", type(error).__name__)
                answer = self._fallback(sources, live_context)
                provider = "fallback"
                degraded = True
        else:
            answer = self._fallback(sources, live_context)
            provider = "fallback"
        await self._store(session_id, "assistant", answer)
        return ChatResponse(
            session_id=session_id,
            answer=answer,
            sources=sources,
            tools_used=tools,
            grounded=bool(sources or live_context),
            provider=provider,
            degraded=degraded,
        )

    async def _live_context(self, message: str, authorization: str | None, roles: list[str]) -> tuple[list[dict], list[str]]:
        context: list[dict] = []
        tools: list[str] = []
        references = re.findall(r"\b(?:RSV|BK)[0-9A-Z-]+\b", message.upper())
        if references:
            context.extend(await backend.search_reservations(references[0], authorization))
            tools.append("search_reservations")
        customer_match = re.search(r"(?:khách hàng|khách)\s+(?:tên\s+)?[\"']?([^\"'.,]{3,80})", message, re.IGNORECASE)
        if customer_match and any(role in {"ADMIN", "MANAGER", "RECEPTIONIST"} for role in roles):
            context.extend(await backend.search_customers(customer_match.group(1).strip(), authorization))
            tools.append("search_customers")
        return context, tools

    async def _gemini(self, history: list[dict], sources, live_context: list[dict], roles: list[str]) -> str:
        assert self.client is not None
        policy_context = "\n\n".join(f"{source.title}: {source.content}" for source in sources)
        safe_live = redact_external_text(json.dumps(live_context, ensure_ascii=False, default=str))
        contents = [
            types.Content(
                role="model" if item["role"] == "assistant" else "user",
                parts=[types.Part.from_text(text=redact_external_text(item["content"]))],
            )
            for item in history[-30:]
        ]
        response = await self.client.aio.models.generate_content(
            model=self.model,
            contents=contents,
            config=types.GenerateContentConfig(
                system_instruction=(
                    "Bạn là trợ lý nghiệp vụ nội bộ HotelAI. Trả lời tiếng Việt tự nhiên, ngắn gọn, "
                    "nhớ ngữ cảnh và phân biệt dữ liệu đã xác minh với gợi ý. Không tự thực hiện thao tác ghi; "
                    "mọi đặt phòng, đổi phòng, hoàn tiền, phát hành giá hoặc hóa đơn đều cần nhân viên xác nhận "
                    "trên màn hình nghiệp vụ. Không tiết lộ PII trong câu trả lời AI. "
                    f"Ngày hiện tại: {date.today().isoformat()}. Vai trò nhân viên: {', '.join(roles)}.\n"
                    f"Tài liệu được ACL cho phép:\n{redact_external_text(policy_context)}\n"
                    f"Dữ liệu live đã che PII:\n{safe_live}"
                ),
                temperature=0.35,
                max_output_tokens=1000,
            ),
        )
        if not response.text:
            raise RuntimeError("Staff Gemini returned no text")
        return response.text.strip()

    @staticmethod
    def _fallback(sources, live_context: list[dict]) -> str:
        if live_context:
            first = live_context[0]
            return (
                f"Đã tìm thấy booking {first.get('reference', '—')} ở trạng thái "
                f"{first.get('status', 'không xác định')}. Hãy mở màn Lễ tân để kiểm tra chi tiết "
                "và xác nhận trước khi thực hiện thao tác."
            )
        if sources:
            return sources[0].content
        return "Tôi chưa có dữ liệu được phân quyền đủ để kết luận. Bạn có thể nhập mã booking hoặc hỏi một chính sách nội bộ cụ thể."

    async def _history(self, session_id: UUID) -> list[dict]:
        async with db.acquire() as connection:
            rows = await connection.fetch(
                "SELECT role,content FROM chat_messages WHERE session_id=$1 ORDER BY created_at DESC LIMIT 30",
                session_id,
            )
        return [dict(row) for row in reversed(rows)]

    async def _store(self, session_id: UUID, role: str, content: str) -> None:
        async with db.acquire() as connection:
            await connection.execute(
                "INSERT INTO chat_sessions(id) VALUES($1) ON CONFLICT(id) DO UPDATE SET updated_at=CURRENT_TIMESTAMP",
                session_id,
            )
            await connection.execute(
                "INSERT INTO chat_messages(session_id,role,content,grounded) VALUES($1,$2,$3,$4)",
                session_id,
                role,
                content,
                True,
            )


staff_assistant = StaffAssistant()
