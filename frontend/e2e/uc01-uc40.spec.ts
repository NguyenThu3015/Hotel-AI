import { expect, test } from "@playwright/test";

import {
  adminToken,
  apiBase,
  authorizedGet,
  loginAdminUi,
  openAdminUiWithToken,
  tomorrow,
} from "./helpers";

test("UC01–UC05: giao diện danh tính, đăng nhập, hồ sơ và khôi phục mật khẩu", async ({
  page,
}) => {
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { name: "Chào mừng trở lại" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Chưa có tài khoản? Đăng ký" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Tạo tài khoản" }),
  ).toBeVisible();
  await expect(page.getByLabel("Họ và tên")).toBeVisible();
  await page
    .getByRole("button", { name: "Đã có tài khoản? Đăng nhập" })
    .click();
  await page.getByRole("button", { name: "Quên mật khẩu?" }).click();
  await expect(
    page.getByRole("heading", { name: "Khôi phục mật khẩu" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Quay lại đăng nhập" }).click();
  await loginAdminUi(page);
});

test("UC06–UC08, UC34–UC35: tìm, lọc, xem phòng và hội thoại/gợi ý AI thật", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.goto("/");
  const dates = page.locator('input[type="date"]');
  // Keep the browser smoke independent from the optional sample reservations
  // (which intentionally occupy the first 100 days).
  await dates.nth(0).fill(tomorrow(120));
  await dates.nth(1).fill(tomorrow(122));
  await page.getByRole("button", { name: "Tìm phòng trống" }).click();
  await expect(
    page.getByRole("heading", { name: "Kết quả theo thời gian thực" }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Gợi ý bằng AI/ }).click();
  await expect(page.getByText(/AI phù hợp/).first()).toBeVisible();
  await page.getByRole("button", { name: /Hỏi trợ lý AI/ }).click();
  await page
    .getByPlaceholder("Tôi cần phòng cho 2 người...")
    .fill(
      `Tôi cần phòng cho 2 người từ ${tomorrow(120)} đến ${tomorrow(122)}, ngân sách 3 triệu.`,
    );
  await page.getByRole("button", { name: "Gửi" }).click();
  await expect(
    page.locator(".chat-messages .assistant").last(),
  ).not.toContainText("ngày nhận và trả phòng");
  expect(
    errors.filter((value) => /TypeError|CORS|status of 5\d\d/.test(value)),
  ).toEqual([]);
});

test("UC09–UC21: mọi API đặt phòng, thanh toán, lưu trú, dịch vụ, hóa đơn và đánh giá có chủ sở hữu rõ ràng", async ({
  request,
}) => {
  const token = await adminToken(request);
  const endpoints = [
    "/bookings",
    "/admin/bookings?size=5",
    "/admin/stay-reservations?query=",
    "/admin/service-stays",
    "/admin/hotel-services",
    "/admin/invoice-stays",
    "/admin/refunds",
    "/admin/reviews?size=5",
  ];
  for (const endpoint of endpoints) {
    await authorizedGet(request, token, endpoint);
  }

  const customerOnlyReviews = await request.get(`${apiBase}/review-stays`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(
    customerOnlyReviews.status(),
    "Admin must not read the customer-only review eligibility endpoint",
  ).toBe(403);

  const bookingPage = await authorizedGet(
    request,
    token,
    "/admin/bookings?size=1",
  );
  const bookings = (await bookingPage.json()) as {
    content: Array<{ id: string }>;
  };
  expect(bookings.content.length).toBeGreaterThan(0);
  await authorizedGet(
    request,
    token,
    `/admin/payments?reservationId=${bookings.content[0].id}`,
  );

  const invoiceStayResponse = await authorizedGet(
    request,
    token,
    "/admin/invoice-stays",
  );
  const invoiceStays = (await invoiceStayResponse.json()) as Array<{
    stayId: string;
  }>;
  expect(invoiceStays.length).toBeGreaterThan(0);
  await authorizedGet(
    request,
    token,
    `/admin/invoices?stayId=${invoiceStays[0].stayId}`,
  );
});

test("UC22–UC33: màn hình quản trị kho phòng, khách hàng, quyền, chính sách, vận hành và báo cáo", async ({
  page,
  request,
}) => {
  const token = await adminToken(request);
  const roomTypeResponse = await authorizedGet(
    request,
    token,
    "/admin/room-types",
  );
  const roomTypes = (await roomTypeResponse.json()) as Array<{ id: string }>;
  expect(roomTypes.length).toBeGreaterThan(0);
  await openAdminUiWithToken(page, token);
  for (const label of [
    "Kho phòng & giá",
    "Vận hành phòng",
    "Khách hàng",
    "Phân quyền",
    "Chính sách",
    "Quản trị hệ thống",
  ]) {
    await page.getByRole("button", { name: new RegExp(label) }).click();
    await expect(page.locator(".admin-workspace h1").first()).toBeVisible();
  }

  for (const endpoint of [
    "/admin/rooms",
    "/admin/room-types",
    "/housekeeping/tasks",
    "/maintenance",
    "/admin/customers?query=&size=5",
    "/admin/staff?size=5",
    "/admin/roles",
    "/admin/permissions",
    `/admin/pricing/base-rates?roomTypeId=${roomTypes[0].id}`,
    "/admin/pricing/promotions",
    "/admin/pricing/policies",
    "/notifications",
    "/admin/reports/dashboard?hotelId=1&from=2026-01-01&to=2026-12-31",
    "/admin/audit-logs?size=5",
  ]) {
    await authorizedGet(request, token, endpoint);
  }
});

test("UC36–UC40: 5 AI deployable qua Gateway, human-in-the-loop và RAG nhân viên", async ({
  page,
  request,
}) => {
  const token = await adminToken(request);
  const headers = { Authorization: `Bearer ${token}` };
  for (const [path, data] of [
    [
      "/ai/chat",
      {
        message:
          "Tôi cần một phòng yên tĩnh cho hai người vào cuối tuần, bạn tư vấn tự nhiên giúp tôi nhé.",
      },
    ],
    ["/ai/forecast", { hotel_id: 1, days: 7, history_days: 90 }],
    ["/ai/anomalies", { hotel_id: 1, lookback_days: 30 }],
    ["/ai/risks/bookings/score", { hotel_id: 1, horizon_days: 30 }],
    [
      "/ai/staff-assistant/chat",
      { message: "Tóm tắt quy trình xử lý yêu cầu đặc biệt của khách." },
    ],
  ] as const) {
    const response = await request.post(`${apiBase}${path}`, { headers, data });
    const responseBody = await response.text();
    expect(response.ok(), `${path}: ${responseBody}`).toBeTruthy();
    if (path.endsWith("/chat")) {
      const chat = JSON.parse(responseBody) as {
        provider?: string;
        degraded?: boolean;
      };
      expect(chat.provider, `${path} did not use Gemini`).toBe("gemini");
      expect(chat.degraded, `${path} used a deterministic fallback`).toBe(
        false,
      );
    }
  }
  await authorizedGet(request, token, "/ai/revenue/constraints");
  await authorizedGet(request, token, "/ai/revenue/proposals");
  await authorizedGet(request, token, "/ai/risks/bookings?min_probability=0");

  await openAdminUiWithToken(page, token);
  await page.getByRole("button", { name: /Trung tâm AI/ }).click();
  await expect(
    page.getByRole("heading", { name: "AI vận hành có kiểm soát" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Trợ lý riêng cho nhân viên" }),
  ).toBeVisible();
  await expect(page.getByText(/chờ duyệt/).first()).toBeVisible();
});
