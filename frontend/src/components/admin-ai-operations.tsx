"use client";

import { useAuth } from "@/components/auth-provider";
import { api } from "@/lib/api";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { friendlyError, viLabel } from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";

interface RoomTypeOption {
  id: string;
  name: string;
  code: string;
  destinationCode?: string;
  destinationName?: string;
  propertyName?: string;
}

interface PriceConstraint {
  roomTypeId: string;
  floorPrice: number;
  ceilingPrice: number;
  version: number;
}

interface PriceProposal {
  id: string;
  roomTypeId: string;
  stayDate: string;
  currentPrice: number;
  recommendedPrice: number;
  floorPrice: number;
  ceilingPrice: number;
  proposalVersion: number;
  modelVersion: string;
  status: string;
  appliedRuleId?: string;
  explanation: {
    predictedOccupancy: number;
    forecastConfidence: number;
    multiplier: number;
    boundedByFloor: boolean;
    boundedByCeiling: boolean;
  };
}

interface BookingRisk {
  id?: string;
  reservationId: string;
  reservationReference: string;
  cancellationProbability: number;
  noShowProbability: number;
  riskLevel: string;
  explanations: string[];
  recommendedAction: string;
  modelVersion: string;
}

interface AssistantMessage {
  role: "user" | "assistant";
  content: string;
  meta?: string;
}

interface AssistantResponse {
  session_id: string;
  answer: string;
  sources: Array<{ title: string; content: string; score: number }>;
  tools_used: string[];
  provider: string;
  degraded: boolean;
}

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

const isoDate = (offset: number) => {
  const value = new Date(Date.now() + offset * 86400000);
  return value.toISOString().slice(0, 10);
};

export function AdminAiOperations() {
  const { user } = useAuth();
  const canManageRevenue = Boolean(
    user?.roles.some((role) => ["ADMIN", "MANAGER"].includes(role)),
  );
  const [roomTypes, setRoomTypes] = useState<RoomTypeOption[]>([]);
  const [constraints, setConstraints] = useState<PriceConstraint[]>([]);
  const [proposals, setProposals] = useState<PriceProposal[]>([]);
  const [risks, setRisks] = useState<BookingRisk[]>([]);
  const [constraintForm, setConstraintForm] = useState({
    roomTypeId: "",
    floorPrice: "",
    ceilingPrice: "",
  });
  const [range, setRange] = useState({ from: isoDate(1), to: isoDate(14) });
  const [comments, setComments] = useState<Record<string, string>>({});
  const [messages, setMessages] = useState<AssistantMessage[]>([
    {
      role: "assistant",
      content:
        "Chào bạn. Tôi có thể tra cứu booking, hồ sơ khách được phép xem và chính sách nội bộ theo vai trò của bạn.",
    },
  ]);
  const [sessionId, setSessionId] = useState<string>();
  const [assistantInput, setAssistantInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [assistantLoading, setAssistantLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useFlashNotifications(error, notice);

  const roomNames = useMemo(
    () =>
      Object.fromEntries(
        roomTypes.map((item) => [
          item.id,
          `[${item.destinationName ?? "Phú Quốc"}] ${item.name}`,
        ]),
      ),
    [roomTypes],
  );

  async function load() {
    setError("");
    const [roomResult, constraintResult, proposalResult, riskResult] =
      await Promise.allSettled([
        api.get<RoomTypeOption[]>("/catalog/room-types"),
        canManageRevenue
          ? api.get<PriceConstraint[]>("/ai/revenue/constraints")
          : Promise.resolve([]),
        canManageRevenue
          ? api.get<PriceProposal[]>("/ai/revenue/proposals")
          : Promise.resolve([]),
        api.get<BookingRisk[]>("/ai/risks/bookings?min_probability=0.25"),
      ]);
    if (roomResult.status === "fulfilled") {
      setRoomTypes(roomResult.value);
      setConstraintForm((current) => ({
        ...current,
        roomTypeId: current.roomTypeId || roomResult.value[0]?.id || "",
      }));
    }
    if (constraintResult.status === "fulfilled")
      setConstraints(constraintResult.value);
    if (proposalResult.status === "fulfilled") setProposals(proposalResult.value);
    if (riskResult.status === "fulfilled") setRisks(riskResult.value);
    if (
      [roomResult, constraintResult, proposalResult, riskResult].every(
        (result) => result.status === "rejected",
      )
    )
      setError("Không thể tải dữ liệu vận hành AI.");
  }

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canManageRevenue]);

  async function saveConstraint(event: FormEvent) {
    event.preventDefault();
    const existing = constraints.find(
      (item) => item.roomTypeId === constraintForm.roomTypeId,
    );
    await perform(async () => {
      await api.put(`/ai/revenue/constraints/${constraintForm.roomTypeId}`, {
        room_type_id: constraintForm.roomTypeId,
        floor_price: Number(constraintForm.floorPrice),
        ceiling_price: Number(constraintForm.ceilingPrice),
        expected_version: existing?.version,
      });
    }, "Đã lưu giá sàn/trần và phiên bản ràng buộc mới.");
  }

  async function generateProposals() {
    await perform(async () => {
      await api.post("/ai/revenue/proposals/generate", {
        hotel_id: 1,
        date_from: range.from,
        date_to: range.to,
      });
    }, "Đã tạo đề xuất giá mới. Giá chưa được áp dụng cho tới khi phê duyệt.");
  }

  async function decide(proposal: PriceProposal, approved: boolean) {
    const comment = comments[proposal.id]?.trim();
    if (!comment || comment.length < 3) {
      setError("Cần nhập lý do phê duyệt hoặc từ chối (tối thiểu 3 ký tự).");
      return;
    }
    await perform(
      () =>
        api.post(
          `/ai/revenue/proposals/${proposal.id}/${approved ? "approve" : "reject"}`,
          { comment },
        ),
      approved
        ? "Đã phê duyệt và công bố mức giá mới."
        : "Đã từ chối đề xuất giá; lịch sử phiên bản vẫn được giữ lại.",
    );
  }

  async function scoreRisks() {
    await perform(
      () => api.post("/ai/risks/bookings/score", { hotel_id: 1, horizon_days: 30 }),
      "Đã tính lại nguy cơ hủy và không đến cho các đặt phòng trong 30 ngày tới.",
    );
  }

  async function perform(action: () => Promise<unknown>, message: string) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(message);
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function sendToAssistant(event: FormEvent) {
    event.preventDefault();
    const message = assistantInput.trim();
    if (!message || assistantLoading) return;
    setMessages((current) => [...current, { role: "user", content: message }]);
    setAssistantInput("");
    setAssistantLoading(true);
    try {
      const response = await api.post<AssistantResponse>(
        "/ai/staff-assistant/chat",
        { message, session_id: sessionId },
      );
      setSessionId(response.session_id);
      const details = [
        "Trợ lý nội bộ",
        response.degraded ? "chế độ hỗ trợ cơ bản" : "phân tích nâng cao",
        response.tools_used.length
          ? "đã đối chiếu dữ liệu theo quyền truy cập"
          : "đã kiểm tra nguồn thông tin nội bộ",
      ].join(" · ");
      setMessages((current) => [
        ...current,
        { role: "assistant", content: response.answer, meta: details },
      ]);
    } catch (reason) {
      setMessages((current) => [
        ...current,
        { role: "assistant", content: friendlyError(reason) },
      ]);
    } finally {
      setAssistantLoading(false);
    }
  }

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>ĐỀ XUẤT GIÁ, RỦI RO VÀ TRỢ LÝ NHÂN VIÊN</span>
          <h1>AI vận hành có kiểm soát</h1>
          <p>
            Giá chỉ được áp dụng sau phê duyệt; rủi ro có giải thích; trợ lý chỉ
            đọc dữ liệu đúng quyền của nhân viên.
          </p>
        </div>
      </div>

      <div className="management-grid governance-grid">
        {canManageRevenue && (
          <section className="panel span-2">
            <div className="panel-heading">
              <div>
                <span>ĐỀ XUẤT GIÁ THÔNG MINH</span>
                <h2>Ràng buộc và đề xuất giá</h2>
              </div>
              <b>{proposals.filter((item) => item.status === "PENDING").length} chờ duyệt</b>
            </div>
            <form className="form-grid compact-form" onSubmit={saveConstraint}>
              <label>
                Loại phòng
                <select
                  required
                  value={constraintForm.roomTypeId}
                  onChange={(event) => {
                    const roomTypeId = event.target.value;
                    const existing = constraints.find(
                      (item) => item.roomTypeId === roomTypeId,
                    );
                    setConstraintForm({
                      roomTypeId,
                      floorPrice: existing ? String(existing.floorPrice) : "",
                      ceilingPrice: existing ? String(existing.ceilingPrice) : "",
                    });
                  }}
                >
                  {roomTypes.map((item) => (
                    <option key={item.id} value={item.id}>
                      [{item.destinationName ?? "Phú Quốc"}] {item.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Giá sàn
                <input
                  required
                  min="1"
                  type="number"
                  value={constraintForm.floorPrice}
                  onChange={(event) =>
                    setConstraintForm({
                      ...constraintForm,
                      floorPrice: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                Giá trần
                <input
                  required
                  min="1"
                  type="number"
                  value={constraintForm.ceilingPrice}
                  onChange={(event) =>
                    setConstraintForm({
                      ...constraintForm,
                      ceilingPrice: event.target.value,
                    })
                  }
                />
              </label>
              <button className="secondary" disabled={loading}>Lưu ràng buộc</button>
            </form>
            <div className="form-grid compact-form ai-date-range">
              <label>
                Từ ngày
                <input
                  type="date"
                  min={isoDate(0)}
                  value={range.from}
                  onChange={(event) => setRange({ ...range, from: event.target.value })}
                />
              </label>
              <label>
                Đến ngày
                <input
                  type="date"
                  min={range.from}
                  value={range.to}
                  onChange={(event) => setRange({ ...range, to: event.target.value })}
                />
              </label>
              <button
                className="primary"
                disabled={loading || constraints.length === 0}
                onClick={() => void generateProposals()}
              >
                Tạo đề xuất, chưa áp dụng
              </button>
            </div>
            {constraints.length === 0 && (
              <p className="muted">Hãy cấu hình ít nhất một giá sàn/trần trước.</p>
            )}
            <div className="review-list">
              {proposals.slice(0, 100).map((proposal) => (
                <article key={proposal.id}>
                  <div className="review-score">
                    <strong>v{proposal.proposalVersion}</strong>
                    <small>{viLabel(proposal.status)}</small>
                  </div>
                  <div>
                    <div className="review-meta">
                      <b>{roomNames[proposal.roomTypeId] ?? proposal.roomTypeId}</b>
                      <span>{proposal.stayDate}</span>
                      <span className="topic">
                        dự báo {Math.round(proposal.explanation.predictedOccupancy)}%
                      </span>
                    </div>
                    <p>
                      {money(proposal.currentPrice)} →{" "}
                      <strong>{money(proposal.recommendedPrice)}</strong> · sàn{" "}
                      {money(proposal.floorPrice)} · trần {money(proposal.ceilingPrice)}
                    </p>
                    <small>Độ tin cậy {Math.round(proposal.explanation.forecastConfidence * 100)}%</small>
                  </div>
                  {proposal.status === "PENDING" ? (
                    <div className="proposal-decision">
                      <input
                        placeholder="Lý do quyết định"
                        value={comments[proposal.id] ?? ""}
                        onChange={(event) =>
                          setComments({ ...comments, [proposal.id]: event.target.value })
                        }
                      />
                      <div className="row-actions">
                        <button disabled={loading} onClick={() => void decide(proposal, true)}>Phê duyệt</button>
                        <button disabled={loading} onClick={() => void decide(proposal, false)}>Từ chối</button>
                      </div>
                    </div>
                  ) : (
                    <span className="status">
                      {proposal.appliedRuleId ? "Đã công bố giá" : viLabel(proposal.status)}
                    </span>
                  )}
                </article>
              ))}
            </div>
          </section>
        )}

        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>CẢNH BÁO NGUY CƠ HỦY</span>
              <h2>Khả năng hủy hoặc không đến nhận phòng</h2>
            </div>
            <button className="secondary" disabled={loading} onClick={() => void scoreRisks()}>
              Chấm lại 30 ngày
            </button>
          </div>
          <div className="review-list">
            {risks.map((item) => (
              <article key={item.reservationId}>
                <div className="review-score">
                  <strong>
                    {Math.round(Math.max(item.cancellationProbability, item.noShowProbability) * 100)}%
                  </strong>
                  <small>{viLabel(item.riskLevel)}</small>
                </div>
                <div>
                  <div className="review-meta">
                    <b>{item.reservationReference}</b>
                    <span>hủy {Math.round(item.cancellationProbability * 100)}%</span>
                    <span>không đến {Math.round(item.noShowProbability * 100)}%</span>
                  </div>
                  <p>{item.explanations.join(" · ")}</p>
                  <small>{item.recommendedAction}</small>
                </div>
              </article>
            ))}
            {risks.length === 0 && (
              <p className="muted">Chưa có đặt phòng nào vượt ngưỡng rủi ro 25%.</p>
            )}
          </div>
        </section>

        <section className="panel span-2 staff-assistant-panel">
          <div className="panel-heading">
            <div>
              <span>HỖ TRỢ THEO QUYỀN TRUY CẬP</span>
              <h2>Trợ lý riêng cho nhân viên</h2>
            </div>
            <small>{sessionId ? "Đang giữ ngữ cảnh hội thoại" : "Phiên mới"}</small>
          </div>
          <div className="staff-assistant-messages">
            {messages.map((message, index) => (
              <article key={`${message.role}-${index}`} className={message.role}>
                <p>{message.content}</p>
                {message.meta && <small>{message.meta}</small>}
              </article>
            ))}
            {assistantLoading && (
              <article className="assistant"><p>Đang đối chiếu dữ liệu…</p></article>
            )}
          </div>
          <form className="staff-assistant-form" onSubmit={sendToAssistant}>
            <input
              value={assistantInput}
              onChange={(event) => setAssistantInput(event.target.value)}
              placeholder="Ví dụ: đặt phòng BK-2026-001 cần chuẩn bị gì trước khi khách đến?"
            />
            <button className="primary" disabled={assistantLoading}>Gửi</button>
          </form>
        </section>
      </div>
    </>
  );
}
