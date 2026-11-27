import { expect, test } from "@playwright/test";
import { adminToken, openAdminUiWithToken, tomorrow } from "./helpers";

test.describe("Liquid Glass Foundation & Global Chrome (Task 1)", () => {
  test("áp dụng biến thiết kế Liquid Glass, header kính mờ, chat widget và an toàn reduced-motion", async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/");

    const blurToken = await page.evaluate(() =>
      getComputedStyle(document.documentElement)
        .getPropertyValue("--lg-blur-chrome")
        .trim(),
    );
    expect(blurToken.length).toBeGreaterThan(0);

    const headerBackdrop = await page.locator(".site-header").evaluate((el) => {
      const s = getComputedStyle(el);
      return s.backdropFilter || s.getPropertyValue("-webkit-backdrop-filter");
    });
    expect(headerBackdrop).toContain("blur");

    const hiddenReveals = await page.locator("[data-reveal]").evaluateAll((els) =>
      els.filter((el) => getComputedStyle(el).opacity === "0").length,
    );
    expect(hiddenReveals).toBe(0);

    await page.getByRole("button", { name: /Hỏi trợ lý AI/ }).click();
    await expect(page.locator(".chat-panel")).toBeVisible();
    await expect(
      page.getByPlaceholder("Tôi cần phòng cho 2 người..."),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Gửi" })).toBeVisible();
  });

  test("giữ đỉnh .admin-workspace dưới 100px với admin header kính gọn", async ({
    page,
    request,
  }) => {
    const token = await adminToken(request);
    await openAdminUiWithToken(page, token);
    const box = await page.locator(".admin-workspace").boundingBox();
    expect(box?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(100);
  });
});

test.describe("Homepage Liquid Glass UX & Review Focus (Task 2)", () => {
  test("đặt #suites ngay dưới #search, lọc giá quảng bá >= 100.000₫ và điều khiển stepper −/+ an toàn", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Kết quả theo thời gian thực" }),
    ).toBeVisible();

    const suitesBox = await page.locator("#suites").boundingBox();
    const destBox = await page
      .locator(".vietnam-destinations-section")
      .boundingBox();
    expect(suitesBox).toBeTruthy();
    expect(destBox).toBeTruthy();
    expect(suitesBox!.y).toBeLessThan(destBox!.y);

    // Commercial starting price badges must never leak 1.000 ₫, Infinity, or NaN
    const heroSummaryText =
      (await page.locator(".hero-note").textContent()) ?? "";
    expect(heroSummaryText).not.toMatch(/\b1\.000\s*₫|Infinity|NaN/);

    const phuQuocCardText =
      (await page
        .locator(".destination-card")
        .filter({ hasText: "Phú Quốc" })
        .first()
        .textContent()) ?? "";
    expect(phuQuocCardText).not.toMatch(/Từ\s*1\.000\s*₫|Infinity|NaN/);

    // Guest +/- steppers with floor clamping
    const adultsInput = page.getByLabel("Người lớn");
    await expect(adultsInput).toHaveValue("2");
    await page.getByRole("button", { name: "Bớt 1 khách lớn" }).click();
    await expect(adultsInput).toHaveValue("1");
    await page.getByRole("button", { name: "Bớt 1 khách lớn" }).click();
    await expect(adultsInput).toHaveValue("1");
    await page.getByRole("button", { name: "Thêm 1 khách lớn" }).click();
    await expect(adultsInput).toHaveValue("2");

    // Date collision guard: setting checkIn >= checkOut advances checkOut automatically
    const checkInInput = page.getByLabel("Nhận phòng");
    const checkOutInput = page.getByLabel("Trả phòng");
    const futureCheckIn = tomorrow(15);
    const expectedMinCheckOut = tomorrow(16);
    await checkOutInput.fill(tomorrow(10));
    await checkInInput.fill(futureCheckIn);
    expect((await checkOutInput.inputValue()) >= expectedMinCheckOut).toBe(
      true,
    );
  });

  for (const viewport of [
    { name: "mobile", width: 390, height: 844 },
    { name: "tablet", width: 768, height: 1024 },
    { name: "desktop", width: 1366, height: 768 },
  ]) {
    test(`trang chủ ${viewport.name}: không tràn ngang và không có nút thiếu nhãn`, async ({
      page,
    }) => {
      await page.setViewportSize({
        width: viewport.width,
        height: viewport.height,
      });
      await page.goto("/");
      const overflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(1);
      await expect(
        page.getByRole("button", { name: "Tìm phòng trống" }),
      ).toBeVisible();
      const unnamed = await page
        .locator("button:not([aria-label])")
        .evaluateAll(
          (buttons) =>
            buttons.filter((button) => !(button.textContent ?? "").trim())
              .length,
        );
      expect(unnamed).toBe(0);
    });
  }
});

test.describe("Secondary Guest Pages Liquid Glass UX (Task 3)", () => {
  test("/rooms hiển thị thanh lọc kính vùng miền & phòng trống tức thì, không có nút thiếu nhãn", async ({
    page,
  }) => {
    await page.goto("/rooms");
    await expect(
      page.getByRole("heading", { name: "Khám phá mọi hạng phòng" }),
    ).toBeVisible();

    const availableFilter = page.getByRole("button", {
      name: "Còn phòng trống",
    });
    await expect(availableFilter).toBeVisible();
    await availableFilter.click();

    for (const region of ["Miền Bắc", "Miền Trung", "Miền Nam", "Tất cả"]) {
      await page.getByRole("button", { name: region }).click();
    }

    await page.setViewportSize({ width: 390, height: 844 });
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);

    const unnamed = await page
      .locator("button:not([aria-label])")
      .evaluateAll(
        (buttons) =>
          buttons.filter((button) => !(button.textContent ?? "").trim()).length,
      );
    expect(unnamed).toBe(0);
  });

  test("/login giữ nguyên luồng xác thực trên mặt kính và không tràn ngang trên mobile", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/login");
    await expect(
      page.getByRole("heading", { name: "Chào mừng trở lại" }),
    ).toBeVisible();
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
    const unnamed = await page
      .locator("button:not([aria-label])")
      .evaluateAll(
        (buttons) =>
          buttons.filter((button) => !(button.textContent ?? "").trim()).length,
      );
    expect(unnamed).toBe(0);
  });
});

test.describe("Admin Operations Cockpit Liquid Glass UX (Task 4)", () => {
  test("áp dụng không gian Frosted Pearl Cockpit, kính mờ trên sidebar/topbar, y < 100px và không tràn ngang trên mobile", async ({
    page,
    request,
  }) => {
    const token = await adminToken(request);
    await page.setViewportSize({ width: 1366, height: 768 });
    await openAdminUiWithToken(page, token);

    await expect(
      page.locator('.admin-layout[data-cockpit-surface="frosted-pearl"]'),
    ).toBeVisible();

    const topbarBlur = await page
      .locator(".workspace-topbar")
      .evaluate((el) => {
        const s = getComputedStyle(el);
        return s.backdropFilter || s.getPropertyValue("-webkit-backdrop-filter");
      });
    expect(topbarBlur).toContain("blur");

    const desktopBox = await page.locator(".admin-workspace").boundingBox();
    expect(desktopBox?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(100);

    // Mobile responsive check across data-heavy tabs
    await page.setViewportSize({ width: 390, height: 844 });
    const mobileBox = await page.locator(".admin-workspace").boundingBox();
    expect(mobileBox?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(100);

    for (const tabName of [
      /Tổng quan/i,
      /Bàn lễ tân/i,
      /Kho phòng & giá/i,
      /Trung tâm AI/i,
    ]) {
      const mobileToggle = page.locator(".sidebar-toggle");
      if (await mobileToggle.isVisible()) {
        await mobileToggle.click();
      }
      await page.getByRole("button", { name: tabName }).click();
      const overflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(1);

      const unnamed = await page
        .locator("button:not([aria-label])")
        .evaluateAll(
          (buttons) =>
            buttons.filter((button) => !(button.textContent ?? "").trim())
              .length,
        );
      expect(unnamed).toBe(0);
    }
  });
});

test.describe("Code Review Regression Fixes", () => {
  test("AppIcon kích thước chuẩn (<= 28px), .hero-metrics-grid dạng grid, .search-button không lệch lề và .chat-panel lấp đầy chiều cao", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto("/");

    const heroBox = await page.locator(".hero").boundingBox();
    expect(heroBox!.height).toBeLessThan(680);

    const metricsDisplay = await page
      .locator(".hero-metrics-grid")
      .evaluate((el) => getComputedStyle(el).display);
    expect(metricsDisplay).toBe("grid");

    const searchBtnMarginLeft = await page
      .locator(".search-button")
      .evaluate((el) => getComputedStyle(el).marginLeft);
    expect(searchBtnMarginLeft).toBe("0px");

    // Chat panel fills height without a large bottom void
    await page.getByRole("button", { name: /Hỏi trợ lý AI/ }).click();
    const panelBox = await page.locator(".chat-panel").boundingBox();
    const formBox = await page.locator(".chat-panel form").boundingBox();
    expect(panelBox!.y + panelBox!.height - (formBox!.y + formBox!.height)).toBeLessThan(36);

    // Mobile chat launcher keeps icon inside button bounds
    await page.locator('.chat-panel button[aria-label="Đóng trợ lý"]').click();
    await page.setViewportSize({ width: 390, height: 844 });
    const launcherBox = await page.locator(".chat-launcher").boundingBox();
    const launcherIconBox = await page
      .locator(".chat-launcher > span")
      .boundingBox();
    expect(launcherIconBox!.x).toBeGreaterThanOrEqual(launcherBox!.x - 1);

    // /login SVG icons stay <= 28px and .auth-story has padding
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto("/login");
    const loginIconBox = await page
      .locator(".auth-story ul li svg")
      .first()
      .boundingBox();
    expect(loginIconBox!.width).toBeLessThanOrEqual(28);
    const storyPadding = await page
      .locator(".auth-story")
      .evaluate((el) => parseFloat(getComputedStyle(el).paddingTop));
    expect(storyPadding).toBeGreaterThanOrEqual(24);
  });

  test("thẻ KPI trong /admin Overview có độ tương phản chữ tối rõ nét trên nền kính sáng", async ({
    page,
    request,
  }) => {
    const token = await adminToken(request);
    await openAdminUiWithToken(page, token);
    const thirdCard = page.locator(".metric-grid > article").nth(2);
    await expect(thirdCard).toBeVisible();
    const textColor = await thirdCard
      .locator("strong")
      .evaluate((el) => getComputedStyle(el).color);
    expect(textColor).not.toBe("rgb(255, 255, 255)");
  });
});
