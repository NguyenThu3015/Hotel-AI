import { expect, test } from "@playwright/test";

import { tomorrow } from "../helpers";
import {
  loginCustomerThroughUi,
  otpFromIdentityOutbox,
  registerCustomerThroughUi,
} from "./fixtures";

test.describe.serial("Hành trình khách hàng từ tài khoản đến sau đặt phòng", () => {
  test.setTimeout(180_000);

  test("đăng ký, OTP sai/đúng, cập nhật hồ sơ và đổi mật khẩu", async ({ page }) => {
    const customer = await registerCustomerThroughUi(page);

    await page.getByRole("link", { name: /Tài khoản/ }).click();
    await expect(page.getByRole("heading", { name: "Hồ sơ cá nhân" })).toBeVisible();
    await page.getByLabel("Họ và tên").fill(`${customer.fullName} đã cập nhật`);
    await page.getByLabel("Điện thoại").fill("0901234567");
    await page.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(page.getByText("Đã cập nhật hồ sơ.")).toBeVisible();

    await page.getByLabel("Mật khẩu hiện tại").fill(customer.password);
    await page.getByLabel("Mật khẩu mới", { exact: true }).fill("HotelAI@54321");
    await page.getByLabel("Nhập lại mật khẩu mới").fill("không-trùng-khớp");
    await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
    await expect(
      page
        .locator(".app-toast.toast-error")
        .filter({ hasText: "Mật khẩu xác nhận chưa trùng khớp" }),
    ).toBeVisible();

    await page.getByLabel("Nhập lại mật khẩu mới").fill("HotelAI@54321");
    await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
    await expect(page).toHaveURL(/\/login/);
    customer.password = "HotelAI@54321";
    await loginCustomerThroughUi(page, customer);

    await page.goto("/login");
    await page.getByRole("button", { name: "Quên mật khẩu?" }).click();
    await page.getByLabel("Email").fill(customer.email);
    await page.getByLabel("Mật khẩu mới").fill("HotelAI@67890");
    await page.getByRole("button", { name: "Gửi mã xác minh" }).click();
    const resetOtp = await otpFromIdentityOutbox(customer.email, "RESET_PASSWORD");
    await page.getByLabel("Mã xác minh gồm 6 số").fill(resetOtp);
    await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
    customer.password = "HotelAI@67890";
    await loginCustomerThroughUi(page, customer);
  });

  test("tìm phòng, dùng AI, giữ phòng, đổi ngày, mở VietQR và hủy", async ({
    page,
  }) => {
    const customer = await registerCustomerThroughUi(page, undefined);
    const consoleErrors: string[] = [];
    const serverErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("response", (response) => {
      if (response.status() >= 500) {
        serverErrors.push(`${response.status()} ${response.url()}`);
      }
    });

    await page.goto("/");
    const dates = page.locator('input[type="date"]');
    await dates.nth(0).fill(tomorrow(150));
    await dates.nth(1).fill(tomorrow(152));
    const started = Date.now();
    await page.getByRole("button", { name: "Tìm phòng trống" }).click();
    await expect(page.locator(".room-card").first()).toBeVisible();
    expect(Date.now() - started, "BR/NFR: tìm phòng phải dưới 3 giây").toBeLessThan(3_000);

    await page.getByRole("button", { name: "Gợi ý bằng AI" }).click();
    await expect(page.locator(".room-card").first()).toContainText(/AI phù hợp/);
    await page.locator(".room-card").first().getByRole("button", { name: "Chọn phòng này" }).click();
    await expect(page.locator(".booking-modal")).toBeVisible();
    await page.getByLabel("Loại giấy tờ").selectOption("CCCD");
    await page.getByLabel("Số giấy tờ").fill(`E2E${Date.now()}`);
    await page.getByLabel("Quốc tịch").selectOption("VN");
    await page.getByLabel("Yêu cầu đặc biệt").fill("Phòng yên tĩnh, không hút thuốc");
    await page.getByRole("button", { name: "Xác nhận giữ phòng" }).click();
    await expect(page).toHaveURL(/\/bookings/);

    const card = page.locator(".booking-list article").first();
    await expect(card).toBeVisible();
    await card.getByRole("button", { name: "Đổi ngày" }).click();
    await card.getByLabel("Ngày nhận phòng mới").fill(tomorrow(151));
    await card.getByLabel("Ngày trả phòng mới").fill(tomorrow(153));
    await card.getByRole("button", { name: "Xác nhận" }).click();
    await expect(
      page.locator(".app-toast.toast-success").filter({ hasText: /Đã đổi lịch|đã được gửi/ }),
    ).toBeVisible();

    await card.getByRole("button", { name: "Thanh toán" }).click();
    const transfer = page.getByRole("region", { name: "Thông tin chuyển khoản Casso" });
    await expect(transfer).toBeVisible();
    await expect(transfer.getByAltText("Mã VietQR thanh toán đặt phòng")).toBeVisible();
    await expect(transfer.locator(".payment-code-callout strong")).not.toBeEmpty();
    await transfer.getByRole("button", { name: "Sao chép mã" }).click();
    await expect(
      page.locator(".app-toast.toast-success").filter({ hasText: "Đã sao chép" }),
    ).toBeVisible();
    await transfer.getByRole("button", { name: "Kiểm tra thanh toán" }).click();
    await expect(
      page
        .locator(".app-toast.toast-success")
        .filter({ hasText: /Casso đã xác nhận|Chưa nhận được xác nhận từ Casso/ })
        .last(),
    ).toBeVisible();
    await transfer.getByRole("button", { name: "Đóng" }).click();

    await card.getByRole("button", { name: "Hủy", exact: true }).click();
    const cancelDialog = page.getByRole("dialog", { name: /Hủy đặt phòng/ });
    await expect(cancelDialog).toBeVisible();
    await cancelDialog.getByRole("button", { name: "Xác nhận hủy" }).click();
    await expect(
      page.locator(".app-toast.toast-success").filter({ hasText: "Đã hủy đặt phòng" }),
    ).toBeVisible();

    expect(serverErrors).toEqual([]);
    expect(
      consoleErrors.filter((message) => /TypeError|CORS|ERR_FAILED/i.test(message)),
    ).toEqual([]);
    expect(customer.email).toContain("@example.test");
  });
});
