# AGENTS.md — Frontend (`app/frontend/`)

> Scope: Angular SPA. Complements the root `AGENTS.md`. Visual/brand fidelity is a
> first-class acceptance criterion here, not an afterthought.

## NBP brand fidelity is REQUIRED (not optional polish)

Source of truth: `docs/design-guidelines.md` + `assets/design-tokens.json`, with
`assets/homepage.png` as the visual reference. **Every screen must be compared against
`homepage.png` and the guidelines before it is considered done.** A bare Angular-Material
default look is a failing result.

Concrete musts:
- **NBP navy header bar** (`#152E52`) with the **gold logo** (`assets/logo.svg`) — on **both** the intake and chat screens. Currently missing — must be added.
- Palette from `design-tokens.json`: NBP Navy `#152E52` (primary), NBP Blue `#4A74B0` (accent/links/buttons), Logo Gold `#BDAD7D` (sparingly), Sand `#E8D499`. Status: error `#C0392B`, success `#2E7D32`. Pull these from the tokens — don't invent ad-hoc colors (the bright-green decision badge was off-brand).
- Fonts: **Brygada 1918** (serif) for headings, **Libre Franklin** (sans) for body/UI — already wired from `assets/fonts/`, keep it.
- Small radii (0–6px), generous spacing (4px base scale), restrained/institutional feel.

## Icons MUST render — no broken glyphs, ever

`<mat-icon>` requires an icon font that is currently **not loaded**, so `upload`/`send`/`image`
render as literal broken text. Before shipping any `<mat-icon>`:
- Load the **Material Symbols/Icons** font (self-host under `assets/` — this is an NBP/possibly
  air-gapped context, so prefer a local copy over a CDN), **or** use inline SVG icons.
- Verify in a real browser (and via Playwright MCP screenshot) that **no glyph shows as text**.

## Page shell hygiene
- `index.html`: set a real Polish `<title>` (e.g. "Asystent reklamacji i zwrotów — NBP"), `lang="pl"`, and the NBP favicon (`assets/favicon.ico`).
- All user-facing text in **Polish** (AC-09/25), including titles, aria-labels, errors, helper text.

## LLM: the frontend NEVER touches it
- No API keys, no provider URLs, no model names in the FE. The FE only calls `/api/...` (TAC-003-10 / TAC-09). The dev proxy (`proxy.conf.json`) forwards `/api` → `:8080`.

## Testing
- Unit (`*.spec.ts`): all deps mocked. Cover validators, SSE parser (incl. split frame + `error` + `done`, TAC-003-06), API error normalization, intake validation, chat streaming render, markdown + disclaimer always visible, decision-badge per category + unknown-category guard.
- The FE never hits a live LLM in its own tests — it uses fixtures/mocks. End-to-end behavior is verified in `app/e2e` against the **real** stack.

## Verification (before every commit)
```bash
npm test -- --watch=false --browsers=ChromeHeadless   # green
npm run lint                                            # no errors
npm run build                                           # succeeds
```
Then **open the app in a browser / Playwright MCP** and compare against `homepage.png` —
passing unit tests ≠ a correct, on-brand UI.

## Conventions
- Standalone components, signal-based state, OnPush-friendly (ADR-003).
- Folders: `core` (api, models), `features/intake`, `features/chat`, `shared` (validators).
- SSE consumed via `fetch()` + `ReadableStream` + `TextDecoder` (not `EventSource`).
- Object URLs for image previews revoked on destroy/replace (no leaks, TAC-003-04).
