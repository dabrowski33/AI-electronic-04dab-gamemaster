# NBP Design Guidelines

Design system extracted from the official **Narodowy Bank Polski** website ([nbp.pl](https://nbp.pl/)) on **2026-06-24**. Use these tokens and assets to build UIs that stay visually consistent with the NBP brand.

> Source of truth for machine-readable values: [`../assets/design-tokens.json`](../assets/design-tokens.json)

---

## 1. Assets

| Asset | File | Usage |
|---|---|---|
| Homepage screenshot | [`../assets/homepage.png`](../assets/homepage.png) | Visual reference for layout & composition |
| Logo (wordmark) | [`../assets/logo.svg`](../assets/logo.svg) | Primary NBP logo — gold emblem + "NBP Narodowy Bank Polski" wordmark |
| Favicon | [`../assets/favicon.ico`](../assets/favicon.ico) | Browser tab / bookmark icon |
| Design tokens | [`../assets/design-tokens.json`](../assets/design-tokens.json) | Structured tokens for code consumption |
| Fonts (self-hosted) | [`../assets/fonts/`](../assets/fonts/) | The two NBP brand typefaces, downloaded as TTF — see §3 |

---

## 2. Colors

The palette is built around a **deep institutional navy**, a **trust-blue accent** for interaction, and a **gold** drawn from the bank's emblem.

### Brand

| Token | Hex | Usage |
|---|---|---|
| NBP Navy (primary) | `#152E52` | Page header background, all headings, dark sections, primary brand color |
| NBP Blue (accent) | `#4A74B0` | Links, primary buttons, interactive accents |
| Logo Gold | `#BDAD7D` | The emblem/logo color; sparingly for decorative or prestige accents |
| Sand / Highlight | `#E8D499` | Soft highlight backgrounds, featured/promo strips |

### Backgrounds

| Token | Hex | Usage |
|---|---|---|
| Default | `#FFFFFF` | Main page background |
| Light | `#F7F7F7` | Section/card backgrounds, subtle separation |
| Dark | `#152E52` | Header and dark inverted sections |
| Muted | `#C4C4C4` | Placeholder/disabled surfaces |
| Overlay | `rgba(21, 46, 82, 0.6)` | Image overlays, modals |

### Text

| Token | Hex | Usage |
|---|---|---|
| Primary | `#464646` | Body copy |
| Heading | `#152E52` | All headings (h1–h3) |
| Secondary | `#333333` | Slightly stronger body text |
| Muted | `#717171` | Captions, metadata, dates |
| Link | `#4A74B0` | Hyperlinks |
| On Dark | `#FFFFFF` | Text on navy/dark backgrounds |

### Borders

| Token | Hex | Usage |
|---|---|---|
| Default | `#BFCEDD` | Input & control borders (cool blue-grey) |
| Muted | `#C4C4C4` | Dividers, neutral borders |

### Status

| Token | Hex | Usage |
|---|---|---|
| Error | `#C0392B` | Validation errors, destructive actions |
| Success | `#2E7D32` | Confirmations, positive states |

> Status colors are not strongly exposed on the public homepage and are sensible institutional defaults — adjust if the group defines official values.

---

## 3. Typography

Two custom typefaces, self-hosted as TrueType (`font-display: swap`):

- **Brygada 1918** — a refined serif used for **headings**. A Polish-designed typeface, fitting for a national institution. Weights 400/500/600/700 (regular + italic each).
- **Libre Franklin** — a humanist sans-serif used for **body, navigation, UI**. Full weight range 100–900.

```css
/* Headings */
font-family: "Brygada 1918", Georgia, "Times New Roman", serif;

/* Body / UI */
font-family: "Libre Franklin", -apple-system, Arial, "Noto Sans", sans-serif;
```

If the custom fonts are unavailable, the fallbacks above keep the serif/sans distinction.

### Downloaded font files (available in this repo)

Both families are committed under [`../assets/fonts/`](../assets/fonts/) as TrueType and can be used directly in the app — no external download needed at build time.

| Family | Path | Files |
|---|---|---|
| Brygada 1918 (serif, headings) | `assets/fonts/brygada-1918/` | `Brygada1918-Regular.ttf`, `-Italic`, `-Medium`, `-MediumItalic`, `-SemiBold`, `-SemiBoldItalic`, `-Bold`, `-BoldItalic` (8 files) |
| Libre Franklin (sans, body/UI) | `assets/fonts/libre-franklin/` | `LibreFranklin-Thin.ttf` → `-Black.ttf` across weights 100–900 + matching italics (18 files) |

Example `@font-face` wiring against the local files:

```css
@font-face {
  font-family: "Brygada 1918";
  font-weight: 500;
  font-style: normal;
  font-display: swap;
  src: url("/assets/fonts/brygada-1918/Brygada1918-Medium.ttf") format("truetype");
}
@font-face {
  font-family: "Libre Franklin";
  font-weight: 400;
  font-style: normal;
  font-display: swap;
  src: url("/assets/fonts/libre-franklin/LibreFranklin-Regular.ttf") format("truetype");
}
```

> ⚖️ **License:** these are NBP's self-hosted font files, downloaded as-is from nbp.pl. **Verify the license of each typeface before using or redistributing it in our application.** (Brygada 1918 and Libre Franklin are both published as open-source SIL Open Font License families on Google Fonts, but confirm the specific version/terms before shipping.)

### Weights

| Name | Value |
|---|---|
| Regular | 400 |
| Medium | 500 (default for headings) |
| Semibold | 600 |
| Bold | 700 |

### Size scale

| Token | Size | Usage |
|---|---|---|
| xs | 12px | Fine print, tags |
| sm | 13px | Buttons, metadata, dates |
| base | 16px | Body copy (site renders ~15.5px) |
| md | 18px | Lead paragraphs |
| lg | 20px | Sub-headings |
| xl | 24px | h1 / h2 (site default heading size) |
| 2xl | 30px | Large hero headings |
| 3xl | 38px | Display |

### Line height

| Token | Value | Usage |
|---|---|---|
| tight | 1.25 | Headings (24px → 32px line) |
| base | 1.5 | Body (16px → 24px line) |
| relaxed | 1.6 | Long-form reading |

---

## 4. Spacing

Base unit **4px**, linear scale.

| Token | Value |
|---|---|
| 1 | 4px |
| 2 | 8px |
| 3 | 12px |
| 4 | 16px |
| 5 | 20px |
| 6 | 24px |
| 7 | 28px |
| 8 | 32px |
| 10 | 40px |
| 12 | 48px |

---

## 5. Border Radius

The brand reads as **conservative and squared-off** — radii are small.

| Token | Value | Usage |
|---|---|---|
| none | 0px | Default for most blocks |
| xs | 2px | Subtle softening |
| sm | 4px | Buttons, badges |
| md | 6px | Inputs, search fields, grouped controls |
| full | 999px | Pills |
| circle | 50% | Avatars, icon buttons, carousel dots |

---

## 6. Components

### Header
Full-width bar on **NBP Navy** (`#152E52`) with the gold logo on the left and white nav controls (language switch `EN`, high-contrast toggle, menu) on the right.

```css
background: #152E52;
color: #FFFFFF;
```

### Navigation
Sans-serif (Libre Franklin), 16px, weight 400, text color `#464646` on white in the dropdown/menu panel; white when over the navy header.

### Primary button ("Więcej" / More)
```css
background: #4A74B0;
color: #FFFFFF;
border: 1px solid #4A74B0;
padding: 6px 12px;
border-radius: 4px;
font-size: 13px;
font-weight: 500;
text-transform: uppercase;
```

### Secondary / outline control (e.g. search trigger)
```css
background: #FFFFFF;
color: #464646;
border: 1px solid #BFCEDD;
padding: 10px;
border-radius: 6px;
```

### Inputs
```css
background: #FFFFFF;
color: #464646;
border: 1px solid #BFCEDD;
border-radius: 6px;
padding: 10px;
font-size: 16px;
```

### Headings
```css
font-family: "Brygada 1918", Georgia, serif;
color: #152E52;
font-weight: 500;
font-size: 24px;
line-height: 32px;
```

### Cards (news / "Aktualności")
White card, light separation, a date stamp (`time`) in muted text on the left, serif/sans heading link, and a small category tag link. Generous vertical rhythm.

---

## 7. Logo Usage

- The logo ([`../assets/logo.svg`](../assets/logo.svg)) is a single-color (**gold `#BDAD7D`**) emblem + wordmark, sized natively at `205×64`.
- On the site it sits on the **navy header** — pair the gold logo with dark navy backgrounds for the canonical look.
- On light backgrounds, the gold remains legible; for stronger contrast you may recolor the SVG `fill` to navy `#152E52`.
- Preserve clear space around the wordmark (at least the height of the emblem on all sides) and never stretch or recolor it to off-brand hues.

---

## 8. Visual Style Summary

NBP's brand is **institutional, authoritative, and quietly elegant**. A deep navy and gold convey trust and national heritage, while the serif headings (Brygada 1918, a Polish typeface) lend gravitas that the clean Libre Franklin body text keeps readable and modern. Layouts are spacious, restrained, and content-first, with small radii and minimal decoration — nothing playful or flashy. The result feels like a serious public financial institution: stable, credible, and accessible (note the built-in high-contrast toggle and "skip to content" link).
