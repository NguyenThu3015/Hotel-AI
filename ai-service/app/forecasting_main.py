from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.analytics import detect_anomalies, forecast, forecast_backtest
from app.database import analytics_db
from app.schemas import AnomalyRequest, ForecastBacktestRequest, ForecastRequest


@asynccontextmanager
async def lifespan(_: FastAPI):
    await analytics_db.connect()
    yield
    await analytics_db.close()


app = FastAPI(title="AI Forecasting Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "UP", "service": "ai-forecasting-service"}


@app.post("/api/v1/forecast")
async def demand_forecast(request: ForecastRequest):
    return await forecast(request)


@app.post("/api/v1/anomalies")
async def anomaly_detection(request: AnomalyRequest):
    return await detect_anomalies(request)


@app.post("/api/v1/forecast/backtest")
async def forecast_model_backtest(request: ForecastBacktestRequest):
    return await forecast_backtest(request)
