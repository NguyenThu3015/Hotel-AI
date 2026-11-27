# Adaptive Dual-Surface Liquid Glass UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign both the HotelAI Guest Booking Portal (`/`, `/rooms`, `/bookings`, `/login`, `/profile`, `ChatWidget`) and the 9-module Staff/Admin Operations Center (`/admin`) into a cohesive, deep-layered **Adaptive Dual-Surface Liquid Glass** experience with fluid 60fps choreography, streamlined booking UX, and 100% Playwright `UC01–UC40` compatibility.

**Architecture:** Introduce a modular CSS token and material layer under `frontend/src/styles/` (`tokens.css`, `liquid-glass.css`, `guest-portal.css`, `admin-cockpit.css`) paired with a zero-dependency client motion controller (`frontend/src/components/liquid-motion.tsx`) for pointer-tracked glass refraction and progressive `IntersectionObserver` reveals. Separate optical translucent chrome (headers, floating booking bar, modals, chat widget, admin sidebar/topbar) from high-contrast data surfaces (`>= 4.5:1` WCAG AA) while preserving every API call, ARIA role, and Vietnamese label contract.

**Tech Stack:** Next.js 16.2.10 (App Router), React 19.2.4, TypeScript 5.9, Modern CSS (`backdrop-filter`, CSS custom properties, `@media (prefers-reduced-motion)`, `@media (prefers-reduced-transparency)`), Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-11-27-liquid-glass-ui-redesign-design.md`

## Global Constraints

- Zero external UI or animation npm packages added to `frontend/package.json`.
- Zero changes to backend microservices, database schemas, API Gateway routes, or business logic payloads.
- Google Fonts (`Playfair Display`, `Plus Jakarta Sans`, `Be Vietnam Pro` with Vietnamese subsets) must be loaded via standard `<link>` tags in `layout.tsx` (never `next/font/google`) so offline/isolated Alpine Docker `npm run build` in `frontend/Dockerfile` succeeds.
- Preserve 100% of Playwright E2E selectors, Vietnamese labels, headings, placeholders, and ARIA roles across `frontend/e2e/uc01-uc40.spec.ts`, `frontend/e2e/regression-workflows.spec.ts`, `frontend/e2e/professional/auth-customer-journey.spec.ts`, and `frontend/e2e/professional/admin-ai-quality.spec.ts`.
- On `/admin`, `.admin-workspace` top `y` coordinate must remain `< 100px` (`admin-ai-quality.spec.ts:63`).
- Every `<button>` on every page must have either non-empty text content or an explicit `aria-label` (`button:not([aria-label])` with empty text `=== 0`).
- Zero horizontal overflow (`documentElement.scrollWidth - clientWidth <= 1`) across `390x844`, `768x1024`, `1366x768`, and `1440x900`.
- Honor `@media (prefers-reduced-motion: reduce)` and `@media (prefers-reduced-transparency: reduce)`.

## Review Focus

1. **Icon-only `−/+` guest steppers or close buttons missing `aria-label`:** Fails `admin-ai-quality.spec.ts:176` and blocks screen-reader users; every stepper and icon button must carry an explicit Vietnamese `aria-label` (tested in Task 2).
2. **Empty commercial price array producing `Infinity ₫` or `NaN ₫`:** Filtering `< 100_000` on an array containing only a `1.000 ₫` test room yields `[]`, where `Math.min(...[]) === Infinity`; `commercialMinPrice(values)` must return `number | null` and render a clean fallback label when `null` (tested in Task 2).
3. **Guest stepper underflow or `checkIn >= checkOut` date collision:** Decrementing `adults` below `1`, `children` below `0`, `rooms` below `1`, or picking `checkIn >= checkOut` would trigger `400` API errors; clamping and auto-advancing `checkOut` by `+1` day must hold (tested in Task 2).
4. **Ambient aurora orbs or wide `/admin` tables causing mobile horizontal overflow (`390x844`):** Decorative layers must clip inside overflow-hidden wrappers and admin tables must scroll inside `.table-glass-scroll` containers without widening `documentElement.scrollWidth` (tested in Tasks 2 & 4).
5. **Progressive `[data-reveal]` hiding SSR content when `prefers-reduced-motion: reduce` is active or before observer hydration:** Elements must default to `opacity: 1; transform: none` and only animate from subtle offset when `html.lg-motion-ready` is active under `(prefers-reduced-motion: no-preference)` (tested in Task 1).

---

### Task 1: Design System Master, Liquid Glass CSS Foundation, Motion Controller & Global Chrome

**Files:**
- Create: `design-system/hotelai-liquid-glass/MASTER.md`
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/styles/liquid-glass.css`
- Create: `frontend/src/components/liquid-motion.tsx`
- Modify: `frontend/src/app/globals.css:1-260`
- Modify: `frontend/src/app/layout.tsx`
- Modify: `frontend/src/components/header.tsx`
- Modify: `frontend/src/components/footer.tsx`
- Modify: `frontend/src/components/chat-widget.tsx`
- Modify: `frontend/src/components/ui-provider.tsx`
- Create: `frontend/e2e/liquid-glass-ux.spec.ts`

**Interfaces:**
- Produces:
  - `LiquidMotionObserver(): JSX.Element | null` in `frontend/src/components/liquid-motion.tsx` — mounts once in `RootLayout`; attaches `pointermove` (`requestAnimationFrame` throttled on `(pointer: fine)`) updating `--mx` / `--my` on `[data-glass-spotlight]` ancestors, and `IntersectionObserver` toggling `data-revealed="true"` on `[data-reveal]`.
  - `commercialMinPrice(prices: Array<number | null | undefined>, minThreshold?: number): number | null` in `frontend/src/components/liquid-motion.tsx` (or shared UI utility) — filters finite numbers `>= (minThreshold ?? 100_000)` and returns the minimum or `null` (never `Infinity` or `NaN`).
  - CSS custom properties in `tokens.css` (`--lg-blur-chrome`, `--lg-glass-light`, `--lg-glass-dark`, `--lg-rim-light`, `--lg-rim-dark`, `--lg-surface-card`, `--lg-accent-emerald`, `--lg-accent-gold`, `--font-display`, `--font-sans`, `--admin-header-height: 68px`) and utility classes `.lg-chrome-light`, `.lg-chrome-dark`, `.lg-card`, `.lg-pill`, `[data-glass-spotlight]`, `[data-reveal]`.

- [ ] **Step 1: Switch port `3000` from static Docker `frontend` container to live `next dev` against Docker Gateway (`8081`)**

Run: `docker compose -f infrastructure/docker-compose.microservices.yml stop frontend` and start `npm run dev` in `frontend/` as a background daemon so `http://localhost:3000` serves live edits against `http://localhost:8081/api/v1`.
Expected: `http://localhost:3000` responds with live Next.js dev server.

- [ ] **Step 2: Write failing Playwright test for Liquid Glass tokens, reduced-motion safety, global chrome & `< 100px` admin offset**

Create `frontend/e2e/liquid-glass-ux.spec.ts` asserting:
1. On `/`, computed `--lg-blur-chrome` is non-empty on `:root`, `.site-header` has `backdrop-filter` containing `blur`, and under `page.emulateMedia({ reducedMotion: "reduce" })` `[data-reveal]` elements remain `opacity: "1"`.
2. Clicking `button[aria-label*="Hỏi trợ lý AI"]` opens `.chat-panel` with placeholder `"Tôi cần phòng cho 2 người..."` and button `"Gửi"`.
3. Logging into `/admin` via `openAdminUiWithToken` renders `.admin-workspace` with `boundingBox().y < 100`.

- [ ] **Step 3: Run Playwright test to verify it fails before implementation**

Run: `cd frontend && npx playwright test e2e/liquid-glass-ux.spec.ts`
Expected: FAIL (`--lg-blur-chrome` is empty on `:root`).

- [ ] **Step 4: Author `design-system/hotelai-liquid-glass/MASTER.md`, `tokens.css`, `liquid-glass.css`, and `liquid-motion.tsx`**

- Document the 3-layer Adaptive Dual-Surface Liquid Glass tokens, typography (`Playfair Display` + `Plus Jakarta Sans` / `Be Vietnam Pro`), contrast floors, and anti-patterns in `design-system/hotelai-liquid-glass/MASTER.md`.
- Implement `frontend/src/styles/tokens.css` and `frontend/src/styles/liquid-glass.css` including `@media (prefers-reduced-motion: reduce)` and `@media (prefers-reduced-transparency: reduce)` opaque fallbacks, specular rim borders, toast/dialog glass surfaces, floating capsule header (`height: 68px` when `.admin-header`), luxury dark glass footer, and 2-layer glass `.chat-panel`.
- Implement `LiquidMotionObserver` and `commercialMinPrice` in `frontend/src/components/liquid-motion.tsx`.

- [ ] **Step 5: Wire `globals.css`, `layout.tsx`, `header.tsx`, `footer.tsx`, `chat-widget.tsx`, and `ui-provider.tsx`**

- Import `../styles/tokens.css` and `../styles/liquid-glass.css` at top of `frontend/src/app/globals.css` (removing the old CSS `@import url(...)` font line in favor of `<link>` preconnect + stylesheet in `layout.tsx`).
- Mount `<LiquidMotionObserver />` inside `RootLayout` (`frontend/src/app/layout.tsx`).
- Enhance `Header`, `Footer`, `ChatWidget` (preserving `aria-label="Hỏi trợ lý AI · Hỗ trợ 24/7"`, placeholder `"Tôi cần phòng cho 2 người..."`, `.chat-messages .assistant`), and `UiProvider` (replacing plain text symbols in `.app-toast` / `.app-dialog` with crisp `AppIcon` SVGs while preserving `.app-toast.toast-success`, `.app-toast.toast-error`, `role="dialog"`, and `aria-label="Đóng thông báo"`).

- [ ] **Step 6: Verify typecheck, lint, and Task 1 Playwright suite**

Run: `cd frontend && npm run typecheck && npm run lint && npx playwright test e2e/liquid-glass-ux.spec.ts`
Expected: PASS (`0` TypeScript/ESLint errors, all `liquid-glass-ux.spec.ts` tests green).

---

### Task 2: Homepage Overhaul — Refined Hero, 1-Row Floating Glass Booking Bar, Immediate `#suites` & Bento Destinations

**Files:**
- Create: `frontend/src/styles/guest-portal.css`
- Modify: `frontend/src/app/globals.css` (import `../styles/guest-portal.css`)
- Modify: `frontend/src/app/page.tsx`
- Modify: `frontend/e2e/liquid-glass-ux.spec.ts`

**Interfaces:**
- Consumes: `commercialMinPrice` from `frontend/src/components/liquid-motion.tsx`, Liquid Glass classes/tokens from Task 1.
- Produces: Redesigned `HomePage` (`frontend/src/app/page.tsx`) and `guest-portal.css` shared guest styles.

- [ ] **Step 1: Extend `frontend/e2e/liquid-glass-ux.spec.ts` with failing Homepage UX & Review Focus tests**

Add test cases asserting on `/`:
1. **Section order:** `#suites` (`heading` `"Kết quả theo thời gian thực"`) appears *above* `.vietnam-destinations-section` (`suitesBox.y < destinationsBox.y`).
2. **Commercial starting price filter:** Hero summary and Phú Quốc Bento card never display `"1.000 ₫"`, `"Infinity"`, or `"NaN"`.
3. **Interactive `−/+` steppers & clamping:** Clicking `button[aria-label="Giảm số người lớn"]` when `Người lớn` is `1` keeps value at `"1"`; clicking `button[aria-label="Tăng số người lớn"]` updates value cleanly; setting `Nhận phòng` equal to or after `Trả phòng` auto-advances `Trả phòng` to at least `+1` day.
4. **Responsive & Accessibility:** Across `390x844`, `768x1024`, `1366x768`, `scrollWidth - clientWidth <= 1`, `Nhận phòng`, `Trả phòng`, and `Tìm phòng trống` are visible without opening a popover, and `button:not([aria-label])` with empty text `=== 0`.

- [ ] **Step 2: Run Playwright test to verify new Homepage assertions fail**

Run: `cd frontend && npx playwright test e2e/liquid-glass-ux.spec.ts`
Expected: FAIL (`#suites` is currently below `.vietnam-destinations-section` and `"1.000 ₫"` appears on Hero).

- [ ] **Step 3: Implement `guest-portal.css` and refactor `frontend/src/app/page.tsx`**

- Streamline the Hero: ambient aurora mesh (`overflow: hidden` so zero horizontal bleed), single telemetry pill, `Playfair Display` headline, primary/secondary glass CTAs, destination spotlight pills, and right-hand optical Liquid Glass Concierge card using `commercialMinPrice(options.map((o) => o.totalAmount))`.
- Build the **1-Row Floating Liquid Glass Booking Bar (`#search`)**: compact preset chips on top, unified horizontal glass bar containing `Điểm đến`, `Nhận phòng` (`input[type="date"]`), `Trả phòng` (`input[type="date"]` with automatic `+1` day guard when `checkIn >= checkOut`), inline `Người lớn` / `Trẻ em` / `Số phòng` controls with `−/+` stepper buttons carrying explicit `aria-label`s (`"Giảm số người lớn"`, `"Tăng số người lớn"`, `"Giảm số trẻ em"`, `"Tăng số trẻ em"`, `"Giảm số phòng"`, `"Tăng số phòng"`), `Tìm phòng phù hợp` (`aria-label="Tìm phòng trống"`), and sleek collapsible `Bộ lọc Đặc quyền & Cá nhân hóa`.
- Reorder sections to: `Hero` → `#search` → `#suites` (shimmering AI Bespoke Concierge bar + Bento/Horizontal `.room-card`s with `[data-glass-spotlight]`, `[data-reveal]`, max 3 visual badges, `.ai-score` keeping `sr-only` `"AI phù hợp XX%"`) → compact 4-item `.guest-trust-strip` → `.vietnam-destinations-section` (segmented glass region tabs, Bento cards using `commercialMinPrice` for `"Từ ... / đêm"` and scrolling smoothly up to `#suites` on click) → `.experience-section` → `.guest-services-section` → `.luxury-testimonials` → `.public-cta` → Liquid Glass `.booking-modal`.

- [ ] **Step 4: Verify typecheck, lint, and Playwright Homepage + UC06–UC08/UC34–UC35 tests**

Run: `cd frontend && npm run typecheck && npm run lint && npx playwright test e2e/liquid-glass-ux.spec.ts e2e/uc01-uc40.spec.ts -g "UC06"`
Expected: PASS.

---

### Task 3: Secondary Guest Pages — `/rooms`, `/bookings`, `/login` & `/profile`

**Files:**
- Modify: `frontend/src/app/rooms/page.tsx`
- Modify: `frontend/src/app/bookings/page.tsx`
- Modify: `frontend/src/app/login/page.tsx`
- Modify: `frontend/src/app/profile/page.tsx`
- Modify: `frontend/src/styles/guest-portal.css`
- Modify: `frontend/e2e/liquid-glass-ux.spec.ts`

**Interfaces:**
- Consumes: `commercialMinPrice` from `frontend/src/components/liquid-motion.tsx`, `guest-portal.css` & `liquid-glass.css`.
- Produces: Upgraded `/rooms`, `/bookings`, `/login`, and `/profile` pages with cohesive Liquid Glass surfaces and improved usability.

- [ ] **Step 1: Add failing Playwright test in `frontend/e2e/liquid-glass-ux.spec.ts` for `/rooms` interactive filter & `/login` / `/profile` / `/bookings` accessibility**

Assert:
1. `/rooms` renders interactive region/availability glass filter pills (`"Tất cả"`, `"Còn phòng trống"`, `"Miền Bắc"`, `"Miền Trung"`, `"Miền Nam"`), filters `.catalog-card` items without horizontal overflow, and has `0` unnamed buttons.
2. `/login` preserves all auth state transitions (`Chào mừng trở lại` ↔ `Tạo tài khoản` ↔ `Khôi phục mật khẩu`) with `0` unnamed buttons and `scrollWidth - clientWidth <= 1` on `390x844`.

- [ ] **Step 2: Run Playwright test to verify `/rooms` filter test fails**

Run: `cd frontend && npx playwright test e2e/liquid-glass-ux.spec.ts -g "/rooms"`
Expected: FAIL (`"Còn phòng trống"` filter pill not found).

- [ ] **Step 3: Upgrade `/rooms`, `/bookings`, `/login`, and `/profile` JSX & `guest-portal.css`**

- `rooms/page.tsx`: Add Liquid Glass hero summary + instant client-side filter bar (`Tất cả`, `Còn phòng trống`, `Miền Bắc`, `Miền Trung`, `Miền Nam` + search input by room/destination name), spotlight glass suite cards, and direct `"Kiểm tra & Đặt phòng"` link.
- `bookings/page.tsx`: Style `.booking-list article` as layered Liquid Glass cards with clear status pills, a premium dark-emerald/champagne bank-card VietQR transfer region (`aria-label="Thông tin chuyển khoản"`, `alt="Mã VietQR thanh toán đặt phòng"`, `.payment-code-callout strong`), inline date-reschedule glass drawer, and interactive star/topic review composer.
- `login/page.tsx`: Split-screen Liquid Glass Atelier (dark aurora editorial story panel on left + luminous frosted glass form card on right, preserving every exact heading, button text, and input label for login, registration, OTP verification, and password reset).
- `profile/page.tsx`: Two-column frosted glass layout for contact profile + password security, plus a clean glass notification feed below.

- [ ] **Step 4: Verify typecheck, lint, and Playwright auth & guest specs**

Run: `cd frontend && npm run typecheck && npm run lint && npx playwright test e2e/liquid-glass-ux.spec.ts e2e/uc01-uc40.spec.ts -g "UC01"`
Expected: PASS.

---

### Task 4: 9-Module `/admin` Frosted Pearl & Deep Ocean Cockpit

**Files:**
- Create: `frontend/src/styles/admin-cockpit.css`
- Modify: `frontend/src/app/globals.css` (import `../styles/admin-cockpit.css`)
- Modify: `frontend/src/app/admin/page.tsx`
- Modify: `frontend/src/components/admin-overview.tsx`
- Modify: `frontend/src/components/admin-front-desk.tsx`
- Modify: `frontend/src/components/admin-ai-operations.tsx`
- Modify: `frontend/src/components/admin-management.tsx`
- Modify: `frontend/src/components/admin-operations.tsx`
- Modify: `frontend/src/components/admin-customers.tsx`
- Modify: `frontend/src/components/admin-access-control.tsx`
- Modify: `frontend/src/components/admin-policies.tsx`
- Modify: `frontend/src/components/admin-stay-actions.tsx`
- Modify: `frontend/e2e/liquid-glass-ux.spec.ts`

**Interfaces:**
- Consumes: `tokens.css`, `liquid-glass.css`, `AppIcon`.
- Produces: Unified Frosted Pearl & Deep Ocean Cockpit across `/admin` and all 9 operational modules while preserving all E2E selectors and `.admin-workspace` top `y < 100px`.

- [ ] **Step 1: Add failing Playwright test in `frontend/e2e/liquid-glass-ux.spec.ts` for `/admin` Cockpit layout, mobile overflow & contrast/buttons**

Assert with `openAdminUiWithToken`:
1. `.admin-sidebar` and `.workspace-topbar` render with glass `backdrop-filter` blur, `.admin-workspace` has `boundingBox().y < 100` on desktop (`1366x768`) and mobile (`390x844`).
2. Navigating across `Tổng quan`, `Bàn lễ tân`, `Kho phòng & giá`, `Trung tâm AI` produces `scrollWidth - clientWidth <= 1` on `390x844` and `0` unnamed buttons.

- [ ] **Step 2: Run Playwright test to verify new `/admin` assertions**

Run: `cd frontend && npx playwright test e2e/liquid-glass-ux.spec.ts -g "/admin"`
Expected: FAIL on `.workspace-topbar` glass blur or mobile table overflow.

- [ ] **Step 3: Implement `admin-cockpit.css` and polish `admin/page.tsx` + `admin-*.tsx` components**

- `admin-cockpit.css` & `admin/page.tsx`: Cool Frosted Mist ambient canvas, floating frosted `.admin-sidebar` with glowing active tab pill, sticky `.workspace-topbar` glass bar, crisp high-contrast `.panel` cards (`>= 4.5:1`), semantic status pills, responsive `.table-scroll` wrappers around wide tables so mobile viewports never overflow horizontally, and refined `.checkin-modal` glass overlay.
- Enhance visual hierarchy in `AdminOverview` (KPI metric cards with accent top bars, clean forecast & daily revenue bar visualizations, risk badges), `AdminFrontDesk` & `AdminStayActions` (glass `.walkin-panel`, clear action clusters, preserving `.walkin-panel summary`, `.checkin-modal`, `.guest-form`, `.checkin-room-grid select`, `button[aria-label="Đóng"]`), `AdminAiOperations` (human-in-the-loop price proposal cards + staff RAG assistant glass chat), and ensure consistent panel/table/form ergonomics across `AdminManagement` (preserving `<section>` ancestor of `<h2>Giá theo ngày</h2>` and `+ Tạo loại phòng hoặc tiện nghi`), `AdminOperations`, `AdminCustomers`, `AdminAccessControl`, and `AdminPolicies`.

- [ ] **Step 4: Verify typecheck, lint, and full Admin Playwright specs**

Run: `cd frontend && npm run typecheck && npm run lint && npx playwright test e2e/liquid-glass-ux.spec.ts e2e/professional/admin-ai-quality.spec.ts`
Expected: PASS.

---

### Task 5: CSS Consolidation, Docker `frontend` Container Rebuild & End-to-End Live Verification

**Files:**
- Modify: `frontend/src/app/globals.css`
- Verify: `infrastructure/docker-compose.microservices.yml` (`frontend` service)

**Interfaces:**
- Consumes: All styles and components from Tasks 1–4.
- Produces: Clean production Next.js standalone build running inside `hotel-ai-microservices-frontend-1` on `http://localhost:3000` with all E2E tests passing.

- [ ] **Step 1: Audit `globals.css` and verify standalone production build**

Remove duplicated base/override rules in `frontend/src/app/globals.css` that were superseded by `tokens.css`, `liquid-glass.css`, `guest-portal.css`, and `admin-cockpit.css` while keeping every active class selector intact.
Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: Clean Next.js 16 production build with `0` errors.

- [ ] **Step 2: Stop temporary `next dev` and rebuild + start Docker `frontend` container**

Stop the background `next dev` task, then run:
`docker compose -f infrastructure/docker-compose.microservices.yml up -d --build --no-deps frontend`
Expected: `hotel-ai-microservices-frontend-1` builds cleanly via `frontend/Dockerfile` and runs on `http://localhost:3000`.

- [ ] **Step 3: Execute full Playwright verification against live Dockerized stack (`http://localhost:3000` + `http://localhost:8081`)**

Run: `cd frontend && npx playwright test e2e/liquid-glass-ux.spec.ts e2e/uc01-uc40.spec.ts e2e/professional/admin-ai-quality.spec.ts`
Expected: 100% PASS across all tests.

- [ ] **Step 4: Visual inspection in built-in Browser on `http://localhost:3000` and `http://localhost:3000/admin`**

Verify live rendering of the Guest Portal (`/`, `/rooms`, `/login`) and Admin Cockpit (`/admin`) in the browser tab so the user sees the finished Liquid Glass UI immediately.
