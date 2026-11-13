from contextlib import asynccontextmanager

import asyncpg

from app.config import get_settings


class Database:
    def __init__(self, url_setting: str = "database_url") -> None:
        self.pool: asyncpg.Pool | None = None
        self.url_setting = url_setting

    async def connect(self) -> None:
        if self.pool is None:
            settings = get_settings()
            database_url = getattr(settings, self.url_setting) or settings.database_url
            self.pool = await asyncpg.create_pool(database_url, min_size=1, max_size=5)

    async def close(self) -> None:
        if self.pool:
            await self.pool.close()
            self.pool = None

    @asynccontextmanager
    async def acquire(self):
        if not self.pool:
            await self.connect()
        assert self.pool is not None
        async with self.pool.acquire() as connection:
            yield connection


db = Database()
analytics_db = Database("analytics_database_url")
revenue_db = Database("revenue_database_url")
customer_analytics_db = Database("customer_analytics_database_url")
