"use client";

import { api } from "@/lib/api";
import type { PageResponse } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";
import { viLabel } from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";
import { useAuth } from "@/components/auth-provider";

interface CustomerProfile {
  id: string;
  identityId: string;
  fullName: string;
  email: string;
  phone?: string;
  dateOfBirth?: string;
  gender?: string;
  preferredLanguage: string;
  roomPreferences: string;
  marketingEmail: boolean;
  marketingSms: boolean;
  marketingZalo: boolean;
  status: string;
  customerStatus: string;
  emailVerified: boolean;
  roles: string[];
  mergedIntoCustomerId?: string;
  version: number;
  updatedAt: string;
  piiMasked: boolean;
}

interface CustomerNote {
  id: string;
  note: string;
  createdByIdentityId: string;
  createdAt: string;
}

interface MergeProposal {
  id: string;
  sourceCustomerId: string;
  targetCustomerId: string;
  reason: string;
  status: string;
  proposedByIdentityId: string;
  reviewedByIdentityId?: string;
  reviewComment?: string;
  createdAt: string;
  reviewedAt?: string;
  version: number;
}

const emptyEdit = {
  fullName: "",
  phone: "",
  dateOfBirth: "",
  gender: "",
  preferredLanguage: "vi",
  roomPreferences: "",
};

export function AdminCustomers() {
  const { user } = useAuth();
  const authorities = new Set([
    ...(user?.roles ?? []),
    ...(user?.permissions ?? []),
  ]);
  const canEditProfile = [
    "ADMIN",
    "MANAGER",
    "CUSTOMER_WRITE",
    "PERM_CUSTOMER_WRITE",
  ].some((authority) => authorities.has(authority));
  const canApproveMerge = [
    "ADMIN",
    "MANAGER",
    "CUSTOMER_MERGE_APPROVE",
    "PERM_CUSTOMER_MERGE_APPROVE",
  ].some((authority) => authorities.has(authority));
  const [customers, setCustomers] = useState<CustomerProfile[]>([]);
  const [selected, setSelected] = useState<CustomerProfile>();
  const [notes, setNotes] = useState<CustomerNote[]>([]);
  const [proposals, setProposals] = useState<MergeProposal[]>([]);
  const [query, setQuery] = useState("");
  const [note, setNote] = useState("");
  const [edit, setEdit] = useState(emptyEdit);
  const [mergeTarget, setMergeTarget] = useState("");
  const [mergeReason, setMergeReason] = useState("");
  const [decisionComments, setDecisionComments] = useState<
    Record<string, string>
  >({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useFlashNotifications(error, notice);

  async function loadCustomers(search = query) {
    const [customerPage, proposalPage] = await Promise.all([
      api.get<PageResponse<CustomerProfile>>(
        `/admin/customers?query=${encodeURIComponent(search)}&size=100`,
      ),
      api.get<PageResponse<MergeProposal>>(
        "/admin/customers/merge-proposals?status=PENDING&size=100",
      ),
    ]);
    setCustomers(customerPage.content);
    setProposals(proposalPage.content);
  }

  async function chooseCustomer(customerId: string) {
    setLoading(true);
    setError("");
    try {
      const [detail, notePage] = await Promise.all([
        api.get<CustomerProfile>(`/admin/customers/${customerId}`),
        api.get<PageResponse<CustomerNote>>(
          `/admin/customers/${customerId}/notes?size=100`,
        ),
      ]);
      setSelected(detail);
      setNotes(notePage.content);
      setEdit({
        fullName: detail.fullName,
        phone: detail.phone ?? "",
        dateOfBirth: detail.dateOfBirth ?? "",
        gender: detail.gender ?? "",
        preferredLanguage: detail.preferredLanguage,
        roomPreferences: detail.roomPreferences,
      });
      setMergeTarget("");
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadCustomers().catch((reason: Error) => setError(reason.message));
    }, 0);
    return () => window.clearTimeout(timer);
    // Initial load only. Searches are explicitly submitted.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function perform(action: () => Promise<unknown>, message: string) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(message);
      await loadCustomers();
      if (selected) await chooseCustomer(selected.id);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function search(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      await loadCustomers(query);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function saveCustomer(event: FormEvent) {
    event.preventDefault();
    if (!selected) return;
    await perform(
      () =>
        api.put(`/admin/customers/${selected.id}`, {
          fullName: edit.fullName,
          phone: edit.phone,
          dateOfBirth: edit.dateOfBirth || undefined,
          gender: edit.gender || undefined,
          preferredLanguage: edit.preferredLanguage,
          roomPreferences: edit.roomPreferences,
          expectedVersion: selected.version,
        }),
      "Đã cập nhật hồ sơ khách hàng.",
    );
  }

  async function addNote(event: FormEvent) {
    event.preventDefault();
    if (!selected || !note.trim()) return;
    await perform(
      () =>
        api.post(`/admin/customers/${selected.id}/notes`, {
          note: note.trim(),
        }),
      "Đã thêm ghi chú nội bộ.",
    );
    setNote("");
  }

  async function proposeMerge(event: FormEvent) {
    event.preventDefault();
    if (!selected || !mergeTarget || !mergeReason.trim()) return;
    await perform(
      () =>
        api.post("/admin/customers/merge-proposals", {
          sourceCustomerId: selected.id,
          targetCustomerId: mergeTarget,
          reason: mergeReason.trim(),
        }),
      "Đã tạo đề xuất gộp; cần quản lý khác rà soát trước khi duyệt.",
    );
    setMergeReason("");
  }

  async function decide(proposal: MergeProposal, approve: boolean) {
    const comment = decisionComments[proposal.id]?.trim();
    if (!comment) {
      setError("Cần nhập nhận xét trước khi duyệt hoặc từ chối gộp hồ sơ.");
      return;
    }
    await perform(
      () =>
        api.post(
          `/admin/customers/merge-proposals/${proposal.id}/${approve ? "approve" : "reject"}`,
          { comment },
        ),
      approve ? "Đã gộp hồ sơ khách hàng." : "Đã từ chối đề xuất gộp.",
    );
  }

  const customerName = (id: string) =>
    customers.find((customer) => customer.id === id)?.fullName ?? id;

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>CHĂM SÓC KHÁCH HÀNG</span>
          <h1>Quản lý khách hàng</h1>
          <p>
            Tìm hồ sơ, cập nhật sở thích, ghi chú chăm sóc và kiểm soát gộp
            hồ sơ trùng lặp.
          </p>
        </div>
      </div>
      <div className="management-grid governance-grid">
        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>TÌM KIẾM AN TOÀN</span>
              <h2>Danh sách khách hàng</h2>
            </div>
            <b>{customers.length}</b>
          </div>
          <form className="form-row" onSubmit={search}>
            <label>
              Tên, email hoặc điện thoại
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Nhập thông tin cần tìm"
              />
            </label>
            <button className="primary" disabled={loading}>
              Tìm khách
            </button>
          </form>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Khách hàng</th>
                  <th>Liên hệ</th>
                  <th>Trạng thái</th>
                  <th>Cập nhật</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((customer) => (
                  <tr key={customer.id}>
                    <td>
                      <strong>{customer.fullName}</strong>
                      <small>{customer.id}</small>
                    </td>
                    <td>
                      {customer.email}
                      <small>{customer.phone || "Chưa có điện thoại"}</small>
                    </td>
                    <td>
                      <span className="status">{customer.customerStatus}</span>
                    </td>
                    <td>
                      <button
                        type="button"
                        onClick={() => void chooseCustomer(customer.id)}
                      >
                        Mở hồ sơ
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>HỒ SƠ CHI TIẾT</span>
              <h2>{selected?.fullName ?? "Chưa chọn khách"}</h2>
            </div>
            {selected?.piiMasked && <span className="status">Thông tin nhạy cảm đã được che</span>}
          </div>
          {selected ? (
            <form className="form-grid compact-form" onSubmit={saveCustomer}>
              <label>
                Họ tên
                <input
                  required
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.fullName}
                  onChange={(event) =>
                    setEdit({ ...edit, fullName: event.target.value })
                  }
                />
              </label>
              <label>
                Điện thoại
                <input
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.phone}
                  onChange={(event) =>
                    setEdit({ ...edit, phone: event.target.value })
                  }
                />
              </label>
              <label>
                Ngày sinh
                <input
                  type="date"
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.dateOfBirth}
                  onChange={(event) =>
                    setEdit({ ...edit, dateOfBirth: event.target.value })
                  }
                />
              </label>
              <label>
                Giới tính
                <select
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.gender}
                  onChange={(event) =>
                    setEdit({ ...edit, gender: event.target.value })
                  }
                >
                  <option value="">Chưa khai báo</option>
                  <option value="FEMALE">Nữ</option>
                  <option value="MALE">Nam</option>
                  <option value="OTHER">Khác</option>
                </select>
              </label>
              <label>
                Ngôn ngữ
                <select
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.preferredLanguage}
                  onChange={(event) =>
                    setEdit({ ...edit, preferredLanguage: event.target.value })
                  }
                >
                  <option value="vi">Tiếng Việt</option>
                  <option value="en">English</option>
                </select>
              </label>
              <label>
                Sở thích phòng
                <textarea
                  disabled={selected.piiMasked || !canEditProfile}
                  value={edit.roomPreferences}
                  onChange={(event) =>
                    setEdit({ ...edit, roomPreferences: event.target.value })
                  }
                />
              </label>
              <small>
                Đồng ý nhận tin: email {selected.marketingEmail ? "có" : "không"}
                {" · "}SMS {selected.marketingSms ? "có" : "không"}
                {" · "}Zalo {selected.marketingZalo ? "có" : "không"}. Nhân
                viên không được tự thay đổi consent.
              </small>
              {canEditProfile && (
                <button
                  className="primary"
                  disabled={loading || selected.piiMasked}
                >
                  Lưu hồ sơ
                </button>
              )}
            </form>
          ) : (
            <p>Chọn một khách hàng trong danh sách để xem hồ sơ.</p>
          )}
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>GHI CHÚ NỘI BỘ</span>
              <h2>Lịch sử chăm sóc</h2>
            </div>
            <b>{notes.length}</b>
          </div>
          {selected && (
            <form className="form-grid compact-form" onSubmit={addNote}>
              <label>
                Ghi chú mới
                <textarea
                  required
                  maxLength={4000}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="Ví dụ: khách ưu tiên phòng xa thang máy"
                />
              </label>
              <button className="primary" disabled={loading}>
                Thêm ghi chú
              </button>
            </form>
          )}
          <div className="audit-timeline">
            {notes.map((item) => (
              <article key={item.id}>
                <i />
                <div>
                  <strong>{item.note}</strong>
                  <small>Ghi chú của nhân viên được phân quyền</small>
                </div>
                <time>{new Date(item.createdAt).toLocaleString("vi-VN")}</time>
              </article>
            ))}
          </div>
        </section>

        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>KIỂM SOÁT TRÙNG HỒ SƠ</span>
              <h2>Đề xuất và duyệt gộp khách hàng</h2>
            </div>
            <b>{proposals.length} chờ duyệt</b>
          </div>
          {selected && (
            <form className="form-grid compact-form" onSubmit={proposeMerge}>
              <h3>Gộp hồ sơ đang chọn vào hồ sơ đích</h3>
              <label>
                Hồ sơ đích
                <select
                  required
                  value={mergeTarget}
                  onChange={(event) => setMergeTarget(event.target.value)}
                >
                  <option value="">Chọn khách hàng giữ lại</option>
                  {customers
                    .filter((customer) => customer.id !== selected.id)
                    .map((customer) => (
                      <option key={customer.id} value={customer.id}>
                        {customer.fullName} · {customer.email}
                      </option>
                    ))}
                </select>
              </label>
              <label>
                Lý do và bằng chứng trùng
                <textarea
                  required
                  maxLength={2000}
                  value={mergeReason}
                  onChange={(event) => setMergeReason(event.target.value)}
                />
              </label>
              <button className="primary" disabled={loading}>
                Tạo đề xuất gộp
              </button>
            </form>
          )}
          <div className="review-list">
            {proposals.map((proposal) => (
              <article key={proposal.id}>
                <div>
                  <div className="review-meta">
                    <b>{customerName(proposal.sourceCustomerId)}</b>
                    <span>→</span>
                    <b>{customerName(proposal.targetCustomerId)}</b>
                    <span className="status">{viLabel(proposal.status)}</span>
                  </div>
                  <p>{proposal.reason}</p>
                  <small>
                    Đề xuất lúc {new Date(proposal.createdAt).toLocaleString("vi-VN")}
                  </small>
                  {canApproveMerge && (
                    <label>
                      Nhận xét người duyệt
                      <textarea
                        value={decisionComments[proposal.id] ?? ""}
                        onChange={(event) =>
                          setDecisionComments({
                            ...decisionComments,
                            [proposal.id]: event.target.value,
                          })
                        }
                      />
                    </label>
                  )}
                </div>
                {canApproveMerge && (
                  <div className="row-actions">
                    <button
                      type="button"
                      onClick={() => void decide(proposal, true)}
                    >
                      Duyệt gộp
                    </button>
                    <button
                      type="button"
                      onClick={() => void decide(proposal, false)}
                    >
                      Từ chối
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}
