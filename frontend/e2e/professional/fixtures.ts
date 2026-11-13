import { execFileSync } from "node:child_process";

import { expect, Page } from "@playwright/test";

import { apiBase } from "../helpers";

export type TestCustomer = {
  email: string;
  password: string;
  fullName: string;
  phone: string;
};

export function uniqueCustomer(prefix = "journey"): TestCustomer {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 10_000)}`;
  return {
    email: `${prefix}.${suffix}@example.test`,
    password: "HotelAI@12345",
    fullName: `Khách kiểm thử ${suffix}`,
    phone: `09${suffix.slice(-8).padStart(8, "0")}`,
  };
}

function postgresContainer(): string {
  const configured = process.env.E2E_POSTGRES_CONTAINER?.trim();
  if (configured) return configured;
  const names = execFileSync(
    "docker",
    ["ps", "--filter", "ancestor=pgvector/pgvector:pg17", "--format", "{{.Names}}"],
    { encoding: "utf8" },
  )
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
  if (!names.length) {
    throw new Error(
      "Không tìm thấy PostgreSQL container. Hãy khởi động docker compose hoặc đặt E2E_POSTGRES_CONTAINER.",
    );
  }
  return names[0];
}

export async function otpFromIdentityOutbox(
  email: string,
  purpose: "ACCOUNT_ACTIVATION" | "RESET_PASSWORD",
): Promise<string> {
  const safeEmail = email.replaceAll("'", "''");
  const safePurpose = purpose.replaceAll("'", "''");
  const query = [
    "SELECT payload->'variables'->>'otp'",
    "FROM outbox_events",
    "WHERE event_type = 'notification.otp.requested.v1'",
    `AND lower(payload->>'recipient') = lower('${safeEmail}')`,
    `AND payload->>'purpose' = '${safePurpose}'`,
    "ORDER BY occurred_at DESC LIMIT 1;",
  ].join(" ");

  let value = "";
  await expect
    .poll(
      () => {
        value = execFileSync(
          "docker",
          [
            "exec",
            postgresContainer(),
            "psql",
            "-U",
            process.env.E2E_DB_USER ?? "hotel_admin",
            "-d",
            "identity_db",
            "-Atc",
            query,
          ],
          { encoding: "utf8" },
        ).trim();
        return value;
      },
      { timeout: 15_000, intervals: [250, 500, 1_000] },
    )
    .toMatch(/^\d{6}$/);
  return value;
}

export async function registerCustomerThroughUi(
  page: Page,
  customer = uniqueCustomer(),
): Promise<TestCustomer> {
  await page.goto("/login");
  await page
    .getByRole("button", { name: "Chưa có tài khoản? Đăng ký" })
    .click();
  await page.getByLabel("Họ và tên").fill(customer.fullName);
  await page.getByLabel("Số điện thoại").fill(customer.phone);
  await page.getByLabel("Email").fill(customer.email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(customer.password);
  await page.getByRole("button", { name: "Gửi mã xác minh" }).click();
  await expect(page.getByText(/Mã xác minh đã gửi tới/)).toBeVisible();

  await page.getByLabel("Mã xác minh gồm 6 số").fill("000000");
  await page.getByRole("button", { name: "Xác minh và đăng nhập" }).click();
  await expect(page.locator(".app-toast.toast-error").last()).toBeVisible();

  const otp = await otpFromIdentityOutbox(customer.email, "ACCOUNT_ACTIVATION");
  await page.getByLabel("Mã xác minh gồm 6 số").fill(otp);
  await page.getByRole("button", { name: "Xác minh và đăng nhập" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("link", { name: /Tài khoản/ })).toBeVisible();
  return customer;
}

export async function loginCustomerThroughUi(page: Page, customer: TestCustomer) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(customer.email);
  await page.getByLabel("Mật khẩu").fill(customer.password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
}

export async function currentAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => window.localStorage.getItem("accessToken") ?? "");
}

export async function assertGatewayHealthy(page: Page) {
  const response = await page.request.get(`${apiBase}/catalog/hotels/1/room-types`);
  expect(response.ok(), await response.text()).toBeTruthy();
}
