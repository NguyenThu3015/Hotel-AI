# Design System Master — HotelAI Adaptive Dual-Surface Liquid Glass

**Project:** HotelAI Vietnam — Luxury Resort Booking & AI Operations Platform  
**Aesthetic Direction:** Adaptive Dual-Surface Liquid Glass (Optical Translucency + High-Contrast Operational Clarity)  
**Updated:** 2026-11-27

---

## 1. Three-Layer Spatial Depth Architecture

1. **Layer 0 — Ambient Aurora Canvas**
   - **Guest Hero & Showcase Zones:** Deep Lagoon Midnight (`#041916` → `#072923`) infused with slow-drifting emerald-cyan (`rgba(20, 184, 152, 0.24)`) and champagne-gold (`rgba(232, 194, 122, 0.18)`) atmospheric gradients.
   - **Guest Content Zones:** Luminous Pearl (`#F3F7F6` → `#FAF8F3`) with subtle warm/cool ambient radial light.
   - **Admin Operations Cockpit (`/admin`):** Cool Frosted Mist (`#EDF3F2` → `#F7FAFA`), engineered for multi-hour eye comfort.

2. **Layer 1 — Optical Liquid Glass Chrome (Navigation, Floating Booking Bar, Modals, Chat, Admin Sidebar/Topbar)**
   - **Light Optical Glass (`.lg-chrome-light`):**
     - Fill: `linear-gradient(135deg, rgba(255, 255, 255, 0.80) 0%, rgba(255, 255, 255, 0.56) 100%)`
     - Filter: `backdrop-filter: blur(24px) saturate(180%)`
     - Specular Rim: `1px solid rgba(255, 255, 255, 0.76)` + `inset 0 1px 0 rgba(255, 255, 255, 0.94)`
     - Shadow: `0 20px 52px rgba(5, 27, 23, 0.10), 0 4px 14px rgba(5, 27, 23, 0.05)`
   - **Dark Optical Glass (`.lg-chrome-dark`):**
     - Fill: `linear-gradient(145deg, rgba(10, 40, 34, 0.68) 0%, rgba(4, 21, 18, 0.48) 100%)`
     - Filter: `backdrop-filter: blur(24px) saturate(170%)`
     - Specular Rim: `1px solid rgba(255, 255, 255, 0.20)` + `inset 0 1px 0 rgba(255, 255, 255, 0.30)`

3. **Layer 2 — Crisp Content & Data Surfaces (`.lg-card`, Tables, Forms)**
   - High-opacity frosted white (`rgba(255, 255, 255, 0.92)`–`0.97`) ensuring WCAG AA `>= 4.5:1` text contrast across every room card, invoice, and booking row.

---

## 2. Color & Typography Tokens

| Token | Value | Role |
| :--- | :--- | :--- |
| `--ink-950` | `#031411` | Deepest lagoon obsidian |
| `--ink-900` | `#07201B` | Primary body ink on light glass (`15.4:1` contrast) |
| `--ink-600` | `#395851` | Secondary readable copy (`6.8:1` contrast) |
| `--emerald` | `#0A6E5C` | Primary interactive emerald |
| `--emerald-bright` | `#12967D` | Hover & active luminous teal |
| `--gold` | `#C8963E` | Champagne luxury accent |
| `--gold-bright` | `#EDD08D` | Specular gold highlight on dark glass |
| `--font-display` | `'Playfair Display', Georgia, serif` | Editorial headings (full Vietnamese diacritics) |
| `--font-sans` | `'Plus Jakarta Sans', 'Be Vietnam Pro', system-ui, sans-serif` | UI controls, forms, and `tabular-nums` pricing/KPIs |

---

## 3. Motion & Accessibility Rules

- **Pointer Lensing (`[data-glass-spotlight]`):** `requestAnimationFrame`-throttled radial specular sheen following `--mx` / `--my` on fine pointers.
- **Scroll Choreography (`[data-reveal]`):** Defaults to `opacity: 1` for SSR/no-JS safety; smoothly reveals (`translateY(14px)` → `0`, `420ms cubic-bezier(0.22, 1, 0.36, 1)`) only when `html.lg-motion-ready` is active and `prefers-reduced-motion: no-preference`.
- **Reduced Motion & Reduced Transparency:** Honored unconditionally via `@media (prefers-reduced-motion: reduce)` and `@media (prefers-reduced-transparency: reduce)`.
- **Zero Unnamed Buttons & `< 100px` Admin Offset:** Every icon button carries a Vietnamese `aria-label`; `/admin` header height is locked at `68px`.
