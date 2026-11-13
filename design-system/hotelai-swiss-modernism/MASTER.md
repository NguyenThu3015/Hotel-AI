# Design System Master File — HotelAI Vietnam (Swiss Editorial Luxury)

> **LOGIC:** When building or updating any page, check `design-system/hotelai-swiss-modernism/pages/[page-name].md` first. If none exists, follow this `MASTER.md` strictly.

---

## Project Identity
- **Project:** HotelAI Vietnam — Curated Luxury Resort & Boutique Hotel Collection (10 Vietnam Destinations · 3 Regions)
- **Aesthetic Direction:** **Swiss Editorial Luxury (Quiet Luxury + International Typographic Precision)** — inspired by *Aman Resorts*, *The Chedi Andermatt*, *7132 Hotel Vals*, and *Cereal / Kinfolk Magazine*.
- **Core Philosophy:**
  1. **Refined Editorial Typography:** High-contrast luxury serif (`Playfair Display` / `Cormorant Garamond`) paired with clean geometric sans-serif (`Plus Jakarta Sans` / `Inter`) — full Vietnamese diacritic elegance, graceful italic contrasts, and generous breathing room.
  2. **High-Contrast Chiaroscuro Rhythm:** Alternating luminous **Warm Gallery Alabaster (`#FAF8F5`)** sections with deep, atmospheric **Obsidian Charcoal (`#121110`)** architectural sections — creating dramatic contrast without harsh brutalist boxes or loud neon colors.
  3. **Whisper-Thin Swiss Grid Hairlines:** Delicate `1px solid rgba(18, 17, 16, 0.1)` (light) and `1px solid rgba(250, 248, 245, 0.14)` (dark) structural dividers, subtle `6px`–`12px` architectural corners, and soft diffused elevation (`0 24px 64px rgba(18, 17, 16, 0.08)`).

---

## Color Palette (Obsidian, Warm Alabaster & Champagne Bronze)

| Token | Hex | Role & Usage |
| :--- | :--- | :--- |
| `--lux-alabaster` | `#FAF8F5` | Primary warm gallery limestone background |
| `--lux-white` | `#FFFFFF` | Pure silk white surface for cards, booking bar, and modals |
| `--lux-sand` | `#F1ECE3` | Secondary warm travertine surface for subtle contrast cells |
| `--lux-obsidian` | `#121110` | Deep architectural charcoal/black for primary typography, primary CTAs, and inverted dark sections |
| `--lux-espresso` | `#1C1A18` | Elevated dark card surface inside obsidian sections |
| `--lux-bronze` | `#B08455` | **Warm Champagne Bronze / Terracotta** — refined luxury accent for kickers, active indicators, stars, and hover highlights |
| `--lux-bronze-light` | `#D4AF83` | Soft champagne gold for dark-mode accents and subtle borders |
| `--lux-bronze-soft` | `#F7F1E8` | Soft warm tint for badges and tags on light surfaces |
| `--lux-text` | `#121110` | Primary editorial ink on light surfaces (16.5:1 contrast) |
| `--lux-muted` | `#5C5750` | Secondary warm stone text on light surfaces |
| `--lux-muted-dark` | `#A8A29A` | Secondary text on obsidian dark surfaces |
| `--lux-line` | `rgba(18, 17, 16, 0.1)` | Whisper-thin Swiss architectural hairline on light surfaces |
| `--lux-line-dark` | `rgba(250, 248, 245, 0.14)` | Whisper-thin Swiss hairline on dark surfaces |

---

## Typography

- **Editorial Display (`--font-display`):** `'Playfair Display'`, `'Cormorant Garamond'`, `Georgia`, `serif` — weight `400`–`600`, line-height `1.04`, tracking `-0.025em`, with expressive italic phrases.
- **Swiss Geometric Sans (`--font-sans`):** `'Plus Jakarta Sans'`, `'Inter'`, `'Be Vietnam Pro'`, `sans-serif` — weight `400`–`700` for clean scannability, uppercase micro-labels (`11px`, `letter-spacing: 0.18em`), and tabular numeric pricing.
