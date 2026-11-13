"use client";

import { api } from "@/lib/api";
import { FormEvent, useEffect, useState } from "react";
import { viLabel } from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";

type PolicyType =
  | "CANCELLATION"
  | "NO_SHOW"
  | "DEPOSIT"
  | "FEES"
  | "CHECK_IN_OUT"
  | "CHILD"
  | "SURCHARGE";

interface PolicyVersion {
  id: string;
  policyKey: string;
  version: number;
  policyType: PolicyType;
  scopeType: "GLOBAL" | "ROOM_TYPE";
  scopeId?: string;
  name: string;
  guestDisplayText: string;
  parameters: Record<string, unknown>;
  effectiveFrom: string;
  effectiveTo?: string;
}

interface ImpactPreview {
  conflictingPolicyVersionIds: string[];
  currentlyEffectivePolicyVersionIds: string[];
  existingReservationSnapshotsRemainUnchanged: boolean;
  affectedFutureOperations: string[];
  limitation: string;
}

const parameterTemplates: Record<PolicyType, Record<string, unknown>> = {
  CANCELLATION: {
    freeCancellationHours: 24,
    latePenaltyType: "PERCENT",
    latePenaltyValue: 100,
  },
  NO_SHOW: { penaltyType: "NIGHTS", penaltyValue: 1 },
  DEPOSIT: { required: true, depositType: "PERCENT", depositValue: 30 },
  FEES: { taxPercent: 8, serviceFeePercent: 5 },
  CHECK_IN_OUT: { checkInHour: 14, checkOutHour: 12 },
  CHILD: { maximumChildAge: 12, freeChildMaximumAge: 5 },
  SURCHARGE: { name: "Phụ thu", amount: 0 },
};

const parameterFields: Record<
  PolicyType,
  Array<{ key: string; label: string; kind?: "number" | "boolean" | "penalty" }>
> = {
  CANCELLATION: [
    { key: "freeCancellationHours", label: "Số giờ được hủy miễn phí", kind: "number" },
    { key: "latePenaltyType", label: "Cách tính phí hủy muộn", kind: "penalty" },
    { key: "latePenaltyValue", label: "Mức phí hủy muộn", kind: "number" },
  ],
  NO_SHOW: [
    { key: "penaltyType", label: "Cách tính phí khi khách không đến", kind: "penalty" },
    { key: "penaltyValue", label: "Mức phí", kind: "number" },
  ],
  DEPOSIT: [
    { key: "required", label: "Yêu cầu đặt cọc", kind: "boolean" },
    { key: "depositType", label: "Cách tính tiền cọc", kind: "penalty" },
    { key: "depositValue", label: "Mức đặt cọc", kind: "number" },
  ],
  FEES: [
    { key: "taxPercent", label: "Thuế (%)", kind: "number" },
    { key: "serviceFeePercent", label: "Phí dịch vụ (%)", kind: "number" },
  ],
  CHECK_IN_OUT: [
    { key: "checkInHour", label: "Giờ nhận phòng", kind: "number" },
    { key: "checkOutHour", label: "Giờ trả phòng", kind: "number" },
  ],
  CHILD: [
    { key: "maximumChildAge", label: "Tuổi tối đa tính là trẻ em", kind: "number" },
    { key: "freeChildMaximumAge", label: "Tuổi tối đa được miễn phí", kind: "number" },
  ],
  SURCHARGE: [
    { key: "name", label: "Tên khoản phụ thu" },
    { key: "amount", label: "Số tiền phụ thu", kind: "number" },
  ],
};

const toLocalDateTime = (date: Date) => {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return shifted.toISOString().slice(0, 16);
};

export function AdminPolicies() {
  const now = new Date();
  const [policies, setPolicies] = useState<PolicyVersion[]>([]);
  const [form, setForm] = useState({
    policyKey: "",
    policyType: "CANCELLATION" as PolicyType,
    scopeType: "GLOBAL" as "GLOBAL" | "ROOM_TYPE",
    scopeId: "",
    name: "Chính sách hủy tiêu chuẩn",
    guestDisplayText:
      "Hủy miễn phí trước giờ nhận phòng 24 giờ; sau thời hạn áp dụng phí theo giá trị booking.",
    parameters: JSON.stringify(parameterTemplates.CANCELLATION, null, 2),
    effectiveFrom: toLocalDateTime(now),
    effectiveTo: "",
  });
  const [preview, setPreview] = useState<ImpactPreview>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useFlashNotifications(error, notice);
  const [loading, setLoading] = useState(false);

  async function load() {
    setPolicies(await api.get<PolicyVersion[]>("/admin/pricing/policies"));
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load().catch((reason: Error) => setError(reason.message));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  function payload() {
    let parameters: Record<string, unknown>;
    try {
      parameters = JSON.parse(form.parameters) as Record<string, unknown>;
    } catch {
      throw new Error("Cấu hình chính sách chưa hợp lệ. Vui lòng kiểm tra lại.");
    }
    return {
      policyKey: form.policyKey || undefined,
      policyType: form.policyType,
      scopeType: form.scopeType,
      scopeId: form.scopeType === "ROOM_TYPE" ? form.scopeId : undefined,
      name: form.name,
      guestDisplayText: form.guestDisplayText,
      parameters,
      effectiveFrom: new Date(form.effectiveFrom).toISOString(),
      effectiveTo: form.effectiveTo
        ? new Date(form.effectiveTo).toISOString()
        : undefined,
    };
  }

  function updateParameter(key: string, value: string | number | boolean) {
    const current = JSON.parse(form.parameters) as Record<string, unknown>;
    setForm({ ...form, parameters: JSON.stringify({ ...current, [key]: value }) });
    setPreview(undefined);
  }

  async function checkImpact() {
    setLoading(true);
    setError("");
    try {
      const value = payload();
      setPreview(
        await api.post<ImpactPreview>(
          "/admin/pricing/policies/impact-preview",
          {
            policyType: value.policyType,
            scopeType: value.scopeType,
            scopeId: value.scopeId,
            effectiveFrom: value.effectiveFrom,
            effectiveTo: value.effectiveTo,
          },
        ),
      );
      setNotice("Đã mô phỏng xung đột và phạm vi tác động.");
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function indexPolicy(policy: PolicyVersion) {
    await api.post("/ai/knowledge", {
      title: `${policy.name} · phiên bản ${policy.version}`,
      content: [
        policy.guestDisplayText,
        `Loại: ${policy.policyType}`,
        `Phạm vi: ${policy.scopeType}${policy.scopeId ? ` ${policy.scopeId}` : ""}`,
        `Hiệu lực: ${policy.effectiveFrom} đến ${policy.effectiveTo || "không thời hạn"}`,
        `Tham số nghiệp vụ: ${JSON.stringify(policy.parameters)}`,
      ].join("\n"),
      source: `pricing-policy:${policy.id}`,
      document_type: "POLICY",
    });
  }

  async function publish(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const created = await api.post<PolicyVersion>(
        "/admin/pricing/policies",
        payload(),
        { "Idempotency-Key": crypto.randomUUID() },
      );
      await indexPolicy(created);
      setNotice(
        `Đã công bố phiên bản ${created.version} và đồng bộ vào kho tri thức AI.`,
      );
      setPreview(undefined);
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function reindex(policy: PolicyVersion) {
    setLoading(true);
    setError("");
    try {
      await indexPolicy(policy);
      setNotice(`Đã đồng bộ ${policy.name} phiên bản ${policy.version} vào kho tri thức.`);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  function newVersion(policy: PolicyVersion) {
    setForm({
      policyKey: policy.policyKey,
      policyType: policy.policyType,
      scopeType: policy.scopeType,
      scopeId: policy.scopeId ?? "",
      name: policy.name,
      guestDisplayText: policy.guestDisplayText,
      parameters: JSON.stringify(policy.parameters, null, 2),
      effectiveFrom: toLocalDateTime(new Date()),
      effectiveTo: "",
    });
    setPreview(undefined);
  }

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>CHÍNH SÁCH CÓ LỊCH SỬ PHIÊN BẢN</span>
          <h1>Chính sách khách sạn</h1>
          <p>
            Công bố theo phiên bản, kiểm tra giao nhau và đồng bộ cùng một nội
            dung cho báo giá và trợ lý khách sạn.
          </p>
        </div>
      </div>
      <div className="management-grid governance-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>SOẠN CHÍNH SÁCH</span>
              <h2>
                {form.policyKey ? "Phiên bản kế tiếp" : "Chính sách mới"}
              </h2>
            </div>
          </div>
          <form className="form-grid compact-form" onSubmit={publish}>
            <div className="form-row">
              <label>
                Loại
                <select
                  value={form.policyType}
                  onChange={(event) => {
                    const policyType = event.target.value as PolicyType;
                    setForm({
                      ...form,
                      policyType,
                      parameters: JSON.stringify(
                        parameterTemplates[policyType],
                        null,
                        2,
                      ),
                    });
                  }}
                >
                  {Object.keys(parameterTemplates).map((type) => (
                    <option key={type} value={type}>{viLabel(type)}</option>
                  ))}
                </select>
              </label>
              <label>
                Phạm vi
                <select
                  value={form.scopeType}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      scopeType: event.target.value as "GLOBAL" | "ROOM_TYPE",
                    })
                  }
                >
                  <option value="GLOBAL">Toàn khách sạn</option>
                  <option value="ROOM_TYPE">Theo loại phòng</option>
                </select>
              </label>
            </div>
            {form.scopeType === "ROOM_TYPE" && (
              <label>
                Mã loại phòng
                <input
                  required
                  value={form.scopeId}
                  onChange={(event) =>
                    setForm({ ...form, scopeId: event.target.value })
                  }
                />
              </label>
            )}
            <label>
              Tên nội bộ
              <input
                required
                value={form.name}
                onChange={(event) =>
                  setForm({ ...form, name: event.target.value })
                }
              />
            </label>
            <label>
              Nội dung hiển thị cho khách và AI
              <textarea
                required
                maxLength={4000}
                value={form.guestDisplayText}
                onChange={(event) =>
                  setForm({ ...form, guestDisplayText: event.target.value })
                }
              />
            </label>
            <fieldset>
              <legend>Điều kiện áp dụng</legend>
              <div className="policy-parameter-grid">
                {parameterFields[form.policyType].map((field) => {
                  const values = JSON.parse(form.parameters) as Record<string, unknown>;
                  const value = values[field.key];
                  return (
                    <label key={field.key}>
                      {field.label}
                      {field.kind === "boolean" ? (
                        <select value={String(value)} onChange={(event) => updateParameter(field.key, event.target.value === "true")}>
                          <option value="true">Có</option>
                          <option value="false">Không</option>
                        </select>
                      ) : field.kind === "penalty" ? (
                        <select value={String(value ?? "PERCENT")} onChange={(event) => updateParameter(field.key, event.target.value)}>
                          <option value="PERCENT">Theo phần trăm</option>
                          <option value="FIXED">Số tiền cố định</option>
                          <option value="NIGHTS">Theo số đêm</option>
                        </select>
                      ) : (
                        <input
                          type={field.kind === "number" ? "number" : "text"}
                          min={field.kind === "number" ? "0" : undefined}
                          value={String(value ?? "")}
                          onChange={(event) => updateParameter(field.key, field.kind === "number" ? Number(event.target.value) : event.target.value)}
                        />
                      )}
                    </label>
                  );
                })}
              </div>
            </fieldset>
            <div className="form-row">
              <label>
                Hiệu lực từ
                <input
                  type="datetime-local"
                  required
                  value={form.effectiveFrom}
                  onChange={(event) =>
                    setForm({ ...form, effectiveFrom: event.target.value })
                  }
                />
              </label>
              <label>
                Hiệu lực đến
                <input
                  type="datetime-local"
                  value={form.effectiveTo}
                  onChange={(event) =>
                    setForm({ ...form, effectiveTo: event.target.value })
                  }
                />
              </label>
            </div>
            <div className="row-actions">
              <button type="button" onClick={() => void checkImpact()}>
                Kiểm tra tác động
              </button>
              <button className="primary" disabled={loading || !preview}>
                Công bố và đồng bộ trợ lý
              </button>
            </div>
            {!preview && (
              <small>Phải kiểm tra tác động trước khi công bố.</small>
            )}
          </form>
          {preview && (
            <div className="policy-impact">
              <strong>Kết quả mô phỏng</strong>
              <p>
                Xung đột: {preview.conflictingPolicyVersionIds.length} · đang
                hiệu lực: {preview.currentlyEffectivePolicyVersionIds.length}
              </p>
              <p>{preview.affectedFutureOperations.join(" · ")}</p>
              <small>{preview.limitation}</small>
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>LỊCH SỬ KHÔNG THỂ SỬA</span>
              <h2>Lịch sử chính sách</h2>
            </div>
            <b>{policies.length}</b>
          </div>
          <div className="review-list">
            {policies.map((policy) => (
              <article key={policy.id}>
                <div>
                  <div className="review-meta">
                    <b>{policy.name}</b>
                    <span className="status">v{policy.version}</span>
                    <span>{viLabel(policy.policyType)}</span>
                  </div>
                  <p>{policy.guestDisplayText}</p>
                  <small>
                    {new Date(policy.effectiveFrom).toLocaleString("vi-VN")} →{" "}
                    {policy.effectiveTo
                      ? new Date(policy.effectiveTo).toLocaleString("vi-VN")
                      : "không thời hạn"}
                  </small>
                </div>
                <div className="row-actions">
                  <button type="button" onClick={() => newVersion(policy)}>
                    Tạo phiên bản mới
                  </button>
                  <button type="button" onClick={() => void reindex(policy)}>
                    Đồng bộ lại trợ lý
                  </button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}
