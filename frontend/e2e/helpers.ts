import { APIRequestContext, expect, Page } from "@playwright/test";

export const apiBase =
  process.env.E2E_API_URL ?? "http://localhost:8081/api/v1";
export const adminEmail = process.env.E2E_ADMIN_EMAIL ?? "admin@hotelai.local";
export const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? "Admin@12345";

let cachedAdminToken: string | undefined;

export async function adminToken(request: APIRequestContext) {
  if (process.env.E2E_ADMIN_ACCESS_TOKEN) {
    return process.env.E2E_ADMIN_ACCESS_TOKEN;
  }
  if (cachedAdminToken) return cachedAdminToken;

  const response = await request.post(`${apiBase}/auth/login`, {
    data: { email: adminEmail, password: adminPassword },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = (await response.json()) as { accessToken: string };
  cachedAdminToken = body.accessToken;
  return cachedAdminToken;
}

export async function authorizedGet(
  request: APIRequestContext,
  token: string,
  path: string,
) {
  const response = await request.get(`${apiBase}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), `${path}: ${await response.text()}`).toBeTruthy();
  return response;
}

export async function loginAdminUi(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(adminEmail);
  await page.getByLabel("Mật khẩu").fill(adminPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/admin/);
  await expect(page.getByText("Trung tâm điều hành")).toBeVisible();
}

export async function openAdminUiWithToken(page: Page, accessToken: string) {
  await page.addInitScript((token) => {
    window.localStorage.setItem("accessToken", token);
  }, accessToken);
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin/);
  await expect(page.getByText("Trung tâm điều hành")).toBeVisible();
}

export function tomorrow(offset: number) {
  return new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);
}
