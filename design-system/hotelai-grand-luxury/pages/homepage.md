# Homepage Page Overrides — HotelAI Grand Luxury Edition

> **PROJECT:** HotelAI Grand Luxury
> **Updated:** 2026-11-02
> **Page Type:** 5-Star Hospitality Flagship Landing & Real-Time Booking Atelier
> **Style:** Liquid Glass + Editorial Quiet Luxury (Aman / Four Seasons / Capella inspired)

---

## Page-Specific Rules

### Color Palette Overrides (Imperial Emerald & Champagne Gold)

| Role | Hex | CSS Variable | Notes |
|------|-----|--------------|-------|
| Obsidian Emerald | `#041512` | `--ink-950` | Deep architectural backdrop |
| Imperial Pine | `#07211C` | `--ink-900` | Primary dark luxury surface |
| Sovereign Jade | `#0C6957` | `--emerald` | Brand emerald accent |
| Champagne Gold | `#D4A852` | `--gold` | Primary metallic luxury accent |
| Luminous Gold | `#F2D28B` | `--gold-bright` | Specular highlights & borders |
| Warm Silk Ivory | `#F8F5EE` | `--ivory` | Warm editorial canvas |
| Alabaster Paper | `#FFFEFA` | `--paper` | Elevated card surface |

### Typography Overrides

- **Display / Editorial Headings:** `'Cormorant Garamond'`, `'Playfair Display'`, serif (Vietnamese-complete, high-contrast editorial strokes)
- **Body / UI Controls:** `'Be Vietnam Pro'`, `'Plus Jakarta Sans'`, sans-serif
- **Telemetry / Coordinates / Prices:** Tabular numerals (`font-variant-numeric: tabular-nums`)

### Layout & Section Architecture

1. **Prestige Top Bar & Header:** Sticky liquid-glass header with brand crest, navigation pills, and live concierge indicator.
2. **Architectural Hero Stage (`.hero`):**
   - Interactive Sanctuary Spotlight Switcher (4 iconic Vietnamese retreats with GPS coordinates & live ambiance).
   - Editorial headline with italic gold serif accents.
   - Liquid-Glass Private Concierge Dossier card (`.hero-note`) with live room telemetry and minimum nightly rate.
3. **Floating Liquid-Glass Booking Atelier (`#search`):**
   - Overlapping hero bottom edge (`margin-top: -115px`), double gold hairline border, automatic nights counter badge, 4 curated stay presets, 6 inline search inputs + gold/emerald CTA, and expandable luxury filter drawer.
4. **Haute Hospitality Trust & Accolades Strip (`.guest-trust-strip`):**
   - 4 pillars of direct-booking assurance with custom SVG medallions.
5. **10 Iconic Vietnam Destinations Bento Gallery (`.vietnam-destinations-section`):**
   - Regional filter tabs (`Toàn quốc`, `Miền Bắc`, `Miền Trung`, `Miền Nam`) + Bento grid with architectural coordinates, live room counts, and 1-click suite filtering.
6. **Real-Time Suites & Villas Collection (`#suites`):**
   - AI Bespoke Concierge bar (`recommend-bar`) + 2-column (3-column on ultra-wide) luxury room cards with suite specifications, amenity badges, AI match score, and transparent total stay pricing.
7. **Sanctuary Experience & "A Day at HotelAI" Editorial Showcase (`.experience-section`):**
   - Interactive architectural tabs + time-of-day luxury rhythm (`Bình Minh`, `Trị Liệu`, `Hoàng Hôn`, `Đêm Tinh Hoa`).
8. **Private Butler Services & Pre-Arrival Atelier (`.guest-services-section`):**
   - 3 service pillars + Roman-numeral numbered pre-arrival add-on cards with active selection feedback.
9. **Verified Guest Chronicles & Prestige Awards (`.luxury-testimonials`):**
   - International hospitality awards strip + 3 editorial guest review cards.
10. **Grand Finale CTA Banner (`.public-cta`) & Bespoke Reservation Modal (`.booking-modal`).**

### Pre-Delivery Verification Checklist

- [x] No emojis used as icons (100% custom SVG via `AppIcon`)
- [x] `cursor: pointer` on all interactive cards, tabs, presets, and buttons
- [x] Smooth transitions (180ms–350ms cubic-bezier) without layout shift
- [x] High contrast text in both light alabaster sections and dark obsidian-emerald sections (WCAG AA+)
- [x] Visible `:focus-visible` gold/emerald rings for keyboard navigation
- [x] `prefers-reduced-motion` media query respected
- [x] Responsive across 375px, 768px, 1024px, 1440px+
