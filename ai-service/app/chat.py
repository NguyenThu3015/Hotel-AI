import json
import logging
import re
import unicodedata
from datetime import date
from typing import Any
from uuid import UUID, uuid4

from google import genai
from google.genai import types
from openai import AsyncOpenAI

from app.backend import backend
from app.config import get_settings
from app.database import db
from app.privacy import redact_external_text
from app.rag import rag
from app.schemas import ChatResponse, RecommendationRequest

TOOLS = [
    {
        "type": "function",
        "name": "search_available_rooms",
        "description": "Tìm phòng trống và giá thật từ Hotel API. Luôn dùng hàm này khi khách hỏi giá hoặc tồn phòng.",
        "parameters": {
            "type": "object",
            "properties": {
                "hotel_id": {"type": "integer"},
                "check_in": {"type": "string"},
                "check_out": {"type": "string"},
                "adults": {"type": "integer"},
                "children": {"type": "integer"},
                "rooms": {"type": "integer"},
            },
            "required": ["hotel_id", "check_in", "check_out", "adults", "rooms"],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "type": "function",
        "name": "get_booking_status",
        "description": "Kiểm tra trạng thái booking có thật.",
        "parameters": {
            "type": "object",
            "properties": {"booking_code": {"type": "string"}},
            "required": ["booking_code"],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "type": "function",
        "name": "calculate_booking_price",
        "description": "Tính giá cuối cùng đã gồm thuế/phí từ Hotel API cho một loại phòng và khoảng ngày.",
        "parameters": {
            "type": "object",
            "properties": {
                "hotel_id": {"type": "integer"},
                "check_in": {"type": "string"},
                "check_out": {"type": "string"},
                "adults": {"type": "integer"},
                "children": {"type": "integer"},
                "rooms": {"type": "integer"},
            },
            "required": ["hotel_id", "check_in", "check_out", "adults", "children", "rooms"],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "type": "function",
        "name": "create_booking_draft",
        "description": "Tạo booking giữ chỗ 15 phút. Chỉ gọi sau khi khách đã đăng nhập và xác nhận rõ toàn bộ thông tin.",
        "parameters": {
            "type": "object",
            "properties": {
                "hotel_id": {"type": "integer"},
                "room_type_id": {"type": "string", "format": "uuid"},
                "check_in": {"type": "string"},
                "check_out": {"type": "string"},
                "adults": {"type": "integer"},
                "children": {"type": "integer"},
                "quantity": {"type": "integer"},
                "contact_name": {"type": "string"},
                "contact_email": {"type": "string"},
                "contact_phone": {"type": ["string", "null"]},
                "special_requests": {"type": ["string", "null"]},
                "promotion_code": {"type": ["string", "null"]},
                "confirmed": {"type": "boolean"},
            },
            "required": [
                "hotel_id",
                "room_type_id",
                "check_in",
                "check_out",
                "adults",
                "children",
                "quantity",
                "contact_name",
                "contact_email",
                "contact_phone",
                "special_requests",
                "promotion_code",
                "confirmed",
            ],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "type": "function",
        "name": "cancel_booking",
        "description": "Hủy booking. Chỉ gọi khi đã giải thích phí dự kiến và khách xác nhận rõ việc hủy.",
        "parameters": {
            "type": "object",
            "properties": {
                "booking_id": {"type": "string", "format": "uuid"},
                "expected_version": {"type": "integer", "minimum": 0},
                "reason": {"type": "string"},
                "confirmed": {"type": "boolean"},
            },
            "required": ["booking_id", "expected_version", "reason", "confirmed"],
            "additionalProperties": False,
        },
        "strict": True,
    },
]

logger = logging.getLogger(__name__)

GEMINI_READ_ONLY_TOOL_NAMES = {
    "search_available_rooms",
    "get_booking_status",
}
GEMINI_TOOLS = [
    types.Tool(
        function_declarations=[
            types.FunctionDeclaration(
                name=tool["name"],
                description=tool["description"],
                parameters_json_schema=tool["parameters"],
            )
            for tool in TOOLS
            if tool["name"] in GEMINI_READ_ONLY_TOOL_NAMES
        ]
    )
]


class ChatService:
    def __init__(self) -> None:
        settings = get_settings()
        self.provider = settings.active_provider
        self.openai_model = settings.openai_chat_model
        self.gemini_model = settings.gemini_chat_model
        self.openai_client = AsyncOpenAI(api_key=settings.openai_api_key) if self.provider == "openai" and settings.openai_api_key else None
        self.gemini_client = (
            genai.Client(api_key=settings.gemini_api_key) if self.provider == "gemini" and settings.gemini_api_key else None
        )

    async def answer(
        self,
        message: str,
        session_id: UUID | None,
        authorization: str | None,
        destination_code: str | None = None,
    ) -> ChatResponse:
        session_id = session_id or uuid4()
        await self._store(session_id, "user", message)
        history = await self._history(session_id)
        detected_dest = self._detect_destination(message) or (
            destination_code.strip().upper()
            if destination_code and destination_code.strip().upper() != "ALL"
            else None
        )
        try:
            sources = await rag.search(message)
        except Exception as error:
            logger.warning("Vector RAG lookup failed; using keyword lookup: %s", type(error).__name__)
            sources = await rag.keyword_search(message)
        response_provider = self.provider
        degraded = False
        try:
            if self.gemini_client:
                answer, tools = await self._gemini_answer(sources, authorization, history, detected_dest)
            elif self.openai_client:
                answer, tools = await self._openai_answer(message, sources, authorization, history, detected_dest)
            else:
                answer, tools = await self._fallback_answer(message, sources, history, authorization, detected_dest)
        except Exception as error:
            logger.warning("%s provider failed; using deterministic fallback: %s", self.provider, type(error).__name__)
            answer, tools = await self._fallback_answer(message, sources, history, authorization, detected_dest)
            response_provider = "fallback"
            degraded = self.provider != "fallback"
        grounded = bool(sources or tools)
        await self._store(session_id, "assistant", answer, grounded=grounded)
        return ChatResponse(
            session_id=session_id,
            answer=answer,
            sources=sources,
            tools_used=tools,
            grounded=grounded,
            provider=response_provider,
            degraded=degraded,
        )

    def _instructions(self, sources: list, destination_code: str | None = None) -> str:
        context = "\n\n".join(
            f"[{index}] {redact_external_text(item.title)}: {redact_external_text(item.content)}" for index, item in enumerate(sources, 1)
        )
        dest_hint = (
            f"Khách đang quan tâm điểm đến mã {destination_code} trong chuỗi HotelAI Việt Nam (10 điểm đến 3 miền Bắc - Trung - Nam). "
            if destination_code
            else "HotelAI là chuỗi khu nghỉ dưỡng & khách sạn 5 sao tại 10 điểm đến Việt Nam (Vịnh Hạ Long, Sapa, Hà Nội, Đà Nẵng, Hội An, Nha Trang, Đà Lạt, Phú Quốc, TP. Hồ Chí Minh, Vũng Tàu). "
        )
        return (
            "Bạn là HotelAI, trợ lý khách sạn nói tiếng Việt tự nhiên, thân thiện và súc tích. "
            + dest_hint
            + "Hiểu toàn bộ ngữ cảnh hội thoại, không hỏi lại dữ liệu khách vừa cung cấp và không trả lời như biểu mẫu cứng. "
            f"Ngày hiện tại là {date.today().isoformat()}. Khi khách dùng ngày tương đối, hãy quy đổi và xác nhận ngày cụ thể. "
            "Mọi giá, tồn phòng hoặc trạng thái booking phải lấy bằng công cụ; tuyệt đối không bịa. "
            "Trong kết quả tìm phòng, totalAmount là tổng tiền của toàn kỳ nghỉ, không phải giá mỗi đêm; "
            "chỉ nói đã gồm thuế/phí khi các trường taxAmount và serviceFee trong dữ liệu xác nhận điều đó. "
            "Nếu thiếu ngày nhận/trả, số khách hoặc số phòng thì chỉ hỏi đúng phần còn thiếu. "
            "Không hứa đã lưu sở thích hoặc bảo đảm yêu cầu đặc biệt khi chưa có booking; hãy nói khách có thể "
            "nhập yêu cầu vào biểu mẫu và khách sạn sẽ xác nhận theo khả năng đáp ứng. "
            "Không thu thập CCCD, số thẻ, email hoặc số điện thoại trong chat; hướng khách sang biểu mẫu bảo mật. "
            "Không tự đặt, hủy, thanh toán hay thay đổi dữ liệu. Khi dữ liệu hoặc căn cứ chưa đủ, nói rõ giới hạn. "
            "Chỉ nhắc nguồn khi nguồn thực sự hỗ trợ câu trả lời. "
            f"Ngữ cảnh chính sách đã truy xuất:\n{context or 'Không có tài liệu phù hợp cho lượt này.'}"
        )

    async def _gemini_answer(
        self,
        sources: list,
        authorization: str | None,
        history: list[dict[str, str]],
        destination_code: str | None = None,
    ) -> tuple[str, list[str]]:
        assert self.gemini_client is not None
        contents = [
            types.Content(
                role="model" if item["role"] == "assistant" else "user",
                parts=[types.Part.from_text(text=redact_external_text(item["content"]))],
            )
            for item in history[-30:]
            if item["role"] in ("user", "assistant")
        ]
        config = types.GenerateContentConfig(
            system_instruction=self._instructions(sources, destination_code),
            tools=GEMINI_TOOLS,
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
            temperature=0.45,
            max_output_tokens=1200,
        )
        tools_used: list[str] = []
        for _ in range(4):
            response = await self.gemini_client.aio.models.generate_content(
                model=self.gemini_model,
                contents=contents,
                config=config,
            )
            calls = list(response.function_calls or [])
            if not calls:
                answer = (response.text or "").strip()
                if not answer:
                    raise RuntimeError("Gemini returned an empty answer")
                return answer, tools_used
            if not response.candidates or not response.candidates[0].content:
                raise RuntimeError("Gemini returned a function call without candidate content")
            contents.append(response.candidates[0].content)
            function_parts = []
            for call in calls:
                if call.name not in GEMINI_READ_ONLY_TOOL_NAMES:
                    result: Any = {"error": "TOOL_NOT_ALLOWED"}
                else:
                    tools_used.append(call.name)
                    result = await self._execute_tool(call.name, dict(call.args or {}), authorization, destination_code)
                safe_result = json.loads(redact_external_text(json.dumps(result, ensure_ascii=False, default=str)))
                function_parts.append(types.Part.from_function_response(name=call.name, response={"result": safe_result}))
            contents.append(types.Content(role="tool", parts=function_parts))
        raise RuntimeError("Gemini exceeded the tool-call limit")

    async def _openai_answer(
        self,
        message: str,
        sources: list,
        authorization: str | None,
        history: list[dict[str, str]],
        destination_code: str | None = None,
    ) -> tuple[str, list[str]]:
        instructions = self._instructions(sources, destination_code)
        inputs: list[Any] = [
            {"role": item["role"], "content": redact_external_text(item["content"])}
            for item in history[-12:]
            if item["role"] in ("user", "assistant")
        ]
        tools_used: list[str] = []
        assert self.openai_client is not None
        response = await self.openai_client.responses.create(model=self.openai_model, instructions=instructions, input=inputs, tools=TOOLS)
        for _ in range(3):
            calls = [item for item in response.output if item.type == "function_call"]
            if not calls:
                return response.output_text, tools_used
            inputs.extend(response.output)
            for call in calls:
                arguments = json.loads(call.arguments)
                tools_used.append(call.name)
                result = await self._execute_tool(call.name, arguments, authorization, destination_code)
                inputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps(result, ensure_ascii=False),
                    }
                )
            response = await self.openai_client.responses.create(
                model=self.openai_model, instructions=instructions, input=inputs, tools=TOOLS
            )
        return "Tôi chưa thể hoàn tất yêu cầu sau nhiều lần kiểm tra dữ liệu.", tools_used

    async def _execute_tool(
        self,
        name: str,
        arguments: dict[str, Any],
        authorization: str | None,
        destination_code: str | None = None,
    ) -> Any:
        if name in ("search_available_rooms", "calculate_booking_price"):
            merged = dict(arguments)
            if destination_code and not merged.get("destination_code") and not merged.get("destinationCode"):
                merged["destination_code"] = destination_code
            return await backend.search_rooms(merged)
        if name == "get_booking_status":
            return await backend.booking_status(arguments["booking_code"], authorization)
        if name == "create_booking_draft":
            return await backend.create_booking_draft(arguments, authorization)
        if name == "cancel_booking":
            return await backend.cancel_booking(arguments, authorization)
        return {"error": "UNKNOWN_TOOL", "message": f"Công cụ {name} không tồn tại."}

    def _detect_destination(self, text: str) -> str | None:
        normalized = self._normalize(text)
        mapping = [
            ("ha long", "HA_LONG"),
            ("quang ninh", "HA_LONG"),
            ("sapa", "SAPA"),
            ("sa pa", "SAPA"),
            ("lao cai", "SAPA"),
            ("ha noi", "HA_NOI"),
            ("da nang", "DA_NANG"),
            ("son tra", "DA_NANG"),
            ("hoi an", "HOI_AN"),
            ("quang nam", "HOI_AN"),
            ("nha trang", "NHA_TRANG"),
            ("khanh hoa", "NHA_TRANG"),
            ("cam ranh", "NHA_TRANG"),
            ("da lat", "DA_LAT"),
            ("lam dong", "DA_LAT"),
            ("tuyen lam", "DA_LAT"),
            ("phu quoc", "PHU_QUOC"),
            ("kien giang", "PHU_QUOC"),
            ("bai kem", "PHU_QUOC"),
            ("ho chi minh", "HCM"),
            ("tphcm", "HCM"),
            ("sai gon", "HCM"),
            ("vung tau", "VUNG_TAU"),
            ("ho tram", "VUNG_TAU"),
        ]
        for keyword, code in mapping:
            if keyword in normalized:
                return code
        return None

    async def _fallback_answer(
        self,
        message: str,
        sources: list,
        history: list[dict[str, str]],
        authorization: str | None,
        destination_code: str | None = None,
    ) -> tuple[str, list[str]]:
        normalized = self._normalize(message)

        if re.fullmatch(r"(?:xin )?chao(?: hotelai)?[!. ]*", normalized):
            return (
                "Xin chào! Tôi có thể giải thích chính sách, kiểm tra phòng và giá theo ngày, "
                "hoặc tra cứu booking của bạn. Bạn đang cần hỗ trợ việc nào?",
                [],
            )

        booking_reference = re.search(r"\bRSV[0-9A-Z]+\b", message.upper())
        if booking_reference and any(word in normalized for word in ("trang thai", "booking", "dat phong")):
            result = await backend.booking_status(booking_reference.group(0), authorization)
            if result.get("error"):
                return result["message"], ["get_booking_status"]
            return (
                f"Booking {result.get('reference')} hiện ở trạng thái {result.get('status')}. "
                f"Thời gian lưu trú: {result.get('checkIn')} đến {result.get('checkOut')}. ",
                ["get_booking_status"],
            )

        if any(word in normalized for word in ("chinh sach huy", "huy phong", "phi huy", "cancellation")):
            source = self._best_source(sources, "huy")
            answer = (
                source.content
                if source
                else (
                    "Booking đang chờ thanh toán được hủy miễn phí. Với booking đã xác nhận, "
                    "phí hủy phụ thuộc chính sách đã chốt trên chính booking và thời điểm hủy."
                )
            )
            return answer + " Tôi có thể tra cứu booking cụ thể nếu bạn cung cấp mã RSV.", []

        if any(
            phrase in normalized for phrase in ("gio check in", "gio check-in", "gio nhan phong", "gio tra phong", "check out", "check-out")
        ):
            source = self._best_source(sources, "nhan va tra phong")
            return (
                source.content if source else "Giờ nhận phòng tiêu chuẩn là 14:00 và giờ trả phòng là 12:00.",
                [],
            )

        if any(word in normalized for word in ("tien nghi", "dich vu", "wifi", "ho boi", "bua sang", "giat ui", "minibar")):
            source = self._best_source(sources, "tien nghi")
            return (
                source.content if source else "Khách sạn có Wi-Fi, điều hòa và các dịch vụ tùy theo loại phòng.",
                [],
            )

        user_messages = [item["content"] for item in history if item["role"] == "user"]
        search_markers = ("tim phong", "can phong", "phong cho", "phong trong", "gia phong", "dat phong")
        anchor = next(
            (
                index
                for index in range(len(user_messages) - 1, -1, -1)
                if any(marker in self._normalize(user_messages[index]) for marker in search_markers)
            ),
            None,
        )
        if any(marker in normalized for marker in search_markers) or (
            anchor is not None and re.fullmatch(r"\s*\d{4}-\d{2}-\d{2}(?:\s+\d{4}-\d{2}-\d{2})?\s*", message)
        ):
            context_messages = user_messages[anchor:] if anchor is not None else [message]
            context = " ".join(context_messages)
            dates = list(dict.fromkeys(re.findall(r"\d{4}-\d{2}-\d{2}", context)))
            people = re.search(r"(\d+)\s*(?:nguoi|khach)", self._normalize(context))
            room_count = re.search(r"(\d+)\s*phong", self._normalize(context))
            if not dates:
                return "Bạn muốn nhận phòng ngày nào? Hãy nhập theo dạng YYYY-MM-DD.", []
            if len(dates) == 1:
                return f"Tôi đã ghi nhận ngày nhận {dates[0]}. Bạn muốn trả phòng ngày nào?", []
            try:
                check_in = date.fromisoformat(dates[-2])
                check_out = date.fromisoformat(dates[-1])
            except ValueError:
                return "Ngày chưa hợp lệ. Vui lòng dùng định dạng YYYY-MM-DD.", []
            if check_in < date.today():
                return "Ngày nhận phòng đã qua. Vui lòng chọn ngày từ hôm nay trở đi.", []
            if check_out <= check_in:
                return "Ngày trả phòng phải sau ngày nhận phòng. Bạn vui lòng chọn lại ngày trả.", []
            context_dest = self._detect_destination(context) or destination_code
            request = RecommendationRequest(
                hotel_id=1,
                check_in=check_in,
                check_out=check_out,
                adults=int(people.group(1)) if people else 1,
                rooms=int(room_count.group(1)) if room_count else 1,
                destination_code=context_dest,
            )
            rooms = await backend.search_rooms(request)
            if not rooms:
                return (
                    f"Tôi đã kiểm tra từ {check_in} đến {check_out} nhưng chưa còn phòng phù hợp. Bạn muốn đổi ngày hay giảm số phòng?",
                    ["search_available_rooms"],
                )
            lines = [
                f"- {room['name']} ({room.get('propertyName') or 'HotelAI Việt Nam'}): còn {room.get('availableRooms', 0)} phòng, tổng {room['totalAmount']:,.0f} VND"
                for room in rooms[:3]
            ]
            return (
                f"Tôi đã kiểm tra tồn và giá thật từ {check_in} đến {check_out}:\n" + "\n".join(lines),
                ["search_available_rooms"],
            )

        relevant = [source for source in sources if source.score >= 0.60]
        if relevant:
            return relevant[0].content, []
        return (
            "Tôi chưa có đủ thông tin đã kiểm chứng để trả lời câu này. "
            "Bạn có thể hỏi về chính sách hủy, giờ nhận/trả phòng, tiện nghi, tìm phòng hoặc trạng thái booking.",
            [],
        )

    async def _history(self, session_id: UUID, limit: int = 30) -> list[dict[str, str]]:
        async with db.acquire() as connection:
            rows = await connection.fetch(
                "SELECT role,content FROM chat_messages WHERE session_id=$1 ORDER BY created_at DESC LIMIT $2",
                session_id,
                limit,
            )
        return [dict(row) for row in reversed(rows)]

    @staticmethod
    def _normalize(value: str) -> str:
        decomposed = unicodedata.normalize("NFD", value.lower().replace("đ", "d"))
        return " ".join("".join(character for character in decomposed if unicodedata.category(character) != "Mn").split())

    def _best_source(self, sources: list, keyword: str):
        normalized_keyword = self._normalize(keyword)
        return next(
            (source for source in sources if normalized_keyword in self._normalize(source.title + " " + source.content)),
            None,
        )

    async def _store(self, session_id: UUID, role: str, content: str, grounded: bool = False) -> None:
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
                grounded,
            )


chat = ChatService()
