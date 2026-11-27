"use client";

import { api, ApiRequestError } from "@/lib/api";
import { AppIcon } from "@/components/app-icon";
import { AdminStayActions } from "@/components/admin-stay-actions";
import type {
  Booking,
  DestinationCatalogItem,
  PageResponse,
  Reservation,
  Room,
  RoomType,
  Service,
} from "@/lib/types";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { isPresentableRoomType, viLabel } from "@/lib/vi";
import { useFlashNotifications, useUi } from "@/components/ui-provider";

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

interface CheckInGuestDraft {
  category: "ADULT" | "CHILD";
  fullName: string;
  dateOfBirth: string;
  nationality: string;
  documentType: string;
  documentNumber: string;
  primaryGuest: boolean;
}

interface CheckInDraft {
  booking: Booking;
  roomIds: string[];
  guests: CheckInGuestDraft[];
  outstandingDepositAcknowledged: boolean;
  earlyCheckInApproved: boolean;
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
  createdAt: string;
  version: number;
}

export function AdminFrontDesk() {
  const { formDialog } = useUi();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [refunds, setRefunds] = useState<
    Array<{
      id: string;
      bookingCode: string;
      amount: number;
      reason: string;
      status: string;
    }>
  >([]);
  const [types, setTypes] = useState<RoomType[]>([]);
  const [selectedStayBooking, setSelectedStayBooking] = useState<Booking>();
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerOptions, setCustomerOptions] = useState<
    Array<{
      id: string;
      fullName: string;
      email: string;
      phone?: string;
      piiMasked: boolean;
    }>
  >([]);
  const [walkIn, setWalkIn] = useState({
    customerId: "",
    destinationCode: "ALL",
    roomTypeId: "",
    checkIn: "",
    checkOut: "",
    adults: "1",
    contactName: "",
    contactEmail: "",
    contactPhone: "",
  });
  const [destinations, setDestinations] = useState<DestinationCatalogItem[]>(
    [],
  );
  const [destinationFilter, setDestinationFilter] = useState("ALL");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [checkInDraft, setCheckInDraft] = useState<CheckInDraft>();
  const [pendingChanges, setPendingChanges] = useState<
    Record<string, ReservationChangeRequest>
  >({});
  useFlashNotifications(error, notice);

  async function load() {
    try {
      const [
        bookingData,
        roomData,
        serviceData,
        typeData,
        refundData,
        destData,
      ] = await Promise.all([
        api.get<PageResponse<Reservation>>(
          `/admin/bookings?hotelId=1${status ? `&status=${status}` : ""}`,
        ),
        api.get<
          Array<{
            id: string;
            roomNumber: string;
            roomTypeId: string;
            roomTypeName: string;
            floor: { name: string };
            status: string;
            destinationCode?: string;
            destinationName?: string;
            propertyName?: string;
          }>
        >("/admin/rooms"),
        api.get<
          Array<{
            id: string;
            code: string;
            name: string;
            unit: string;
            currentPrice: { unitPrice: number };
          }>
        >("/hotel-services"),
        api.get<
          Array<{
            id: string;
            code: string;
            name: string;
            description?: string;
            maxAdults: number;
            maxChildren: number;
            amenities: Array<{ name: string }>;
            destinationCode?: string;
            destinationName?: string;
            propertyName?: string;
          }>
        >("/catalog/room-types"),
        api.get<
          PageResponse<{
            id: string;
            paymentId: string;
            amount: number;
            reason: string;
            status: string;
          }>
        >("/admin/refunds?size=100"),
        api
          .get<DestinationCatalogItem[]>("/availability/destinations")
          .catch(() => []),
      ]);
      if (destData.length > 0) setDestinations(destData);
      const typeName = new Map(typeData.map((type) => [type.id, type.name]));
      const typeMeta = new Map(
        typeData.map((type) => [
          type.id,
          {
            destinationCode: type.destinationCode ?? "PHU_QUOC",
            destinationName: type.destinationName ?? "Phú Quốc",
            propertyName: type.propertyName ?? "HotelAI Phú Quốc",
          },
        ]),
      );
      const changeEntries = await Promise.all(
        bookingData.content.map(async (booking) => {
          const requests = await api
            .get<ReservationChangeRequest[]>(
              `/admin/bookings/${booking.id}/change-requests`,
            )
            .catch(() => []);
          return [
            booking.id,
            requests.find((request) => request.status === "PENDING"),
          ] as const;
        }),
      );
      const sellableTypes = typeData.filter((type) =>
        isPresentableRoomType(type.name, type.code),
      );
      setBookings(
        bookingData.content.map((booking) => {
          const meta = typeMeta.get(booking.roomTypeId);
          return {
            id: booking.id,
            bookingCode: booking.reference,
            status: booking.status,
            hotelId: 1,
            checkIn: booking.checkIn,
            checkOut: booking.checkOut,
            totalAmount: booking.totalAmount,
            paidAmount: booking.paidAmount,
            adults: booking.adults,
            children: booking.children,
            contactName: booking.contactName,
            contactEmail: booking.contactEmail,
            contactPhone: booking.contactPhone,
            version: booking.version,
            destinationCode: meta?.destinationCode ?? "PHU_QUOC",
            destinationName: meta?.destinationName ?? "Phú Quốc",
            propertyName: meta?.propertyName ?? "HotelAI Phú Quốc",
            rooms: Array.from({ length: booking.roomCount }, () => ({
              roomTypeId: booking.roomTypeId,
              roomType: typeName.get(booking.roomTypeId) ?? booking.roomTypeId,
              destinationCode: meta?.destinationCode ?? "PHU_QUOC",
              destinationName: meta?.destinationName ?? "Phú Quốc",
              propertyName: meta?.propertyName ?? "HotelAI Phú Quốc",
            })),
          };
        }),
      );
      setPendingChanges(
        Object.fromEntries(
          changeEntries.filter(
            (entry): entry is readonly [string, ReservationChangeRequest] =>
              Boolean(entry[1]),
          ),
        ),
      );
      setRooms(
        roomData.map((room) => {
          const meta = typeMeta.get(room.roomTypeId);
          return {
            id: room.id,
            roomTypeId: room.roomTypeId,
            roomNumber: room.roomNumber,
            floor: room.floor.name,
            status: room.status,
            destinationCode:
              room.destinationCode ?? meta?.destinationCode ?? "PHU_QUOC",
            destinationName:
              room.destinationName ?? meta?.destinationName ?? "Phú Quốc",
            propertyName:
              room.propertyName ?? meta?.propertyName ?? "HotelAI Phú Quốc",
          };
        }),
      );
      setServices(
        serviceData.map((service) => ({
          id: service.id,
          hotelId: 1,
          code: service.code,
          name: service.name,
          unit: service.unit,
          price: Number(service.currentPrice.unitPrice),
        })),
      );
      setRefunds(
        refundData.content.map((refund) => ({
          id: refund.id,
          bookingCode: `Thanh toán ${refund.paymentId.slice(0, 8)}`,
          amount: Number(refund.amount),
          reason: refund.reason,
          status: refund.status,
        })),
      );
      setTypes(
        sellableTypes.map((type) => ({
          id: type.id,
          hotelId: 1,
          code: type.code,
          name: type.name,
          description: type.description,
          maxAdults: type.maxAdults,
          maxChildren: type.maxChildren,
          basePrice: 0,
          amenities: type.amenities.map((amenity) => amenity.name),
          destinationCode: type.destinationCode ?? "PHU_QUOC",
          destinationName: type.destinationName ?? "Phú Quốc",
          propertyName: type.propertyName ?? "HotelAI Phú Quốc",
        })),
      );
      setWalkIn((current) => ({
        ...current,
        roomTypeId:
          current.roomTypeId || String(sellableTypes[0]?.id ?? ""),
      }));
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const checkIn = new Date();
      const checkOut = new Date();
      checkOut.setDate(checkOut.getDate() + 1);
      setWalkIn((current) => ({
        ...current,
        checkIn: checkIn.toISOString().slice(0, 10),
        checkOut: checkOut.toISOString().slice(0, 10),
      }));
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  function openCheckIn(booking: Booking) {
    const suitable = rooms.filter(
      (room) =>
        room.status === "AVAILABLE" &&
        booking.rooms.some(
          (line) => String(line.roomTypeId) === String(room.roomTypeId),
        ),
    );
    const roomIds = suitable
      .slice(0, booking.rooms.length)
      .map((room) => String(room.id));
    const guests: CheckInGuestDraft[] = [
      ...Array.from({ length: booking.adults ?? 1 }, (_, index) => ({
        category: "ADULT" as const,
        fullName: index === 0 ? booking.contactName ?? "" : "",
        dateOfBirth: "",
        nationality: "Việt Nam",
        documentType: "CCCD",
        documentNumber: "",
        primaryGuest: index === 0,
      })),
      ...Array.from({ length: booking.children ?? 0 }, () => ({
        category: "CHILD" as const,
        fullName: "",
        dateOfBirth: "",
        nationality: "Việt Nam",
        documentType: "GIAY_KHAI_SINH",
        documentNumber: "",
        primaryGuest: false,
      })),
    ];
    setCheckInDraft({
      booking,
      roomIds,
      guests,
      outstandingDepositAcknowledged: true,
      earlyCheckInApproved:
        new Date().toISOString().slice(0, 10) < booking.checkIn,
    });
  }

  function updateCheckInGuest(
    index: number,
    field: keyof CheckInGuestDraft,
    value: string | boolean,
  ) {
    if (!checkInDraft) return;
    setCheckInDraft({
      ...checkInDraft,
      guests: checkInDraft.guests.map((guest, guestIndex) =>
        guestIndex === index ? { ...guest, [field]: value } : guest,
      ),
    });
  }

  async function submitCheckIn(event: FormEvent) {
    event.preventDefault();
    if (!checkInDraft) return;
    const { booking, roomIds, guests } = checkInDraft;
    if (roomIds.length !== booking.rooms.length || roomIds.some((id) => !id)) {
      setError(`Vui lòng chọn đủ ${booking.rooms.length} phòng.`);
      return;
    }
    if (new Set(roomIds).size !== roomIds.length) {
      setError("Mỗi phòng chỉ được chọn một lần.");
      return;
    }
    const checkInCommandKey = crypto.randomUUID();
    const succeeded = await act(
      async () => {
        let synchronizationError: unknown;
        for (let attempt = 0; attempt < 20; attempt += 1) {
          try {
            return await api.post(
              "/admin/stays/check-ins",
              {
                reservationId: booking.id,
                roomIds,
                guests: guests.map((guest) => ({
                  fullName: guest.fullName,
                  dateOfBirth: guest.dateOfBirth,
                  nationality: guest.nationality,
                  documentType: guest.documentType,
                  documentNumber: guest.documentNumber,
                  primaryGuest: guest.primaryGuest,
                })),
                outstandingDepositAcknowledged:
                  checkInDraft.outstandingDepositAcknowledged,
                earlyCheckInApproved: checkInDraft.earlyCheckInApproved,
              },
              { "Idempotency-Key": checkInCommandKey },
            );
          } catch (reason) {
            if (
              !(reason instanceof ApiRequestError) ||
              reason.code !== "invalid-state"
            ) {
              throw reason;
            }
            synchronizationError = reason;
            await new Promise((resolve) => window.setTimeout(resolve, 500));
          }
        }
        throw synchronizationError;
      },
      `Đã nhận phòng cho ${booking.bookingCode}.`,
    );
    if (succeeded) setCheckInDraft(undefined);
  }

  async function checkout(booking: Booking) {
    const stayId = await resolveStayId(booking);
    if (!stayId) return;
    await act(
      () =>
        api.post(
          `/admin/stays/${stayId}/check-out`,
          { incidentals: [], debtOverride: false, debtOverrideReason: null },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Check-out và phát hành hóa đơn thành công.",
    );
  }

  async function addService(booking: Booking) {
    const values = await formDialog({
      title: "Thêm dịch vụ vào kỳ lưu trú",
      description: `Chọn dịch vụ phát sinh cho đặt phòng ${booking.bookingCode}. Chi phí sẽ được cộng vào hóa đơn cuối kỳ.`,
      confirmLabel: "Thêm dịch vụ",
      fields: [
        {
          name: "serviceId",
          label: "Dịch vụ",
          type: "select",
          required: true,
          defaultValue: String(services[0]?.id ?? ""),
          options: services.map((service) => ({
            value: String(service.id),
            label: `${service.name} · ${money(service.price)}/${service.unit}`,
          })),
        },
        { name: "quantity", label: "Số lượng", type: "number", required: true, min: 1, defaultValue: "1" },
      ],
    });
    if (!values) return;
    const stayId = await resolveStayId(booking);
    if (!stayId) return;
    await act(
      () =>
        api.post(
          "/admin/service-usages",
          {
            stayId,
            serviceId: values.serviceId,
            quantity: Number(values.quantity),
            occurredAt: new Date().toISOString(),
            performed: true,
            reason: "Ghi nhận tại quầy",
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã cộng dịch vụ vào booking.",
    );
  }

  async function resolveStayId(booking: Booking) {
    try {
      const stays = await api.get<Array<{ stayId: string; reservationId: string }>>(
        `/admin/service-stays?query=${encodeURIComponent(booking.bookingCode)}`,
      );
      const stay = stays.find(
        (item) => String(item.reservationId) === String(booking.id),
      );
      if (!stay) setError("Chưa tìm thấy hồ sơ lưu trú tương ứng với booking này.");
      return stay?.stayId;
    } catch (reason) {
      setError((reason as Error).message);
      return undefined;
    }
  }

  async function act(action: () => Promise<unknown>, success: string) {
    setError("");
    try {
      await action();
      setNotice(success);
      await load();
      return true;
    } catch (reason) {
      setError((reason as Error).message);
      return false;
    }
  }

  async function approveChange(request: ReservationChangeRequest) {
    await act(
      () =>
        api.post(
          `/admin/bookings/change-requests/${request.id}/approve`,
          { expectedVersion: request.version },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã duyệt yêu cầu đổi lịch. Ngày lưu trú, giá và tồn phòng đã được cập nhật.",
    );
  }

  async function rejectChange(request: ReservationChangeRequest) {
    const values = await formDialog({
      title: "Từ chối yêu cầu đổi lịch",
      description:
        "Khách sẽ nhận được lý do này. Hãy ghi rõ nguyên nhân và phương án hỗ trợ nếu có.",
      confirmLabel: "Xác nhận từ chối",
      tone: "danger",
      fields: [
        {
          name: "reason",
          label: "Lý do từ chối",
          type: "textarea",
          required: true,
          placeholder: "Ví dụ: Hạng phòng đã hết trong ngày khách yêu cầu…",
        },
      ],
    });
    if (!values) return;
    await act(
      () =>
        api.post(
          `/admin/bookings/change-requests/${request.id}/reject`,
          { expectedVersion: request.version, reason: values.reason.trim() },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã từ chối yêu cầu đổi lịch và lưu lý do.",
    );
  }

  async function createWalkIn(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      if (!walkIn.roomTypeId) throw new Error("Vui lòng chọn loại phòng.");
      if (!walkIn.contactName.trim() || !walkIn.contactEmail.trim()) {
        throw new Error("Vui lòng nhập tên và email liên hệ của khách.");
      }
      let customerId = walkIn.customerId;
      if (!customerId) {
        const customer = await api.post<{ id: string }>("/admin/customers", {
          fullName: walkIn.contactName.trim(),
          email: walkIn.contactEmail.trim(),
          phone: walkIn.contactPhone.trim() || undefined,
        });
        customerId = customer.id;
      }
      const booking = await api.post<Reservation>(
        `/admin/bookings?customerId=${encodeURIComponent(customerId)}&channel=STAFF`,
        {
          roomTypeId: walkIn.roomTypeId,
          checkIn: walkIn.checkIn,
          checkOut: walkIn.checkOut,
          adults: Number(walkIn.adults),
          children: 0,
          roomCount: 1,
          contactName: walkIn.contactName,
          contactEmail: walkIn.contactEmail,
          contactPhone: walkIn.contactPhone,
          guests: [{ fullName: walkIn.contactName, nationality: "VN", primary: true }],
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      const cashCommandKey = crypto.randomUUID();
      let cashRecorded = false;
      let cashError: unknown;
      for (let attempt = 0; attempt < 20; attempt += 1) {
        try {
          await api.post(
            "/admin/payments/cash",
            {
              reservationId: booking.id,
              purpose: "FULL",
              amount: booking.totalAmount,
              currency: booking.currency,
              note: "Thanh toán tiền mặt tại quầy",
            },
            { "Idempotency-Key": cashCommandKey },
          );
          cashRecorded = true;
          break;
        } catch (reason) {
          if (
            !(reason instanceof ApiRequestError) ||
            reason.code !== "reservation-projection-unavailable"
          ) {
            throw reason;
          }
          cashError = reason;
          await new Promise((resolve) => window.setTimeout(resolve, 500));
        }
      }
      if (!cashRecorded) throw cashError;
      let confirmedBooking: Reservation | undefined;
      for (let attempt = 0; attempt < 20; attempt += 1) {
        const current = await api.get<Reservation>(
          `/admin/bookings/${booking.id}`,
        );
        if (current.status === "PENDING_CONFIRMATION") {
          confirmedBooking = await api.post<Reservation>(
            `/admin/bookings/${booking.id}/confirm`,
            { expectedVersion: current.version },
            { "Idempotency-Key": crypto.randomUUID() },
          );
          break;
        }
        if (current.status === "CONFIRMED") {
          confirmedBooking = current;
          break;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 500));
      }
      if (!confirmedBooking) {
        throw new Error(
          "Đã ghi nhận tiền mặt nhưng booking chưa sẵn sàng để xác nhận. Vui lòng mở lại booking sau vài giây và bấm Xác nhận.",
        );
      }
      setNotice(
        `Đã tạo và xác nhận đặt phòng tại quầy ${confirmedBooking.reference}, lưu hồ sơ khách và ghi nhận đủ tiền mặt.`,
      );
      setWalkIn({
        ...walkIn,
        customerId: "",
        contactName: "",
        contactEmail: "",
        contactPhone: "",
      });
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function searchCustomers() {
    setError("");
    try {
      const page = await api.get<
        PageResponse<{
          id: string;
          fullName: string;
          email: string;
          phone?: string;
          piiMasked: boolean;
        }>
      >(`/admin/customers?query=${encodeURIComponent(customerQuery)}&size=20`);
      setCustomerOptions(page.content);
      if (!page.content.length)
        setNotice("Không tìm thấy khách hàng phù hợp.");
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function selectCustomer(customerId: string) {
    if (!customerId) {
      setWalkIn({ ...walkIn, customerId: "" });
      return;
    }
    try {
      const detail = await api.get<{
        id: string;
        fullName: string;
        email: string;
        phone?: string;
        piiMasked: boolean;
      }>(`/admin/customers/${customerId}`);
      setWalkIn({
        ...walkIn,
        customerId: detail.id,
        contactName: detail.fullName,
        contactEmail: detail.piiMasked ? "" : detail.email,
        contactPhone: detail.piiMasked ? "" : detail.phone || "",
      });
      setNotice(
        detail.piiMasked
          ? "Đã chọn khách; tài khoản hiện tại không có quyền xem PII nên cần nhập thông tin liên hệ."
          : "Đã điền thông tin khách hàng.",
      );
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  const filteredBookings = useMemo(
    () =>
      destinationFilter === "ALL"
        ? bookings
        : bookings.filter(
            (b) => (b.destinationCode ?? "PHU_QUOC") === destinationFilter,
          ),
    [bookings, destinationFilter],
  );

  const filteredRooms = useMemo(
    () =>
      destinationFilter === "ALL"
        ? rooms
        : rooms.filter(
            (r) => (r.destinationCode ?? "PHU_QUOC") === destinationFilter,
          ),
    [rooms, destinationFilter],
  );

  const walkInTypes = useMemo(
    () =>
      walkIn.destinationCode === "ALL"
        ? types
        : types.filter(
            (t) =>
              (t.destinationCode ?? "PHU_QUOC") === walkIn.destinationCode,
          ),
    [types, walkIn.destinationCode],
  );

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>ĐẶT PHÒNG · THANH TOÁN · LƯU TRÚ</span>
          <h1>Bàn lễ tân</h1>
          <p>
            Theo dõi và xử lý hành trình khách theo từng cơ sở HotelAI trên toàn
            quốc.
          </p>
        </div>
        <div className="row-actions" style={{ gap: "12px" }}>
          <label className="compact-field">
            Cơ sở / Điểm đến
            <select
              value={destinationFilter}
              onChange={(event) => setDestinationFilter(event.target.value)}
            >
              <option value="ALL">Toàn chuỗi (10 cơ sở)</option>
              {destinations.map((dest) => (
                <option key={dest.code} value={dest.code}>
                  {dest.name} · {dest.propertyName}
                </option>
              ))}
            </select>
          </label>
          <label className="compact-field">
            Trạng thái
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              <option value="">Tất cả</option>
              {[
                "PENDING_PAYMENT",
                "CONFIRMED",
                "CHECKED_IN",
                "CHECKED_OUT",
                "CANCELLED",
              ].map((value) => (
                <option key={value} value={value}>
                  {viLabel(value)}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <details className="walkin-panel panel">
        <summary>+ Tạo booking tại quầy</summary>
        <form className="form-grid compact-form" onSubmit={createWalkIn}>
          <div className="form-row">
            <label>
              Tìm khách hàng
              <input
                value={customerQuery}
                onChange={(event) => setCustomerQuery(event.target.value)}
                placeholder="Tên, email hoặc điện thoại"
              />
            </label>
            <button type="button" onClick={() => void searchCustomers()}>
              Tìm
            </button>
            <label>
              Hồ sơ khách cũ (không bắt buộc)
              <select
                value={walkIn.customerId}
                onChange={(event) => void selectCustomer(event.target.value)}
              >
                <option value="">Khách mới — hệ thống tự tạo hồ sơ</option>
                {customerOptions.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.fullName} · {customer.email}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <p className="form-hint">
            Khách mới không cần có tài khoản. Chỉ cần nhập thông tin liên hệ bên dưới;
            hệ thống sẽ tự tạo hồ sơ khách tại quầy.
          </p>
          <div className="form-row">
            <label>
              Điểm đến / Cơ sở
              <select
                value={walkIn.destinationCode}
                onChange={(event) => {
                  const destCode = event.target.value;
                  const matching =
                    destCode === "ALL"
                      ? types
                      : types.filter(
                          (t) =>
                            (t.destinationCode ?? "PHU_QUOC") === destCode,
                        );
                  setWalkIn({
                    ...walkIn,
                    destinationCode: destCode,
                    roomTypeId: String(matching[0]?.id ?? ""),
                  });
                }}
              >
                <option value="ALL">Tất cả cơ sở</option>
                {destinations.map((dest) => (
                  <option key={dest.code} value={dest.code}>
                    {dest.name} · {dest.propertyName}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Loại phòng
              <select
                value={walkIn.roomTypeId}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, roomTypeId: event.target.value })
                }
              >
                {walkInTypes.map((type) => (
                  <option key={type.id} value={type.id}>
                    [{type.destinationName ?? "Phú Quốc"}] {type.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Người lớn
              <input
                type="number"
                min="1"
                value={walkIn.adults}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, adults: event.target.value })
                }
              />
            </label>
          </div>
          <div className="form-row">
            <label>
              Nhận phòng
              <input
                type="date"
                required
                value={walkIn.checkIn}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, checkIn: event.target.value })
                }
              />
            </label>
            <label>
              Trả phòng
              <input
                type="date"
                min={walkIn.checkIn}
                required
                value={walkIn.checkOut}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, checkOut: event.target.value })
                }
              />
            </label>
          </div>
          <div className="form-row">
            <label>
              Tên khách
              <input
                required
                value={walkIn.contactName}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, contactName: event.target.value })
                }
              />
            </label>
            <label>
              Email
              <input
                type="email"
                required
                value={walkIn.contactEmail}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, contactEmail: event.target.value })
                }
              />
            </label>
            <label>
              Điện thoại
              <input
                value={walkIn.contactPhone}
                onChange={(event) =>
                  setWalkIn({ ...walkIn, contactPhone: event.target.value })
                }
              />
            </label>
          </div>
          <button className="primary">Tạo booking & xác nhận tiền mặt</button>
        </form>
      </details>
      {!!refunds.filter((refund) => refund.status === "PENDING").length && (
        <section className="refund-strip">
          <strong>Yêu cầu hoàn tiền</strong>
          {refunds
            .filter((refund) => refund.status === "PENDING")
            .map((refund) => (
              <article key={refund.id}>
                <span>
                  {refund.bookingCode} · {money(refund.amount)}
                </span>
                <small>{refund.reason}</small>
                <button
                  onClick={() =>
                    void act(
                      () =>
                        api.post(
                          `/admin/refunds/${refund.id}/approve`,
                          {},
                          { "Idempotency-Key": crypto.randomUUID() },
                        ),
                      "Đã hoàn tiền và cập nhật booking.",
                    )
                  }
                >
                  Duyệt hoàn
                </button>
              </article>
            ))}
        </section>
      )}
      {!!Object.keys(pendingChanges).length && (
        <section className="change-request-strip">
          <div className="change-request-strip-heading">
            <div>
              <span>YÊU CẦU CẦN XỬ LÝ</span>
              <strong>Khách đề nghị đổi ngày lưu trú</strong>
            </div>
            <b>{Object.keys(pendingChanges).length}</b>
          </div>
          {bookings
            .filter((booking) => pendingChanges[String(booking.id)])
            .map((booking) => {
              const request = pendingChanges[String(booking.id)];
              return (
                <article key={request.id}>
                  <div>
                    <strong>{booking.bookingCode}</strong>
                    <small>
                      Hiện tại: {booking.checkIn} → {booking.checkOut}
                    </small>
                  </div>
                  <div>
                    <span>Khách yêu cầu</span>
                    <strong>
                      {request.requestedChange.checkIn} →{" "}
                      {request.requestedChange.checkOut}
                    </strong>
                  </div>
                  <div className="row-actions">
                    <button onClick={() => void rejectChange(request)}>
                      Từ chối
                    </button>
                    <button
                      className="primary"
                      onClick={() => void approveChange(request)}
                    >
                      Duyệt thay đổi
                    </button>
                  </div>
                </article>
              );
            })}
        </section>
      )}
      {selectedStayBooking && (
        <AdminStayActions
          booking={selectedStayBooking}
          rooms={rooms}
          onClose={() => setSelectedStayBooking(undefined)}
          onCompleted={load}
        />
      )}
      {checkInDraft && (
        <div className="modal-backdrop" role="presentation">
          <form
            className="booking-modal checkin-modal"
            onSubmit={submitCheckIn}
            aria-label={`Nhận phòng ${checkInDraft.booking.bookingCode}`}
          >
            <div className="modal-heading">
              <div>
                <span>
                  THỦ TỤC NHẬN PHÒNG ·{" "}
                  <AppIcon name="map-pin" className="inline-pin-icon" />{" "}
                  {checkInDraft.booking.propertyName ?? "HotelAI Phú Quốc"}
                </span>
                <h2>{checkInDraft.booking.bookingCode}</h2>
                <p>
                  {checkInDraft.booking.adults ?? 1} người lớn ·{" "}
                  {checkInDraft.booking.children ?? 0} trẻ em ·{" "}
                  {checkInDraft.booking.rooms.length} phòng
                </p>
              </div>
              <button type="button" onClick={() => setCheckInDraft(undefined)} aria-label="Đóng">
                ×
              </button>
            </div>
            <section className="checkin-section">
              <div className="checkin-section-title">
                <b>1. Phân phòng</b>
                <span>Chỉ hiển thị phòng đang trống đúng loại đã đặt.</span>
              </div>
              <div className="checkin-room-grid">
                {Array.from(
                  { length: checkInDraft.booking.rooms.length },
                  (_, index) => (
                    <label key={index}>
                      Phòng {index + 1}
                      <select
                        required
                        value={checkInDraft.roomIds[index] ?? ""}
                        onChange={(event) => {
                          const roomIds = [...checkInDraft.roomIds];
                          roomIds[index] = event.target.value;
                          setCheckInDraft({ ...checkInDraft, roomIds });
                        }}
                      >
                        <option value="">Chọn phòng trống</option>
                        {rooms
                          .filter(
                            (room) =>
                              room.status === "AVAILABLE" &&
                              checkInDraft.booking.rooms.some(
                                (line) =>
                                  String(line.roomTypeId) === String(room.roomTypeId),
                              ),
                          )
                          .map((room) => (
                            <option key={room.id} value={String(room.id)}>
                              Phòng {room.roomNumber} · {room.floor || "Chưa rõ tầng"}
                            </option>
                          ))}
                      </select>
                    </label>
                  ),
                )}
              </div>
            </section>
            <section className="checkin-section">
              <div className="checkin-section-title">
                <b>2. Thông tin người lưu trú</b>
                <span>
                  Cần nhập đủ đúng số người trong booking; khách chính phải từ 18 tuổi.
                </span>
              </div>
              <div className="guest-form-list">
                {checkInDraft.guests.map((guest, index) => (
                  <fieldset className="guest-form" key={index}>
                    <legend>
                      {guest.category === "ADULT" ? "Người lớn" : "Trẻ em"} {index + 1}
                      {guest.primaryGuest ? " · Khách chính" : ""}
                    </legend>
                    <label>
                      Họ và tên
                      <input
                        required
                        value={guest.fullName}
                        onChange={(event) =>
                          updateCheckInGuest(index, "fullName", event.target.value)
                        }
                      />
                    </label>
                    <label>
                      Ngày sinh
                      <input
                        type="date"
                        required
                        max={new Date().toISOString().slice(0, 10)}
                        value={guest.dateOfBirth}
                        onChange={(event) =>
                          updateCheckInGuest(index, "dateOfBirth", event.target.value)
                        }
                      />
                    </label>
                    <label>
                      Quốc tịch
                      <input
                        required
                        value={guest.nationality}
                        onChange={(event) =>
                          updateCheckInGuest(index, "nationality", event.target.value)
                        }
                      />
                    </label>
                    <label>
                      Loại giấy tờ
                      <select
                        value={guest.documentType}
                        onChange={(event) =>
                          updateCheckInGuest(index, "documentType", event.target.value)
                        }
                      >
                        <option value="CCCD">CCCD</option>
                        <option value="PASSPORT">Hộ chiếu</option>
                        <option value="GIAY_KHAI_SINH">Giấy khai sinh</option>
                      </select>
                    </label>
                    <label className="span-2">
                      Số giấy tờ
                      <input
                        required
                        value={guest.documentNumber}
                        onChange={(event) =>
                          updateCheckInGuest(index, "documentNumber", event.target.value)
                        }
                      />
                    </label>
                  </fieldset>
                ))}
              </div>
            </section>
            <section className="checkin-confirmations">
              <label>
                <input
                  type="checkbox"
                  checked={checkInDraft.outstandingDepositAcknowledged}
                  onChange={(event) =>
                    setCheckInDraft({
                      ...checkInDraft,
                      outstandingDepositAcknowledged: event.target.checked,
                    })
                  }
                />
                Lễ tân đã kiểm tra và xác nhận tình trạng thanh toán/đặt cọc.
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={checkInDraft.earlyCheckInApproved}
                  onChange={(event) =>
                    setCheckInDraft({
                      ...checkInDraft,
                      earlyCheckInApproved: event.target.checked,
                    })
                  }
                />
                Cho phép nhận phòng sớm hơn ngày dự kiến {checkInDraft.booking.checkIn}.
              </label>
            </section>
            <div className="modal-actions">
              <button type="button" onClick={() => setCheckInDraft(undefined)}>
                Hủy
              </button>
              <button className="primary">Hoàn tất nhận phòng</button>
            </div>
          </form>
        </div>
      )}
      <section className="panel data-panel">
        <div className="table-wrap frontdesk-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Booking</th>
                <th>Lưu trú</th>
                <th>Phòng</th>
                <th>Tài chính</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredBookings.map((booking) => (
                <tr key={booking.id}>
                  <td>
                    <strong>{booking.bookingCode}</strong>
                    <small>#{booking.id}</small>
                  </td>
                  <td>
                    {booking.checkIn}
                    <br />
                    <small>đến {booking.checkOut}</small>
                  </td>
                  <td>
                    <strong>
                      {booking.rooms
                        .map((room) => room.roomNumber ?? room.roomType)
                        .join(", ")}
                    </strong>
                    <br />
                    <small>
                      <AppIcon name="map-pin" className="inline-pin-icon" />
                      {booking.propertyName ?? "HotelAI Phú Quốc"} ·{" "}
                      {booking.destinationName ?? "Phú Quốc"}
                    </small>
                  </td>
                  <td>
                    <strong>{money(booking.totalAmount)}</strong>
                    <small>Đã thu {money(booking.paidAmount)}</small>
                  </td>
                  <td>
                    <span className={`status ${booking.status.toLowerCase()}`}>
                      {viLabel(booking.status)}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      {booking.status === "CONFIRMED" && (
                        <button onClick={() => openCheckIn(booking)}>
                          Nhận phòng
                        </button>
                      )}
                      {booking.status === "CHECKED_IN" && (
                        <button onClick={() => void addService(booking)}>
                          + Dịch vụ
                        </button>
                      )}
                      {["CHECKED_IN", "CHECKED_OUT"].includes(
                        booking.status,
                      ) && (
                        <button
                          onClick={() => setSelectedStayBooking(booking)}
                        >
                          Lưu trú & hóa đơn
                        </button>
                      )}
                      {booking.status === "CHECKED_IN" && (
                        <button
                          className="primary"
                          onClick={() => void checkout(booking)}
                        >
                          Check-out
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!filteredBookings.length && (
                <tr>
                  <td colSpan={6} className="empty-cell">
                    Không có booking trong bộ lọc này.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
      <div className="availability-strip">
        <strong>
          Tình trạng phòng hôm nay
          {destinationFilter !== "ALL"
            ? ` (${destinations.find((d) => d.code === destinationFilter)?.name ?? destinationFilter})`
            : ""}
        </strong>
        {(
          ["AVAILABLE", "OCCUPIED", "WAITING_CLEANING", "MAINTENANCE"] as const
        ).map((value) => (
          <span key={value}>
            <i className={`dot dot-${value.toLowerCase()}`} />
            {viLabel(value)}:{" "}
            {filteredRooms.filter((room) => room.status === value).length}
          </span>
        ))}
      </div>
    </>
  );
}
