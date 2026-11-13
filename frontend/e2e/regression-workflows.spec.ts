import { expect, test } from "@playwright/test";

import {
  adminPassword,
  adminToken,
  apiBase,
  openAdminUiWithToken,
  tomorrow,
} from "./helpers";

type RoomType = { id: string; code: string; name: string };
type Room = {
  id: string;
  roomNumber: string;
  roomTypeId: string;
  status: string;
};
type Reservation = {
  id: string;
  reference: string;
  status: string;
  roomTypeId: string;
  checkIn: string;
  checkOut: string;
  roomCount: number;
  adults: number;
  children: number;
  specialRequests?: string | null;
  totalAmount: number;
  paidAmount: number;
  currency: string;
  version: number;
};

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });
const commandHeaders = (token: string) => ({
  ...authHeaders(token),
  "Idempotency-Key": crypto.randomUUID(),
});

test("Regression: phiên admin và đổi mật khẩu hoạt động qua Gateway", async ({
  request,
}) => {
  const token = await adminToken(request);
  const me = await request.get(`${apiBase}/auth/me`, {
    headers: authHeaders(token),
  });
  expect(me.status(), await me.text()).toBe(200);
  const profile = (await me.json()) as { roles: string[] };
  expect(profile.roles).toContain("ADMIN");

  const rejectedChange = await request.post(`${apiBase}/auth/change-password`, {
    headers: authHeaders(token),
    data: {
      currentPassword: `${adminPassword}-khong-dung`,
      newPassword: "AdminNew@12345",
    },
  });
  expect(rejectedChange.status(), await rejectedChange.text()).toBe(400);

  // The failed validation must not mutate the real administrator password.
  const loginAgain = await request.post(`${apiBase}/auth/login`, {
    data: { email: "admin@hotelai.local", password: adminPassword },
  });
  expect(loginAgain.status(), await loginAgain.text()).toBe(200);
});

test("Regression: giá theo ngày và quy tắc đủ năm ngày trong tuần", async ({
  request,
}) => {
  const token = await adminToken(request);
  const uniqueDateOffset = 300 + (Date.now() % 10_000);
  const typesResponse = await request.get(`${apiBase}/admin/room-types`, {
    headers: authHeaders(token),
  });
  expect(typesResponse.status(), await typesResponse.text()).toBe(200);
  const roomTypes = (await typesResponse.json()) as RoomType[];
  const deluxe = roomTypes.find((roomType) => roomType.code === "DELUXE");
  expect(deluxe, "Missing seeded DELUXE room type").toBeTruthy();

  const stayFrom = tomorrow(200);
  const stayTo = tomorrow(201);
  const baseRate = await request.post(`${apiBase}/admin/pricing/base-rates`, {
    headers: commandHeaders(token),
    data: {
      roomTypeId: deluxe!.id,
      amount: 1_450_000,
      stayFrom,
      stayTo,
      bookingEffectiveFrom: new Date().toISOString(),
      bookingEffectiveTo: null,
      outlierConfirmed: true,
    },
  });
  expect(baseRate.status(), await baseRate.text()).toBe(201);
  const createdRate = (await baseRate.json()) as {
    id: string;
    amount: number;
    stayFrom: string;
    stayTo: string;
  };
  expect(createdRate.amount).toBe(1_450_000);
  expect(createdRate.stayFrom).toBe(stayFrom);
  expect(createdRate.stayTo).toBe(stayTo);

  const rateList = await request.get(
    `${apiBase}/admin/pricing/base-rates?roomTypeId=${deluxe!.id}`,
    { headers: authHeaders(token) },
  );
  expect(rateList.status(), await rateList.text()).toBe(200);
  expect(
    ((await rateList.json()) as Array<{ id: string }>).some(
      (rate) => rate.id === createdRate.id,
    ),
  ).toBe(true);

  const rule = await request.post(`${apiBase}/admin/pricing/rules`, {
    headers: commandHeaders(token),
    data: {
      ruleKey: null,
      roomTypeId: deluxe!.id,
      name: `E2E ngày thường ${Date.now()}`,
      priceMode: "MULTIPLIER",
      value: 1,
      priority: 7000 + (Date.now() % 2000),
      stayFrom: tomorrow(uniqueDateOffset),
      stayTo: tomorrow(uniqueDateOffset + 7),
      daysOfWeek: [
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY",
      ],
      minGuests: null,
      maxGuests: null,
      bookingEffectiveFrom: new Date().toISOString(),
      bookingEffectiveTo: null,
      outlierConfirmed: true,
    },
  });
  expect(rule.status(), await rule.text()).toBe(201);
});

test("Regression: khách mới tại quầy, thanh toán, check-in và check-out", async ({
  page,
  request,
}) => {
  test.setTimeout(120_000);
  const token = await adminToken(request);
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const customerResponse = await request.post(`${apiBase}/admin/customers`, {
    headers: authHeaders(token),
    data: {
      fullName: `Khách E2E ${suffix}`,
      email: `e2e.walkin.${suffix}@example.test`,
      phone: "0901234567",
    },
  });
  expect(customerResponse.status(), await customerResponse.text()).toBe(201);
  const customer = (await customerResponse.json()) as { id: string };
  expect(customer.id).toBeTruthy();

  const [typesResponse, roomsResponse] = await Promise.all([
    request.get(`${apiBase}/admin/room-types`, {
      headers: authHeaders(token),
    }),
    request.get(`${apiBase}/admin/rooms`, { headers: authHeaders(token) }),
  ]);
  expect(typesResponse.status(), await typesResponse.text()).toBe(200);
  expect(roomsResponse.status(), await roomsResponse.text()).toBe(200);
  const roomTypes = (await typesResponse.json()) as RoomType[];
  const rooms = (await roomsResponse.json()) as Room[];
  const deluxe = roomTypes.find((roomType) => roomType.code === "DELUXE");
  const room = rooms.find(
    (candidate) =>
      candidate.roomTypeId === deluxe?.id && candidate.status === "AVAILABLE",
  );
  expect(deluxe, "Missing seeded DELUXE room type").toBeTruthy();
  expect(room, "No physical DELUXE room is available for check-in").toBeTruthy();

  const bookingResponse = await request.post(
    `${apiBase}/admin/bookings?customerId=${customer.id}&channel=STAFF`,
    {
      headers: commandHeaders(token),
      data: {
        roomTypeId: deluxe!.id,
        checkIn: tomorrow(0),
        checkOut: tomorrow(1),
        roomCount: 1,
        adults: 1,
        children: 0,
        contactName: `Khách E2E ${suffix}`,
        contactEmail: `e2e.walkin.${suffix}@example.test`,
        contactPhone: "0901234567",
        specialRequests: "Kiểm thử luồng tại quầy",
        promotionCode: null,
        guests: [
          {
            fullName: `Khách E2E ${suffix}`,
            documentType: "CCCD",
            documentNumber: `E2E${Date.now()}`,
            nationality: "VN",
            primary: true,
          },
        ],
      },
    },
  );
  expect(bookingResponse.status(), await bookingResponse.text()).toBe(201);
  let booking = (await bookingResponse.json()) as Reservation;

  const changeResponse = await request.put(
    `${apiBase}/admin/bookings/${booking.id}`,
    {
      headers: commandHeaders(token),
      data: {
        roomTypeId: booking.roomTypeId,
        checkIn: booking.checkIn,
        checkOut: tomorrow(2),
        roomCount: booking.roomCount,
        adults: booking.adults,
        children: booking.children,
        specialRequests: booking.specialRequests,
        expectedVersion: booking.version,
      },
    },
  );
  expect(changeResponse.status(), await changeResponse.text()).toBe(200);
  const changeOutcome = (await changeResponse.json()) as {
    applied: boolean;
    reservation: Reservation;
  };
  expect(changeOutcome.applied).toBe(true);
  expect(changeOutcome.reservation.checkOut).toBe(tomorrow(2));
  booking = changeOutcome.reservation;

  const cashHeaders = commandHeaders(token);
  let cashResponse = await request.post(`${apiBase}/admin/payments/cash`, {
    headers: cashHeaders,
    data: {
      reservationId: booking.id,
      purpose: "FULL",
      amount: booking.totalAmount,
      currency: booking.currency,
      note: "E2E tiền mặt tại quầy",
    },
  });
  for (
    let attempt = 0;
    cashResponse.status() === 409 && attempt < 20;
    attempt += 1
  ) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    cashResponse = await request.post(`${apiBase}/admin/payments/cash`, {
      headers: cashHeaders,
      data: {
        reservationId: booking.id,
        purpose: "FULL",
        amount: booking.totalAmount,
        currency: booking.currency,
        note: "E2E tiền mặt tại quầy",
      },
    });
  }
  expect(cashResponse.status(), await cashResponse.text()).toBe(201);

  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${apiBase}/admin/bookings/${booking.id}`,
          { headers: authHeaders(token) },
        );
        if (response.status() !== 200) return `HTTP_${response.status()}`;
        const current = (await response.json()) as Reservation;
        return `${current.status}:${current.paidAmount}`;
      },
      { timeout: 30_000 },
    )
    .toBe(`PENDING_CONFIRMATION:${booking.totalAmount}`);

  const pendingConfirmation = await request.get(
    `${apiBase}/admin/bookings/${booking.id}`,
    { headers: authHeaders(token) },
  );
  expect(pendingConfirmation.status(), await pendingConfirmation.text()).toBe(200);
  const currentBooking = (await pendingConfirmation.json()) as Reservation;
  const confirmation = await request.post(
    `${apiBase}/admin/bookings/${booking.id}/confirm`,
    {
      headers: commandHeaders(token),
      data: { expectedVersion: currentBooking.version },
    },
  );
  expect(confirmation.status(), await confirmation.text()).toBe(200);
  expect(((await confirmation.json()) as Reservation).status).toBe("CONFIRMED");

  await openAdminUiWithToken(page, token);
  await page.getByRole("button", { name: /Bàn lễ tân/ }).click();
  await page.locator(".walkin-panel summary").click();
  const optionalCustomer = page.getByLabel(/Hồ sơ khách cũ/);
  await expect(optionalCustomer).toBeVisible();
  await expect(optionalCustomer).not.toHaveAttribute("required", "");
  await expect(page.getByText(/Khách mới không cần có tài khoản/)).toBeVisible();

  const bookingRow = page.locator("tbody tr").filter({
    hasText: booking.reference,
  });
  await expect(bookingRow).toBeVisible();
  await bookingRow.getByRole("button", { name: "Nhận phòng" }).click();
  await expect(page.locator(".checkin-modal")).toBeVisible();
  await expect(page.locator(".guest-form")).toHaveCount(1);
  await expect(page.locator(".checkin-room-grid select")).toHaveCount(1);
  await page.locator('.checkin-modal button[aria-label="Đóng"]').click();

  const checkInHeaders = commandHeaders(token);
  const checkInData = {
    reservationId: booking.id,
    roomIds: [room!.id],
    guests: [
      {
        fullName: `Khách E2E ${suffix}`,
        dateOfBirth: "1990-01-01",
        nationality: "Việt Nam",
        documentType: "CCCD",
        documentNumber: `E2E${Date.now()}`,
        primaryGuest: true,
      },
    ],
    outstandingDepositAcknowledged: true,
    earlyCheckInApproved: true,
  };
  let checkInResponse = await request.post(
    `${apiBase}/admin/stays/check-ins`,
    {
      headers: checkInHeaders,
      data: checkInData,
    },
  );
  for (
    let attempt = 0;
    checkInResponse.status() === 409 && attempt < 20;
    attempt += 1
  ) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    checkInResponse = await request.post(
      `${apiBase}/admin/stays/check-ins`,
      { headers: checkInHeaders, data: checkInData },
    );
  }
  expect([201, 202], await checkInResponse.text()).toContain(
    checkInResponse.status(),
  );
  const stay = (await checkInResponse.json()) as { id: string; status: string };
  expect(stay.status).toBe("OPEN");

  const checkoutResponse = await request.post(
    `${apiBase}/admin/stays/${stay.id}/check-out`,
    {
      headers: commandHeaders(token),
      data: { incidentals: [], debtOverride: false, debtOverrideReason: null },
    },
  );
  expect([200, 201, 202]).toContain(checkoutResponse.status());
});
