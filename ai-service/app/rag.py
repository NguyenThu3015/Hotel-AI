import json
import re
import unicodedata

from app.database import db
from app.embeddings import embeddings
from app.schemas import Source


def chunks(text: str, size: int = 700, overlap: int = 100) -> list[str]:
    paragraphs = [part.strip() for part in text.splitlines() if part.strip()]
    result: list[str] = []
    current = ""
    for paragraph in paragraphs:
        if len(current) + len(paragraph) + 1 > size and current:
            result.append(current)
            current = current[-overlap:] + " " + paragraph
        else:
            current = f"{current} {paragraph}".strip()
    if current:
        result.append(current)
    return result or [text[:size]]


def vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in vector) + "]"


class RagService:
    async def index_document(
        self,
        title: str,
        content: str,
        source: str | None,
        document_type: str,
        audience: str = "PUBLIC",
        allowed_roles: list[str] | None = None,
    ) -> int:
        normalized_roles = sorted({role.strip().upper() for role in allowed_roles or [] if role.strip()})
        async with db.acquire() as connection:
            async with connection.transaction():
                document_id = None
                if source:
                    document_id = await connection.fetchval(
                        "SELECT id FROM documents WHERE source=$1 AND active ORDER BY updated_at DESC LIMIT 1",
                        source,
                    )
                if document_id:
                    await connection.execute(
                        "UPDATE documents SET title=$2,document_type=$3,content=$4,audience=$5,"
                        "allowed_roles=$6::jsonb,updated_at=CURRENT_TIMESTAMP "
                        "WHERE id=$1",
                        document_id,
                        title,
                        document_type,
                        content,
                        audience,
                        json.dumps(normalized_roles),
                    )
                    await connection.execute("DELETE FROM document_chunks WHERE document_id=$1", document_id)
                else:
                    document_id = await connection.fetchval(
                        "INSERT INTO documents(title,source,document_type,content,audience,allowed_roles) "
                        "VALUES($1,$2,$3,$4,$5,$6::jsonb) RETURNING id",
                        title,
                        source,
                        document_type,
                        content,
                        audience,
                        json.dumps(normalized_roles),
                    )
                for index, chunk in enumerate(chunks(content)):
                    vector = await embeddings.embed(chunk, "document")
                    await connection.execute(
                        "INSERT INTO document_chunks(document_id,chunk_index,content,embedding,metadata) "
                        "VALUES($1,$2,$3,$4::vector,$5::jsonb)",
                        document_id,
                        index,
                        chunk,
                        vector_literal(vector),
                        json.dumps({"title": title, "source": source, "embedding_model": embeddings.model_key}),
                    )
                return document_id

    async def index_missing_documents(self) -> None:
        async with db.acquire() as connection:
            rows = await connection.fetch(
                "SELECT d.id,d.title,d.source,d.content FROM documents d WHERE d.active AND NOT EXISTS("
                "SELECT 1 FROM document_chunks c WHERE c.document_id=d.id "
                "AND c.metadata->>'embedding_model'=$1)",
                embeddings.model_key,
            )
            for row in rows:
                await connection.execute("DELETE FROM document_chunks WHERE document_id=$1", row["id"])
                for index, chunk in enumerate(chunks(row["content"])):
                    vector = await embeddings.embed(chunk, "document")
                    await connection.execute(
                        "INSERT INTO document_chunks(document_id,chunk_index,content,embedding,metadata) "
                        "VALUES($1,$2,$3,$4::vector,$5::jsonb) ON CONFLICT DO NOTHING",
                        row["id"],
                        index,
                        chunk,
                        vector_literal(vector),
                        json.dumps(
                            {
                                "title": row["title"],
                                "source": row["source"],
                                "embedding_model": embeddings.model_key,
                            }
                        ),
                    )

    async def search(
        self,
        query: str,
        limit: int = 4,
        audience: str = "PUBLIC",
        roles: list[str] | None = None,
    ) -> list[Source]:
        vector = await embeddings.embed(query, "query")
        async with db.acquire() as connection:
            rows = await connection.fetch(
                "SELECT d.title,c.content,1-(c.embedding <=> $1::vector) AS score "
                "FROM document_chunks c JOIN documents d ON d.id=c.document_id "
                "WHERE d.active AND c.embedding IS NOT NULL "
                "AND (d.audience='PUBLIC' OR ($3='STAFF' AND d.audience='STAFF' AND "
                "(d.allowed_roles='[]'::jsonb OR d.allowed_roles ?| $4::text[]))) "
                "ORDER BY c.embedding <=> $1::vector LIMIT $2",
                vector_literal(vector),
                limit,
                audience,
                roles or [],
            )
        return [Source(title=row["title"], content=row["content"], score=max(0.0, float(row["score"]))) for row in rows]

    async def keyword_search(
        self,
        query: str,
        limit: int = 4,
        audience: str = "PUBLIC",
        roles: list[str] | None = None,
    ) -> list[Source]:
        """Keep policy lookup available when an external embedding API is unavailable."""
        query_terms = self._terms(query)
        if not query_terms:
            return []
        async with db.acquire() as connection:
            rows = await connection.fetch(
                "SELECT title,content FROM documents WHERE active "
                "AND (audience='PUBLIC' OR ($1='STAFF' AND audience='STAFF' AND "
                "(allowed_roles='[]'::jsonb OR allowed_roles ?| $2::text[]))) "
                "ORDER BY updated_at DESC LIMIT 100",
                audience,
                roles or [],
            )
        ranked: list[tuple[float, Source]] = []
        for row in rows:
            title_terms = self._terms(row["title"])
            content_terms = self._terms(row["content"])
            title_hits = len(query_terms & title_terms)
            content_hits = len(query_terms & content_terms)
            if not title_hits and not content_hits:
                continue
            score = min(0.85, 0.35 + title_hits * 0.15 + content_hits * 0.05)
            ranked.append(
                (
                    score,
                    Source(title=row["title"], content=row["content"], score=score),
                )
            )
        ranked.sort(key=lambda item: item[0], reverse=True)
        return [source for _, source in ranked[:limit]]

    @staticmethod
    def _terms(value: str) -> set[str]:
        decomposed = unicodedata.normalize("NFD", value.lower().replace("đ", "d"))
        normalized = "".join(character for character in decomposed if unicodedata.category(character) != "Mn")
        ignored = {
            "ban",
            "cho",
            "cua",
            "duoc",
            "gi",
            "khach",
            "khong",
            "la",
            "mot",
            "nhung",
            "the",
            "toi",
            "va",
            "voi",
        }
        return {term for term in re.findall(r"[a-z0-9]+", normalized) if len(term) > 1 and term not in ignored}


rag = RagService()
