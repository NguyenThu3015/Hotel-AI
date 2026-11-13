"use client";

import { api } from "@/lib/api";
import type {
  BankTransferInstructions,
  PageResponse,
  Payment,
  Reservation,
} from "@/lib/types";
import { useAuth } from "@/components/auth-provider";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { viLabel, viPolicy, viTopic } from "@/lib/vi";
import { useFlashNotifications, useUi } from "@/components/ui-provider";
import { isHotelStaff } from "@/lib/admin-permissions";

interface EligibleReviewStay {
  stayId: string;
  reservationId: string;
  reservationReference: string;
  checkIn: string;
  checkOut: string;
  actualCheckOut: string;
  roomNumbers: string[];
}

interface MyReview {
  id: string;
  stayId: string;
  reservationReference: string;
  rating: number;
  comment: string;
  topics: string[];
  status: string;
  createdAt: string;
  hotelResponse?: { content: string };
}

interface ReservationChangeRequest {
  id: string;
  reservationId: string;
  status: string;
  requestedChange: {
    checkIn: string;
    checkOut: string;
    roomTypeId: string;
    roomCount: number;
    adults: number;
    children: number;
  };
  decisionReason?: string;
  createdAt: string;
  version: number;
}

interface ReservationChangeOutcome {
  applied: boolean;
  reservation: Reservation;
  changeRequest?: ReservationChangeRequest | null;
}

const reviewTopics = [
  ["ROOM", "Phòng"],
  ["CLEANLINESS", "Sạch sẽ"],
  ["SERVICE", "Dịch vụ"],
  ["STAFF", "Nhân viên"],
  ["LOCATION", "Vị trí"],
  ["VALUE", "Giá trị"],
  ["AMENITIES", "Tiện nghi"],
] as const;

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(value);

const editableStatuses = ["PENDING_PAYMENT", "PENDING_CONFIRMATION", "CONFIRMED"];

export default function BookingsPage() {
  const { confirmDialog } = useUi();
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [bookings, setBookings] = useState<Reservation[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [bankTransfer, setBankTransfer] = useState<
    (BankTransferInstructions & { paymentId: string; bookingReference: string }) | undefined
  >();
  const [editing, setEditing] = useState<string>();
  const [dates, setDates] = useState({ checkIn: "", checkOut: "" });
  const [changeRequests, setChangeRequests] = useState<
    Record<string, ReservationChangeRequest[]>
  >({});
  const [reschedulingId, setReschedulingId] = useState<string>();
  const [eligibleStays, setEligibleStays] = useState<EligibleReviewStay[]>([]);
  const [myReviews, setMyReviews] = useState<MyReview[]>([]);
  const [review, setReview] = useState({
    stayId: "",
    rating: "5",
    topics: [] as string[],
    comment: "",
  });
  useFlashNotifications(error, notice);

  async function load() {
    try {
      const [page, paymentPage, reviewStays, reviewPage] = await Promise.all([
        api.get<PageResponse<Reservation>>("/bookings"),
        api
          .get<PageResponse<Payment>>("/payments?size=100")
          .catch(() => ({
            content: [],
            totalElements: 0,
            totalPages: 0,
            size: 100,
            number: 0,
          })),
        api.get<EligibleReviewStay[]>("/review-stays").catch(() => []),
        api
          .get<PageResponse<MyReview>>("/reviews/mine?size=100")
          .catch(() => ({
            content: [],
            totalElements: 0,
            totalPages: 0,
            size: 100,
            number: 0,
          })),
      ]);
      const requestEntries = await Promise.all(
        page.content.map(async (booking) => {
          const requests = await api
            .get<ReservationChangeRequest[]>(
              `/bookings/${booking.id}/change-requests`,
            )
            .catch(() => []);
          return [booking.id, requests] as const;
        }),
      );
      const nextChangeRequests: Record<string, ReservationChangeRequest[]> =
        Object.fromEntries(requestEntries);
      const completedWhileOpen = Object.entries(changeRequests).some(
        ([bookingId, requests]) =>
          requests.some((request) => request.status === "PENDING") &&
          !nextChangeRequests[bookingId]?.some(
            (request: ReservationChangeRequest) => request.status === "PENDING",
          ),
      );
      setBookings(page.content);
      setChangeRequests(nextChangeRequests);
      if (completedWhileOpen) {
        setNotice(
          "Yêu cầu đổi lịch vừa được lễ tân xử lý. Ngày lưu trú và tổng tiền đã được cập nhật.",
        );
      }
      setPayments(paymentPage.content);
      setEligibleStays(reviewStays);
      setMyReviews(reviewPage.content);
      setReview((current) => ({
        ...current,
        stayId: reviewStays.some((stay) => stay.stayId === current.stayId)
          ? current.stayId
          : reviewStays[0]?.stayId || "",
      }));
      setError("");
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.replace("/login?next=/bookings");
      return;
    }
    if (isHotelStaff(user.roles)) {
      router.replace("/admin");
      return;
    }
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
    // Initial load is keyed by the authenticated user, not transient page state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, user, router]);

  useEffect(() => {
    if (!payments.some((payment) => ["CREATED", "PENDING"].includes(payment.status))) {
      return;
    }
    const timer = window.setInterval(() => {
      void api
        .get<PageResponse<Payment>>("/payments?size=100")
        .then(async (page) => {
          const completed = page.content.find(
            (payment) =>
              payment.status === "SUCCEEDED" &&
              payments.some(
                (previous) =>
                  previous.id === payment.id &&
                  ["CREATED", "PENDING"].includes(previous.status),
              ),
          );
          setPayments(page.content);
          if (completed) {
            setBankTransfer((current) =>
              current?.paymentId === completed.id ? undefined : current,
            );
            setNotice("Casso đã xác nhận chuyển khoản thành công.");
            await load();
          }
        })
        .catch(() => undefined);
    }, 8000);
    return () => window.clearInterval(timer);
    // Payment polling is restarted only when the payment collection changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [payments]);

  useEffect(() => {
    const hasPendingChange = Object.values(changeRequests).some((requests) =>
      requests.some((request) => request.status === "PENDING"),
    );
    if (!hasPendingChange) return;
    const timer = window.setInterval(() => void load(), 6000);
    return () => window.clearInterval(timer);
    // Poll only while a change request is awaiting a staff decision.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [changeRequests]);

  function activePaymentFor(reservationId: string) {
    return payments.find(
      (payment) =>
        payment.reservationId === reservationId &&
        payment.provider === "CASSO" &&
        ["CREATED", "PENDING"].includes(payment.status),
    );
  }

  async function showBankTransfer(payment: Payment, booking: Reservation) {
    const instructions = await api.get<BankTransferInstructions>(
      `/payments/${payment.id}/bank-transfer`,
    );
    setBankTransfer({
      ...instructions,
      paymentId: payment.id,
      bookingReference: booking.reference,
    });
  }

  async function cancel(booking: Reservation) {
    const policy = viPolicy(booking.cancellationPolicy);
    const confirmed = await confirmDialog({
      title: `Hủy đặt phòng ${booking.reference}`,
      description: `${policy} Bạn có chắc muốn tiếp tục hủy đặt phòng này?`,
      confirmLabel: "Xác nhận hủy",
      cancelLabel: "Giữ đặt phòng",
      tone: "danger",
    });
    if (!confirmed) return;

    try {
      await api.post(
        `/bookings/${booking.id}/cancel`,
        {
          expectedVersion: booking.version,
          reason: "Khách hàng yêu cầu hủy trên website",
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      setNotice("Đã hủy đặt phòng.");
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function pay(booking: Reservation) {
    const due = booking.totalAmount - booking.paidAmount;
    const depositDue = Math.max(
      0,
      booking.depositRequired - booking.paidAmount,
    );
    const amount = depositDue > 0 ? depositDue : due;
    const purpose =
      depositDue > 0 ? "DEPOSIT" : booking.paidAmount > 0 ? "BALANCE" : "FULL";

    try {
      setError("");
      setNotice("");
      const existing = activePaymentFor(booking.id);
      if (existing) {
        await showBankTransfer(existing, booking);
        setNotice(
          "Đã mở lại yêu cầu chuyển khoản đang chờ. Hệ thống sẽ tự cập nhật khi Casso xác nhận giao dịch.",
        );
        return;
      }
      const payment = await api.post<Payment>(
        "/payments/intents",
        {
          reservationId: booking.id,
          provider: "CASSO",
          purpose,
          amount,
          currency: booking.currency,
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      setPayments((current) => [payment, ...current]);
      await showBankTransfer(payment, booking);
      setNotice(
        "Đã tạo yêu cầu chuyển khoản. Booking chỉ được xác nhận thanh toán sau khi Casso gửi webhook hợp lệ.",
      );
    } catch (reason) {
      try {
        // Nếu một lần bấm trước đã tạo giao dịch nhưng phản hồi về trình duyệt
        // bị gián đoạn, mở lại giao dịch đó thay vì hiển thị lỗi xung đột.
        const page = await api.get<PageResponse<Payment>>(
          `/payments?reservationId=${booking.id}&size=20`,
        );
        setPayments((current) => [
          ...page.content,
          ...current.filter(
            (item) => !page.content.some((fresh) => fresh.id === item.id),
          ),
        ]);
        const existing = page.content.find(
          (payment) =>
            payment.provider === "CASSO" &&
            ["CREATED", "PENDING"].includes(payment.status),
        );
        if (existing) {
          await showBankTransfer(existing, booking);
          setNotice(
            "Yêu cầu chuyển khoản đã tồn tại và vừa được mở lại. Bạn không cần tạo hoặc chuyển khoản lần nữa.",
          );
          return;
        }
      } catch {
        // Giữ lỗi gốc nếu không thể khôi phục một giao dịch đang chờ.
      }
      setError((reason as Error).message);
    }
  }

  async function checkBankTransfer() {
    if (!bankTransfer) return;
    try {
      const payment = await api.get<Payment>(`/payments/${bankTransfer.paymentId}`);
      setPayments((current) =>
        current.map((item) => (item.id === payment.id ? payment : item)),
      );
      if (payment.status === "SUCCEEDED") {
        setBankTransfer(undefined);
        setNotice("Casso đã xác nhận chuyển khoản thành công.");
        await load();
        return;
      }
      if (["FAILED", "CANCELLED", "EXPIRED"].includes(payment.status)) {
        setError(`Yêu cầu thanh toán ${viLabel(payment.status).toLocaleLowerCase("vi-VN")}.`);
        return;
      }
      setNotice("Chưa nhận được xác nhận từ Casso. Vui lòng kiểm tra lại sau ít phút.");
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function copyTransferContent() {
    if (!bankTransfer) return;
    try {
      await navigator.clipboard.writeText(bankTransfer.transferContent);
      setNotice("Đã sao chép nội dung chuyển khoản.");
    } catch {
      setError("Không thể sao chép tự động. Vui lòng chọn và sao chép nội dung thủ công.");
    }
  }

  async function reschedule(booking: Reservation) {
    setError("");
    setNotice("");
    const existingPending = changeRequests[booking.id]?.find(
      (request) => request.status === "PENDING",
    );
    if (existingPending) {
      setEditing(undefined);
      setNotice(
        `Yêu cầu đổi sang ${existingPending.requestedChange.checkIn} → ${existingPending.requestedChange.checkOut} đang chờ lễ tân duyệt. Lịch và giá hiện tại được giữ nguyên cho đến khi có quyết định.`,
      );
      return;
    }
    if (!dates.checkIn || !dates.checkOut || dates.checkOut <= dates.checkIn) {
      setError("Ngày trả phòng phải sau ngày nhận phòng.");
      return;
    }
    if (dates.checkIn === booking.checkIn && dates.checkOut === booking.checkOut) {
      setError("Bạn chưa thay đổi ngày nhận hoặc ngày trả phòng.");
      return;
    }
    try {
      setReschedulingId(booking.id);
      const outcome = await api.put<ReservationChangeOutcome>(
        `/bookings/${booking.id}`,
        {
          roomTypeId: booking.roomTypeId,
          checkIn: dates.checkIn,
          checkOut: dates.checkOut,
          roomCount: booking.roomCount,
          adults: booking.adults,
          children: booking.children,
          specialRequests: booking.specialRequests,
          expectedVersion: booking.version,
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      setEditing(undefined);
      if (outcome.applied) {
        const updated = outcome.reservation;
        await load();
        setNotice(
          `Đã đổi lịch ${booking.checkIn} → ${booking.checkOut} thành ${updated.checkIn} → ${updated.checkOut}. Tổng tiền mới: ${money(updated.totalAmount)}.`,
        );
      } else {
        if (outcome.changeRequest) {
          setChangeRequests((current) => ({
            ...current,
            [booking.id]: [
              outcome.changeRequest!,
              ...(current[booking.id] ?? []).filter(
                (request) => request.id !== outcome.changeRequest!.id,
              ),
            ],
          }));
        }
        setNotice(
          `Đã gửi yêu cầu đổi sang ${dates.checkIn} → ${dates.checkOut}. Vì booking đã xác nhận hoặc gần ngày nhận phòng, lễ tân cần duyệt trước. Lịch và giá hiện tại vẫn được giữ nguyên để bảo vệ chỗ đã đặt.`,
        );
      }
    } catch (reason) {
      const latestRequests = await api
        .get<ReservationChangeRequest[]>(
          `/bookings/${booking.id}/change-requests`,
        )
        .catch(() => []);
      const pending = latestRequests.find(
        (request) => request.status === "PENDING",
      );
      if (pending) {
        setEditing(undefined);
        setChangeRequests((current) => ({
          ...current,
          [booking.id]: latestRequests,
        }));
        setNotice(
          `Yêu cầu đổi sang ${pending.requestedChange.checkIn} → ${pending.requestedChange.checkOut} đã được ghi nhận và đang chờ lễ tân duyệt. Bạn không cần gửi lại.`,
        );
      } else {
        setError((reason as Error).message);
      }
    } finally {
      setReschedulingId(undefined);
    }
  }

  async function showReceipt(reservationId: string) {
    try {
      const page = await api.get<PageResponse<Payment>>(
        `/payments?reservationId=${reservationId}`,
      );
      const payment = page.content.find((item) => item.status === "SUCCEEDED");
      if (!payment) {
        setNotice("Đặt phòng này chưa có giao dịch thanh toán thành công.");
        return;
      }
      const receipt = await api.get<{
        providerReference: string;
        amount: number;
      }>(`/payments/${payment.id}/receipt`);
      setNotice(
        `Biên nhận ${receipt.providerReference} · ${money(receipt.amount)}`,
      );
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function submitReview() {
    if (!review.stayId || !review.topics.length || review.comment.trim().length < 10) {
      setError(
        "Hãy chọn kỳ lưu trú, ít nhất một chủ đề và nhập nhận xét từ 10 ký tự.",
      );
      return;
    }
    try {
      await api.post(
        "/reviews",
        {
          stayId: review.stayId,
          rating: Number(review.rating),
          topics: review.topics,
          comment: review.comment.trim(),
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      setNotice(
        "Đã gửi đánh giá. Nội dung có dữ liệu nhạy cảm sẽ chờ kiểm duyệt.",
      );
      setReview({ stayId: "", rating: "5", topics: [], comment: "" });
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <span>HÀNH TRÌNH</span>
          <h1>Đặt phòng của tôi</h1>
        </div>
      </div>
      {authLoading && <p className="muted">Đang kiểm tra phiên đăng nhập…</p>}
      {bankTransfer && (
        <section
          className="bank-transfer-panel"
          aria-label="Thông tin chuyển khoản Casso"
        >
          <div className="bank-transfer-qr">
            <Image
              src={bankTransfer.qrUrl}
              alt="Mã VietQR thanh toán đặt phòng"
              width={280}
              height={280}
              unoptimized
            />
          </div>
          <div className="bank-transfer-details">
            <span>CHUYỂN KHOẢN NGÂN HÀNG · VIETQR</span>
            <h2>Chuyển khoản cho {bankTransfer.bookingReference}</h2>
            <div className="payment-code-callout">
              <span>MÃ ĐỐI SOÁT / NỘI DUNG CHUYỂN KHOẢN</span>
              <strong>{bankTransfer.transferContent}</strong>
              <button onClick={() => void copyTransferContent()}>
                Sao chép mã
              </button>
            </div>
            <dl>
              <div>
                <dt>Ngân hàng</dt>
                <dd>{bankTransfer.bankId}</dd>
              </div>
              <div>
                <dt>Số tài khoản</dt>
                <dd>{bankTransfer.accountNumber}</dd>
              </div>
              <div>
                <dt>Chủ tài khoản</dt>
                <dd>{bankTransfer.accountName}</dd>
              </div>
              <div>
                <dt>Số tiền</dt>
                <dd>{money(bankTransfer.amount)}</dd>
              </div>
              <div className="transfer-content-row">
                <dt>Nội dung bắt buộc</dt>
                <dd>{bankTransfer.transferContent}</dd>
              </div>
              {bankTransfer.expiresAt && (
                <div>
                  <dt>Hạn thanh toán</dt>
                  <dd>
                    {new Date(bankTransfer.expiresAt).toLocaleString("vi-VN")}
                  </dd>
                </div>
              )}
            </dl>
            <p className="muted">
              Không sửa số tiền hoặc nội dung chuyển khoản. Casso sẽ tự động
              đối chiếu giao dịch.
            </p>
            <div className="row-actions">
              <button onClick={() => void copyTransferContent()}>
                Sao chép nội dung
              </button>
              <button
                className="primary"
                onClick={() => void checkBankTransfer()}
              >
                Kiểm tra thanh toán
              </button>
              <button onClick={() => setBankTransfer(undefined)}>Đóng</button>
            </div>
          </div>
        </section>
      )}
      <div className="booking-list">
        {bookings.map((booking) => {
          const activePayment = activePaymentFor(booking.id);
          const pendingChange = changeRequests[booking.id]?.find(
            (request) => request.status === "PENDING",
          );
          return (
          <article key={booking.id}>
            <div>
              <span className={`status ${booking.status.toLowerCase()}`}>
                {viLabel(booking.status)}
              </span>
              <h3>{booking.reference}</h3>
              <p>
                {booking.checkIn} → {booking.checkOut} · {booking.roomCount}{" "}
                phòng
              </p>
            </div>
            <div>
              <strong>{money(booking.totalAmount)}</strong>
              <small>Đã trả {money(booking.paidAmount)}</small>
              {activePayment && (
                <small className="payment-pending-label">
                  Đang chờ Casso đối soát
                </small>
              )}
            </div>
            <div className="actions">
              {editableStatuses.includes(booking.status) && (
                <button onClick={() => void cancel(booking)}>Hủy</button>
              )}
              {booking.totalAmount > booking.paidAmount &&
                ["PENDING_PAYMENT", "PENDING_CONFIRMATION", "CONFIRMED", "CHECKED_IN"].includes(
                  booking.status,
                ) && (
                  <button
                    className="primary"
                    onClick={() => void pay(booking)}
                  >
                    {activePayment ? "Xem chuyển khoản" : "Thanh toán"}
                  </button>
                )}
              {editableStatuses.includes(booking.status) && !pendingChange && (
                <button
                  onClick={() => {
                    setEditing(booking.id);
                    setDates({
                      checkIn: booking.checkIn,
                      checkOut: booking.checkOut,
                    });
                  }}
                >
                  Đổi ngày
                </button>
              )}
              {pendingChange && (
                <button type="button" disabled>
                  Đang chờ duyệt
                </button>
              )}
              {booking.paidAmount > 0 && (
                <button onClick={() => void showReceipt(booking.id)}>
                  Biên nhận
                </button>
              )}
            </div>
            {pendingChange && (
              <div className="pending-change-card" role="status">
                <div>
                  <span>YÊU CẦU ĐỔI LỊCH ĐANG CHỜ DUYỆT</span>
                  <strong>
                    {pendingChange.requestedChange.checkIn} →{" "}
                    {pendingChange.requestedChange.checkOut}
                  </strong>
                </div>
                <p>
                  Lịch hiện tại {booking.checkIn} → {booking.checkOut} và giá hiện
                  tại vẫn được giữ nguyên. Sau khi lễ tân duyệt, hệ thống sẽ tự
                  cập nhật ngày, tính lại giá và gửi thông báo cho bạn.
                </p>
              </div>
            )}
            {editing === booking.id && (
              <div className="inline-editor">
                <label>
                  Ngày nhận phòng mới
                  <input
                    type="date"
                    value={dates.checkIn}
                    onChange={(event) =>
                      setDates({ ...dates, checkIn: event.target.value })
                    }
                  />
                </label>
                <label>
                  Ngày trả phòng mới
                  <input
                    type="date"
                    min={dates.checkIn}
                    value={dates.checkOut}
                    onChange={(event) =>
                      setDates({ ...dates, checkOut: event.target.value })
                    }
                  />
                </label>
                <button
                  className="primary"
                  disabled={reschedulingId === booking.id}
                  onClick={() => void reschedule(booking)}
                >
                  {reschedulingId === booking.id ? "Đang gửi…" : "Xác nhận"}
                </button>
                <button onClick={() => setEditing(undefined)}>Đóng</button>
              </div>
            )}
          </article>
          );
        })}
        {!bookings.length && (
          <p className="muted">Bạn chưa có đặt phòng nào.</p>
        )}
      </div>
      <div className="section-heading">
        <div>
          <span>CHIA SẺ TRẢI NGHIỆM</span>
          <h2>Đánh giá kỳ lưu trú</h2>
        </div>
      </div>
      <div className="management-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>ĐÁNH GIÁ SAU KỲ NGHỈ</span>
              <h3>Chia sẻ trải nghiệm</h3>
            </div>
          </div>
          {eligibleStays.length ? (
            <div className="form-grid compact-form">
              <label>
                Kỳ lưu trú đã hoàn tất
                <select
                  value={review.stayId}
                  onChange={(event) =>
                    setReview({ ...review, stayId: event.target.value })
                  }
                >
                  {eligibleStays.map((stay) => (
                    <option key={stay.stayId} value={stay.stayId}>
                      {stay.reservationReference} · {stay.checkIn} → {stay.checkOut}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Điểm đánh giá
                <select
                  value={review.rating}
                  onChange={(event) =>
                    setReview({ ...review, rating: event.target.value })
                  }
                >
                  {[5, 4, 3, 2, 1].map((rating) => (
                    <option key={rating} value={rating}>
                      {rating}/5
                    </option>
                  ))}
                </select>
              </label>
              <fieldset>
                <legend>Chủ đề</legend>
                <div className="row-actions">
                  {reviewTopics.map(([value, label]) => (
                    <label key={value}>
                      <input
                        type="checkbox"
                        checked={review.topics.includes(value)}
                        onChange={(event) =>
                          setReview({
                            ...review,
                            topics: event.target.checked
                              ? [...review.topics, value]
                              : review.topics.filter((topic) => topic !== value),
                          })
                        }
                      />
                      {label}
                    </label>
                  ))}
                </div>
              </fieldset>
              <label>
                Nhận xét
                <textarea
                  minLength={10}
                  maxLength={5000}
                  value={review.comment}
                  onChange={(event) =>
                    setReview({ ...review, comment: event.target.value })
                  }
                  placeholder="Điều gì khiến kỳ nghỉ của bạn đáng nhớ?"
                />
              </label>
              <button className="primary" onClick={() => void submitReview()}>
                Gửi đánh giá
              </button>
            </div>
          ) : (
            <p className="muted">
              Chưa có kỳ lưu trú đã trả phòng đủ điều kiện đánh giá.
            </p>
          )}
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>LỊCH SỬ CỦA TÔI</span>
              <h3>Đánh giá đã gửi</h3>
            </div>
            <b>{myReviews.length}</b>
          </div>
          <div className="review-list">
            {myReviews.map((item) => (
              <article key={item.id}>
                <div className="review-score">
                  <strong>{item.rating}</strong>
                  <small>/5</small>
                </div>
                <div>
                  <div className="review-meta">
                    <b>{item.reservationReference}</b>
                    <span className="status">{viLabel(item.status)}</span>
                    {item.topics.map((topic) => (
                      <span className="topic" key={topic}>
                        {viTopic(topic)}
                      </span>
                    ))}
                  </div>
                  <p>{item.comment}</p>
                  {item.hotelResponse && (
                    <blockquote>
                      Khách sạn: {item.hotelResponse.content}
                    </blockquote>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </section>
  );
}
