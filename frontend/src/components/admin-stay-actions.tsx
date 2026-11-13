"use client";

import { api } from "@/lib/api";
import type { Booking, Room } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";
import { viLabel } from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";

interface StayView {
  id: string;
  reservationId: string;
  reservationReference: string;
  contactName: string;
  contactEmail: string;
  status: string;
  scheduledCheckIn: string;
  scheduledCheckOut: string;
  currency: string;
  chargesTotal: number;
  paidTotal: number;
  balance: number;
  assignments: Array<{
    id: string;
    roomId: string;
    roomNumber: string;
    roomTypeId: string;
    status: string;
  }>;
  operations: Array<{
    id: string;
    type: string;
    status: string;
    reason: string;
    lastError?: string;
    createdAt: string;
  }>;
}

interface InvoiceView {
  id: string;
  invoiceNumber?: string;
  status: string;
  buyerName: string;
  buyerEmail: string;
  totalAmount: number;
  paidAmount: number;
  balance: number;
  createdAt: string;
}

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

export function AdminStayActions({
  booking,
  rooms,
  onClose,
  onCompleted,
}: {
  booking: Booking;
  rooms: Room[];
  onClose: () => void;
  onCompleted: () => Promise<void>;
}) {
  const [stay, setStay] = useState<StayView>();
  const [invoices, setInvoices] = useState<InvoiceView[]>([]);
  const [roomChange, setRoomChange] = useState({
    fromRoomId: "",
    targetRoomId: "",
    priceDelta: "0",
    reason: "",
  });
  const [extension, setExtension] = useState({
    newCheckoutDate: booking.checkOut,
    priceDelta: "0",
    reason: "",
  });
  const [invoice, setInvoice] = useState({
    buyerName: "",
    buyerEmail: "",
    buyerAddress: "",
    buyerTaxCode: "",
  });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useFlashNotifications(error, notice);
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setError("");
    try {
      const matches = await api.get<
        Array<{ stayId: string; reservationId: string }>
      >(`/admin/service-stays?query=${encodeURIComponent(booking.bookingCode)}`);
      const match = matches.find(
        (item) => String(item.reservationId) === String(booking.id),
      );
      if (!match) throw new Error("Chưa tìm thấy hồ sơ lưu trú của booking này.");
      const detail = await api.get<StayView>(`/admin/stays/${match.stayId}`);
      setStay(detail);
      const active = detail.assignments.find(
        (assignment) => assignment.status === "ACTIVE",
      );
      setRoomChange((current) => ({
        ...current,
        fromRoomId: current.fromRoomId || active?.roomId || "",
      }));
      setExtension((current) => ({
        ...current,
        newCheckoutDate:
          current.newCheckoutDate || detail.scheduledCheckOut,
      }));
      setInvoice((current) => ({
        ...current,
        buyerName: current.buyerName || detail.contactName,
        buyerEmail: current.buyerEmail || detail.contactEmail,
      }));
      setInvoices(
        await api.get<InvoiceView[]>(`/admin/invoices?stayId=${detail.id}`),
      );
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
    // The selected booking is immutable for the lifetime of this panel.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function perform(action: () => Promise<unknown>, message: string) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(message);
      await load();
      await onCompleted();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function changeRoom(event: FormEvent) {
    event.preventDefault();
    if (!stay) return;
    await perform(
      () =>
        api.post(
          `/admin/stays/${stay.id}/room-changes`,
          {
            fromRoomId: roomChange.fromRoomId,
            targetRoomId: roomChange.targetRoomId,
            priceDelta: Number(roomChange.priceDelta),
            reason: roomChange.reason,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã gửi đổi phòng và cập nhật folio.",
    );
  }

  async function extend(event: FormEvent) {
    event.preventDefault();
    if (!stay) return;
    await perform(
      () =>
        api.post(
          `/admin/stays/${stay.id}/extensions`,
          {
            newCheckoutDate: extension.newCheckoutDate,
            priceDelta: Number(extension.priceDelta),
            reason: extension.reason,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã gia hạn kỳ lưu trú và cập nhật folio.",
    );
  }

  async function createInvoice(event: FormEvent) {
    event.preventDefault();
    if (!stay) return;
    await perform(
      () =>
        api.post(
          "/admin/invoices/drafts",
          {
            stayId: stay.id,
            buyerName: invoice.buyerName,
            buyerEmail: invoice.buyerEmail,
            buyerAddress: invoice.buyerAddress,
            buyerTaxCode: invoice.buyerTaxCode,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã lập hóa đơn nháp từ folio hiện tại.",
    );
  }

  async function issueInvoice(item: InvoiceView) {
    await perform(
      () =>
        api.post(
          `/admin/invoices/${item.id}/issue`,
          {},
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã phát hành hóa đơn.",
    );
  }

  async function emailInvoice(item: InvoiceView) {
    await perform(
      () =>
        api.post(
          `/admin/invoices/${item.id}/email`,
          { recipient: item.buyerEmail },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã xếp hàng gửi hóa đơn qua email.",
    );
  }

  async function downloadInvoice(item: InvoiceView) {
    try {
      const blob = await api.download(`/admin/invoices/${item.id}/download`);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${item.invoiceNumber || "hoa-don"}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  const activeAssignments =
    stay?.assignments.filter((assignment) => assignment.status === "ACTIVE") ??
    [];
  const availableTargets = rooms.filter(
    (room) =>
      room.status === "AVAILABLE" &&
      !activeAssignments.some(
        (assignment) => String(assignment.roomId) === String(room.id),
      ),
  );

  return (
    <section className="panel span-2">
      <div className="panel-heading">
        <div>
          <span>LƯU TRÚ VÀ HÓA ĐƠN</span>
          <h2>Điều chỉnh lưu trú · {booking.bookingCode}</h2>
        </div>
        <button type="button" onClick={onClose}>
          Đóng
        </button>
      </div>
      {loading && <p className="muted">Đang đồng bộ dữ liệu lưu trú…</p>}
      {stay && (
        <>
          <div className="availability-strip">
            <span>Trạng thái: {viLabel(stay.status)}</span>
            <span>Trả phòng: {stay.scheduledCheckOut}</span>
            <span>Phải thu: {money(stay.chargesTotal)}</span>
            <span>Còn lại: {money(stay.balance)}</span>
          </div>
          {stay.status === "OPEN" && (
            <div className="management-grid">
              <form className="form-grid compact-form" onSubmit={changeRoom}>
                <h3>Đổi phòng</h3>
                <label>
                  Phòng hiện tại
                  <select
                    required
                    value={roomChange.fromRoomId}
                    onChange={(event) =>
                      setRoomChange({
                        ...roomChange,
                        fromRoomId: event.target.value,
                      })
                    }
                  >
                    {activeAssignments.map((assignment) => (
                      <option key={assignment.roomId} value={assignment.roomId}>
                        {assignment.roomNumber}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Phòng đích sẵn sàng
                  <select
                    required
                    value={roomChange.targetRoomId}
                    onChange={(event) =>
                      setRoomChange({
                        ...roomChange,
                        targetRoomId: event.target.value,
                      })
                    }
                  >
                    <option value="">Chọn phòng</option>
                    {availableTargets.map((room) => (
                      <option key={room.id} value={String(room.id)}>
                        {room.roomNumber} · {room.floor}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Chênh lệch giá đã xác nhận
                  <input
                    type="number"
                    value={roomChange.priceDelta}
                    onChange={(event) =>
                      setRoomChange({
                        ...roomChange,
                        priceDelta: event.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Lý do
                  <textarea
                    required
                    value={roomChange.reason}
                    onChange={(event) =>
                      setRoomChange({
                        ...roomChange,
                        reason: event.target.value,
                      })
                    }
                  />
                </label>
                <button className="primary" disabled={loading}>
                  Xác nhận đổi phòng
                </button>
              </form>

              <form className="form-grid compact-form" onSubmit={extend}>
                <h3>Gia hạn lưu trú</h3>
                <label>
                  Ngày trả phòng mới
                  <input
                    type="date"
                    required
                    min={stay.scheduledCheckOut}
                    value={extension.newCheckoutDate}
                    onChange={(event) =>
                      setExtension({
                        ...extension,
                        newCheckoutDate: event.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Phụ thu đã xác nhận
                  <input
                    type="number"
                    min="0"
                    required
                    value={extension.priceDelta}
                    onChange={(event) =>
                      setExtension({
                        ...extension,
                        priceDelta: event.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Lý do
                  <textarea
                    required
                    value={extension.reason}
                    onChange={(event) =>
                      setExtension({ ...extension, reason: event.target.value })
                    }
                  />
                </label>
                <button className="primary" disabled={loading}>
                  Xác nhận gia hạn
                </button>
              </form>
            </div>
          )}

          <div className="management-grid">
            <form className="form-grid compact-form" onSubmit={createInvoice}>
              <h3>Lập hóa đơn</h3>
              <label>
                Tên người mua
                <input
                  required
                  value={invoice.buyerName}
                  onChange={(event) =>
                    setInvoice({ ...invoice, buyerName: event.target.value })
                  }
                />
              </label>
              <label>
                Email nhận hóa đơn
                <input
                  type="email"
                  required
                  value={invoice.buyerEmail}
                  onChange={(event) =>
                    setInvoice({ ...invoice, buyerEmail: event.target.value })
                  }
                />
              </label>
              <label>
                Địa chỉ
                <input
                  required
                  value={invoice.buyerAddress}
                  onChange={(event) =>
                    setInvoice({ ...invoice, buyerAddress: event.target.value })
                  }
                />
              </label>
              <label>
                Mã số thuế (nếu có)
                <input
                  value={invoice.buyerTaxCode}
                  onChange={(event) =>
                    setInvoice({ ...invoice, buyerTaxCode: event.target.value })
                  }
                />
              </label>
              <button className="primary" disabled={loading}>
                Tạo bản nháp
              </button>
            </form>
            <div>
              <h3>Hóa đơn của kỳ lưu trú</h3>
              <div className="review-list">
                {invoices.map((item) => (
                  <article key={item.id}>
                    <div>
                      <strong>{item.invoiceNumber || "HÓA ĐƠN NHÁP"}</strong>
                      <p>
                        {money(item.totalAmount)} · còn {money(item.balance)}
                      </p>
                      <span className="status">{viLabel(item.status)}</span>
                    </div>
                    <div className="row-actions">
                      {item.status === "DRAFT" && (
                        <button
                          type="button"
                          onClick={() => void issueInvoice(item)}
                        >
                          Phát hành
                        </button>
                      )}
                      {item.status !== "DRAFT" && (
                        <>
                          <button
                            type="button"
                            onClick={() => void downloadInvoice(item)}
                          >
                            Tải PDF
                          </button>
                          <button
                            type="button"
                            onClick={() => void emailInvoice(item)}
                          >
                            Gửi email
                          </button>
                        </>
                      )}
                    </div>
                  </article>
                ))}
                {!invoices.length && <p>Chưa có hóa đơn.</p>}
              </div>
            </div>
          </div>
          {!!stay.operations.length && (
            <div className="audit-timeline">
              {stay.operations.map((operation) => (
                <article key={operation.id}>
                  <i />
                  <div>
                    <strong>
                      {viLabel(operation.type)} · {viLabel(operation.status)}
                    </strong>
                    <p>{operation.lastError || operation.reason}</p>
                  </div>
                  <time>
                    {new Date(operation.createdAt).toLocaleString("vi-VN")}
                  </time>
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
