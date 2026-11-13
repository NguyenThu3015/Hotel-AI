import hashlib
import math
import re

from google import genai
from google.genai import types
from openai import AsyncOpenAI

from app.config import get_settings
from app.privacy import redact_external_text


class EmbeddingService:
    dimensions = 1536

    def __init__(self) -> None:
        settings = get_settings()
        self.provider = settings.active_provider
        self.openai_model = settings.openai_embedding_model
        self.gemini_model = settings.gemini_embedding_model
        self.openai_client = AsyncOpenAI(api_key=settings.openai_api_key) if self.provider == "openai" and settings.openai_api_key else None
        self.gemini_client = (
            genai.Client(api_key=settings.gemini_api_key) if self.provider == "gemini" and settings.gemini_api_key else None
        )

    @property
    def model_key(self) -> str:
        if self.gemini_client:
            return f"gemini:{self.gemini_model}:{self.dimensions}"
        if self.openai_client:
            return f"openai:{self.openai_model}:{self.dimensions}"
        return f"local:sha256:{self.dimensions}"

    async def embed(self, text: str, purpose: str = "query") -> list[float]:
        safe_text = redact_external_text(text)
        if self.gemini_client:
            instruction = (
                "Represent this hotel policy document for semantic retrieval:\n"
                if purpose == "document"
                else "Represent this hotel guest question for retrieving the best policy answer:\n"
            )
            response = await self.gemini_client.aio.models.embed_content(
                model=self.gemini_model,
                contents=instruction + safe_text,
                config=types.EmbedContentConfig(output_dimensionality=self.dimensions),
            )
            values = response.embeddings[0].values if response.embeddings else None
            if not values:
                raise RuntimeError("Gemini returned no embedding")
            return list(values)
        if self.openai_client:
            response = await self.openai_client.embeddings.create(
                model=self.openai_model,
                input=safe_text,
                dimensions=self.dimensions,
            )
            return response.data[0].embedding
        return self._local_embedding(safe_text)

    def _local_embedding(self, text: str) -> list[float]:
        vector = [0.0] * self.dimensions
        for token in re.findall(r"\w+", text.lower(), flags=re.UNICODE):
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.dimensions
            vector[index] += -1.0 if digest[4] & 1 else 1.0
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]


embeddings = EmbeddingService()
