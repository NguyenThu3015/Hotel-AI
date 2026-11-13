"use client";

import { api } from "@/lib/api";
import type { DestinationCatalogItem } from "@/lib/types";
import { viGuestText, viLabel } from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";
import { useEffect, useState } from "react";

interface Dashboard {
  revenue: number;
  bookings: number;
  cancellationRate: number;
  occupancyRate: number;
  adr: number;
  revpar: number;
  dailyRevenue: Array<{ day?: string; revenue?: number }>;
  popularServices: Array<Record<string, unknown>>;
  popularRoomTypes: Array<{ name: string; bookings: number; revenue: number }>;
  bookingSources: Array<{ source: string; bookings: number }>;
}
interface ForecastItem {
  date: string;
  predictedRooms: number;
  predictedOccupancy: number;
  recommendedPriceMultiplier: number;
  confidence: number;
}
interface Anomaly {
  type: string;
  severity: string;
  entityId: string | number;
  message: string;
  score: number;
}

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

export function AdminOverview() {
  const [dashboard, setDashboard] = useState<Dashboard>();
  const [forecast, setForecast] = useState<ForecastItem[]>([]);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  const [destinations, setDestinations] = useState<DestinationCatalogItem[]>(
    [],
  );
  const [risk, setRisk] = useState("LOW");
  const [error, setError] = useState("");
  useFlashNotifications(error);

  async function downloadReport(format: "pdf" | "xlsx") {
    try {
      setError("");
      const blob = await api.download(
        `/admin/reports/export.${format}?hotelId=1&from=2026-01-01&to=2026-12-31`,
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `hotel-report-2026.${format}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  useEffect(() => {
    const now = new Date();
    const to = now.toISOString().slice(0, 10);
    const from = new Date(now.getTime() - 30 * 86400000)
      .toISOString()
      .slice(0, 10);
    Promise.all([
      api.get<Dashboard>(
        `/admin/reports/dashboard?hotelId=1&from=${from}&to=${to}`,
      ),
      api.post<{ items: ForecastItem[] }>("/ai/forecast", {
        hotel_id: 1,
        days: 14,
        history_days: 90,
      }),
      api.post<{ riskLevel: string; items: Anomaly[] }>("/ai/anomalies", {
        hotel_id: 1,
        lookback_days: 30,
      }),
      api
        .get<DestinationCatalogItem[]>("/availability/destinations")
        .catch(() => []),
    ])
      .then(([metrics, demand, anomalyResult, destData]) => {
        setDashboard(metrics);
        setForecast(demand.items);
        setAnomalies(anomalyResult.items);
        setRisk(anomalyResult.riskLevel);
        setDestinations(destData);
      })
      .catch((reason) => setError((reason as Error).message));
  }, []);

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>HIỆU SUẤT 30 NGÀY</span>
          <h1>Tổng quan kinh doanh</h1>
          <p>
            Chỉ số vận hành và tín hiệu AI được cập nhật từ dữ liệu giao dịch
            thực.
          </p>
        </div>
        <div className="export-actions">
          <button
            className="secondary button-link"
            onClick={() => void downloadReport("pdf")}
          >
            PDF
          </button>
          <button
            className="primary button-link"
            onClick={() => void downloadReport("xlsx")}
          >
            Xuất Excel
          </button>
        </div>
      </div>
      {dashboard && (
        <div className="metric-grid metric-grid-wide">
          <article className="metric-featured">
            <span>Doanh thu</span>
            <strong>{money(dashboard.revenue)}</strong>
            <small>Booking đã ghi nhận</small>
          </article>
          <article>
            <span>Lấp đầy</span>
            <strong>{dashboard.occupancyRate}%</strong>
            <small>Công suất phòng</small>
          </article>
          <article>
            <span>Giá phòng trung bình</span>
            <strong>{money(dashboard.adr)}</strong>
            <small>Giá phòng bình quân</small>
          </article>
          <article>
            <span>Doanh thu mỗi phòng</span>
            <strong>{money(dashboard.revpar)}</strong>
            <small>Doanh thu/phòng</small>
          </article>
          <article>
            <span>Booking</span>
            <strong>{dashboard.bookings}</strong>
            <small>Tỷ lệ hủy {dashboard.cancellationRate}%</small>
          </article>
        </div>
      )}
      <div className="insight-grid">
        <section className="panel forecast-panel">
          <div className="panel-heading">
            <div>
              <span>DỰ BÁO NHU CẦU</span>
              <h2>14 ngày tới</h2>
            </div>
            <small>
              Độ tin cậy {Math.round((forecast[0]?.confidence ?? 0) * 100)}%
            </small>
          </div>
          <div className="forecast-chart">
            {forecast.map((item) => (
              <div
                className="forecast-column"
                key={item.date}
                title={`${item.predictedOccupancy}% · hệ số giá ${item.recommendedPriceMultiplier}`}
                aria-label={`${new Date(item.date).toLocaleDateString("vi-VN")}: công suất dự báo ${Math.round(item.predictedOccupancy)}%, dự kiến ${item.predictedRooms} phòng`}
              >
                <span>{Math.round(item.predictedOccupancy)}%</span>
                <i
                  style={{
                    height: `${Math.max(18, Math.min(180, item.predictedOccupancy * 1.8))}px`,
                  }}
                />
                <small>
                  {new Date(item.date).toLocaleDateString("vi-VN", {
                    day: "2-digit",
                    month: "2-digit",
                  })}
                </small>
              </div>
            ))}
          </div>
          <p className="muted">
            Cột cao biểu thị nhu cầu lớn. Hệ thống đề xuất hệ số giá 0,9–1,3
            theo công suất dự báo.
          </p>
        </section>
        <section className="panel anomaly-panel">
          <div className="panel-heading">
            <div>
              <span>CẢNH BÁO BẤT THƯỜNG</span>
              <h2>Mức rủi ro: {viLabel(risk)}</h2>
            </div>
            <span className={`risk risk-${risk.toLowerCase()}`}>
              {anomalies.length} cảnh báo
            </span>
          </div>
          {anomalies.length === 0 ? (
            <div className="healthy-state">
              <strong>Không phát hiện bất thường</strong>
              <p>Thanh toán và tỷ lệ hủy đang trong ngưỡng bình thường.</p>
            </div>
          ) : (
            <div className="anomaly-list">
              {anomalies.slice(0, 6).map((item) => (
                <article key={`${item.type}-${item.entityId}`}>
                  <span>{viLabel(item.severity)}</span>
                  <div>
                    <strong>{viLabel(item.type)}</strong>
                    <p>{viGuestText(item.message)}</p>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
      {dashboard && (
        <div className="report-breakdown">
          <section className="panel">
            <div className="panel-heading">
              <div>
                <span>LOẠI PHÒNG</span>
                <h2>Được chọn nhiều</h2>
              </div>
            </div>
            <ul className="simple-list">
              {dashboard.popularRoomTypes.map((item) => (
                <li key={item.name}>
                  <span>{item.name}</span>
                  <strong>{item.bookings} booking</strong>
                </li>
              ))}
            </ul>
          </section>
          <section className="panel">
            <div className="panel-heading">
              <div>
                <span>NGUỒN KHÁCH</span>
                <h2>Kênh đặt phòng</h2>
              </div>
            </div>
            <ul className="simple-list">
              {dashboard.bookingSources.map((item) => (
                <li key={item.source}>
                  <span>{item.source}</span>
                  <strong>{item.bookings}</strong>
                </li>
              ))}
            </ul>
          </section>
          <section className="panel">
            <div className="panel-heading">
              <div>
                <span>DỊCH VỤ</span>
                <h2>Phổ biến</h2>
              </div>
            </div>
            <ul className="simple-list">
              {dashboard.popularServices.map((item, index) => (
                <li key={index}>
                  <span>{String(item.name ?? "Dịch vụ")}</span>
                  <strong>{String(item.quantity ?? 0)}</strong>
                </li>
              ))}
            </ul>
          </section>
          {destinations.length > 0 && (
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <span>CHUỖI ĐIỂM ĐẾN VIỆT NAM</span>
                  <h2>{destinations.length} cơ sở 3 miền</h2>
                </div>
              </div>
              <ul className="simple-list">
                {destinations.map((dest) => (
                  <li key={dest.code}>
                    <span>
                      📍 {dest.name} ({dest.regionLabel})
                    </span>
                    <strong>
                      {dest.availableRooms} phòng trống · {dest.roomTypeCount} hạng
                    </strong>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </>
  );
}
