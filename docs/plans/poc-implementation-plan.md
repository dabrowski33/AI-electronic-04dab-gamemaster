# PoC Implementation Plan — Hardware Service Decision Copilot

## Context

`app/` is currently an **empty scaffold** — no backend or frontend code exists. Everything around it is ready: the PRD, four ADRs, the two Polish policy documents (`docs/policies/`), the NBP design tokens + fonts + logo (`assets/`), and `.env.example`. The goal of this plan is to deliver a **fully working proof of concept** of the Hardware Service Decision Copilot — a Spring Boot + Angular app where a customer submits an electronics return/complaint with one photo and receives a preliminary, advisory LLM decision, then chats with the agent.

This is an **orchestration plan**. The orchestrator (me) does **not** write code. All implementation is delegated to three pre-contextualized subagents — `be-developer`, `fe-developer`, `qa-engineer` — each of which already knows the full stack, TDD rules, verification commands, and required ADR reading. Task prompts therefore carry only **task-specific acceptance criteria + cross-agent contracts**, never restated stack rules.

The intended outcome: full coverage of all PRD acceptance criteria, built test-first, committed in small focused steps, with backend and frontend progressing in parallel through isolated git worktrees and re-joining for end-to-end QA.

## Locked decisions (from clarification)

| Decision | Choice |
|---|---|
| LLM access | Live OpenRouter key available, **free to use**; models taken **verbatim** from env vars (no pre-flight check) |
| Scope | **Full coverage** — both scenarios, all 4 decision categories, all validation/error paths, retry, streaming chat, off-topic |
| Execution | **Parallel in isolated git worktrees** after a shared contract step; merge per milestone |
| Angular version | **Pin to Angular 18** (Material + ngx-markdown pinned to the same major) |
| Git | Commit each step onto current branch `electronic-complains-chat-app`; worktrees branch off it and merge back; **no push** |
| TDD | Test-first, confirm red, implement minimum, verify green, refactor green |

> **Authority & conflicts:** the root `AGENTS.md` (and nested `app/*/AGENTS.md`) is the
> binding source of truth. If an ADR or this plan ever contradicts it, **stop and flag the
> conflict** — do not silently follow the weaker rule. Known reconciliation: **E2E mocks
> NOTHING** (real frontend → real backend → **real OpenRouter**). The earlier wording in
> ADR-000 §10 ("nothing mocked except LLM") contradicts this and must be corrected to match
> `AGENTS.md`; the `stub-llm` profile is a dev/demo/integration helper, **never** the E2E gate.

## Source-of-truth documents (orchestrator quotes the relevant slice into each task prompt)

- `docs/PRD-Product-Requirements-Document.md` — functional behavior, acceptance criteria AC-01..AC-27.
- `docs/ADR/000-main-architecture.md` — system, data models, global testing strategy, TAC-01..TAC-10.
- `docs/ADR/001-backend-api.md` — endpoints, DTOs, error model, TAC-001-01..08.
- `docs/ADR/002-llm-integration.md` — gateway, prompts, structured outputs, TAC-002-01..08.
- `docs/ADR/003-frontend.md` — screens, SSE consumption, validators, TAC-003-01..10.
- `docs/design-guidelines.md` + `assets/design-tokens.json` — NBP brand tokens/fonts.
- `docs/policies/polityka-zwrotow.md`, `docs/policies/polityka-reklamacji.md` — agent rules.

---

## Orchestration principles

1. **Minimal context per task.** Each delegated task gets: the exact ACs/TACs it must satisfy (quoted), the relevant contract slice, the file paths it owns, the verification command to run, and the commit message. Not the whole PRD/ADR set.
2. **Contract-first decoupling.** A single checked-in contract artifact + shared fixtures lets the FE track run **fully concurrently** with the BE track. The only true FE→BE runtime dependency is the final "re-point to live proxy" step.
3. **Worktree discipline.** After the fork, **no dev worktree edits any root or `docs/**` file.** The orchestrator owns root/docs/plan files on the integration branch and merges them down before each fork. This makes policies and contract read-only inputs, not conflict sources.
4. **Commit cadence.** One focused commit per step (`Backend:`/`Frontend:`/`QA:`/`Docs:`). Merge per **milestone**, not per commit. Re-fork fresh worktrees off the updated integration branch at each milestone. Keep milestones ≤ ~10 commits.
5. **Each step is verified before commit** with the scope-appropriate command; an agent reports red→green honestly.

---

## Critical path (true serialization points)

```
Phase 0 (foundation + contract freeze)  ──►  M0 merge  ──►  FORK two worktrees
                                                              │
                        ┌─────────────────────────────────────┴───────────────┐
                  BE track (Phase 1 ► 3 ► 4.1-4.2)                  FE track (Phase 2, concurrent)
                        └─────────────────────────────┬───────────────────────┘
                                                  M4 merge (rejoin)
                                                       │
                                          Phase 4.3 FE re-point to live proxy
                                                       │
                                          Phase 5 QA (smoke + E2E + live smoke)
                                                       │
                                          Phase 6 polish + README + final merge
```

**Must exist before forking:** generated Maven wrapper, both apps scaffolded green, shared contract artifact, shared fixtures (incl. SSE transcript), policy-load decision, WireMock conventions.
**Must be merged before QA E2E:** BE contract endpoints + **real** LLM integration + streaming; FE intake + chat (branded, icons loaded); a real `OPENROUTER_API_KEY` available. (The `stub-llm` profile may exist for dev, but the E2E gate uses the real stack.)

---

## Phase 0 — Foundation & contract freeze (shared branch, before fork)

> Goal: both apps compile/test green, and every cross-agent seam is frozen as a checked-in artifact so the two tracks never block each other.

| Step | Agent | Deliverable | Verify | Commit |
|---|---|---|---|---|
| 0.1 | be | Scaffold `app/backend` (Spring Boot 3.5, Java 21, Maven), **generate `mvnw`/`mvnw.cmd`** honoring `.gitattributes` (LF for `mvnw`, CRLF for `*.cmd`), `/health` via Actuator | `./mvnw -v` then `./mvnw test` | `Backend: scaffold Spring Boot app + Maven wrapper` |
| 0.2 | be | Wire test stack (JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, **WireMock**) + one passing smoke test | `./mvnw test` | `Backend: add test harness (JUnit5, Mockito, WireMock)` |
| 0.3 | fe | Scaffold `app/frontend` (**Angular 18**, Material + ngx-markdown pinned to v18), `proxy.conf.json` → `:8080` | `npm test && npm run lint && npm run build` | `Frontend: scaffold Angular 18 app + Material + proxy` |
| 0.4 | orchestrator | **Contract artifact** `docs/contracts/api-contract.md`: enums (CaseType, the PRD §8 equipment list, the 4 DecisionCategory values, ImageAnalysis fields), DTO shapes, error model `{code,message,fields?}` + status codes, **exact SSE frame format** | review | `Docs: freeze API contract for PoC` |
| 0.5 | orchestrator | **Shared fixtures** `app/fixtures/`: valid JPEG/PNG/WebP, >10MB generator, one `ImageAnalysis` + one `DecisionResult` JSON **per scenario × per category** (8), and a chunked **SSE transcript** (incl. a frame split across chunk boundaries + terminal `done` + mid-stream `error`) | review | `Docs: add shared test fixtures + SSE transcript` |
| 0.6 | orchestrator | Decide & document **policy-load path** (copy into `backend/src/main/resources` vs read repo path) and any `.gitignore` additions | review | `Docs: decide policy-load strategy` |

**M0 — merge 0.1–0.6 into `electronic-complains-chat-app`, then fork two worktrees** (one for be-developer, one for fe-developer).

---

## Phase 1 — Backend core, LLM stubbed (be worktree)

> All steps use an **in-memory stub `LlmGateway`** and **WireMock**; no live LLM. Each step: test first → red → implement → `./mvnw test` green → commit.

1. **1.1 ImageCompressor** — downscale to configured max long edge, re-encode JPEG; output smaller than input, long edge ≤ max, tiny image not upscaled. *(TAC-001-02, TAC-001-06)*
2. **1.2 SessionStore** interface + InMemory impl — create/get/appendMessage/exists, concurrent append.
3. **1.3 PolicyProvider** — returns return policy for `ZWROT`, complaint policy for `REKLAMACJA`.
4. **1.4 Request DTO + Bean Validation** — required/blank, `requiredIfComplaint` reason, future-date rejected, max-lengths. *(AC-03..AC-06)*
5. **1.5 GlobalExceptionHandler** — 400 VALIDATION_ERROR (+`fields`), 415 UNSUPPORTED_MEDIA_TYPE, 413 PAYLOAD_TOO_LARGE; multipart max-size config. *(TAC-001-08)*
6. **1.6 `POST /cases` integration (stub gateway)** — happy + each validation failure asserts **0 outbound LLM calls** via WireMock count. *(TAC-001-01, TAC-01/02/03)*
7. **1.7 MessageComposer** — first message = greeting + body + **mandatory disclaimer**, for every category. *(TAC-001-04, TAC-001-05, AC-24)*
8. **1.8 Decision enum coercion** — any out-of-set category → `NEEDS_HUMAN_REVIEW`. *(TAC-04)*
9. **1.9 `GET /cases/{id}`** — summary + transcript; 404 unknown.
10. **1.10 `POST /cases/{id}/messages` SSE (stub stream)** — `Content-Type: text/event-stream`, ≥1 token + terminal `done` (tested against the **Phase-0 SSE transcript**); 404 before any LLM; 400 empty message. *(TAC-001-05, TAC-001-07)*
11. **1.11 Deterministic stubbed-LLM run profile** (e.g. Spring profile `stub-llm`) — boots the real backend with a canned `LlmGateway` returning per-category fixtures; **this is the QA E2E backend.** *(supports ADR-000 §10 E2E strategy)*

**M1 — merge be worktree → integration branch.**

---

## Phase 2 — Frontend core (fe worktree, CONCURRENT with Phase 1)

> Runs against the **contract + fixtures only**, not a live backend. Each step test-first (`*.spec.ts`).

1. **2.1 Models** — TS types/enums mirroring the contract artifact (CaseType, EquipmentCategory + Polish labels, DecisionCategory, SubmitCaseResponse, ChatMessage, ApiError).
2. **2.2 Validators** — `futureDateForbidden`, `requiredIfComplaint`, file type/size. *(TAC-003-01/02/03)*
3. **2.3 api.service** — multipart submit + `ApiError` normalization (busy/error states). *(TAC-003-05)*
4. **2.4 SSE parser** — `fetch()`+`ReadableStream`+`TextDecoder`, tested against the **Phase-0 SSE transcript** incl. split-frame + `error` + `done`. *(TAC-003-06)* — highest-risk seam, do early.
5. **2.5 Intake component** — Reactive Form, file preview, objectURL revoke on destroy/replace, inline validation, submit/loading/error with **data preserved on error**. *(TAC-003-04/05, AC-07/08)*
6. **2.6 Navigate + first message render** — markdown render with **disclaimer always visible**; `missingInfo` shown for MORE_INFO_REQUIRED. *(TAC-003-08, AC-20)*
7. **2.7 Chat component** — streaming bubble accumulation + typing indicator only while streaming + decision-category visual highlight + unknown-category guard. *(TAC-003-07)*
8. **2.8 Case-summary header + Polish-label audit** — all UI text Polish; NBP design tokens/fonts applied. *(TAC-003-09, AC-25)*

**M2 — merge fe worktree → integration branch** (alongside M1).

---

## Phase 3 — LLM integration (be worktree, after M1)

> WireMock for all tests; **live key used for manual prompt iteration only** (outside the suite).

1. **3.1 OpenAiClientConfig** — explicit `.baseUrl()` + **`OPENROUTER_API_KEY` only** (OpenRouter is the sole provider; do **not** fall back to a machine-global `OPENAI_API_KEY` — sending an OpenAI key to the OpenRouter endpoint yields `401: Missing Authentication header`, see Remediation §R1); **never `fromEnv()`**, never api.openai.com; optional attribution headers. *(TAC-002-01/02)*
2. **3.2 Model routing** — vision→`OPENROUTER_VISION_MODEL`, decide/chat→`OPENROUTER_TEXT_MODEL`, fallback `OPENROUTER_MODEL`. *(TAC-002-03)*
3. **3.3 analyzeImage** — base64 `data:image/jpeg` image_url; structured `ImageAnalysis` per scenario. *(TAC-002-06, AC-11/12/13)*
4. **3.4 decide** — structured `DecisionResult`; **full policy text present in request payload**; per-category parse. *(TAC-002-04/05, AC-14/16/17)*
5. **3.5 Retry/fail-closed** — transient 5xx/429/timeout retried to bound, then gateway exception → **502/503 with no session persisted**. *(TAC-001-06, TAC-002-07)*
6. **3.6 `/chat/completions` only** — assert `/responses` never called. *(TAC-002-08, TAC-10)*
7. **3.7 PromptCatalog** — 4 prompts + chat system prompt, Polish output, escalation rules (never invent facts; insufficient/contradictory → MORE_INFO_REQUIRED/NEEDS_HUMAN_REVIEW). *(AC-18/19)* Wire real gateway into CaseService; full submit per scenario.

**M3 — merge be worktree.**

---

## Phase 4 — Streaming chat end-to-end

1. **4.1 streamChat (be)** — WireMock SSE token stream + mid-stream error → SSE `error` event.
2. **4.2 ChatService (be)** — append user msg → build full context (form+analysis+decision+transcript) → stream → append assistant msg; bounded executor for emitters. *(AC-21/22, off-topic decline AC-23)*
3. **M4 — merge be + fe worktrees → integration branch (rejoin).**
4. **4.3 FE re-point (fe)** — point at the real `/api` proxy; verify incremental SSE render against the running backend. *(only true FE→BE dependency)*

---

## Phase 5 — QA (integration branch, qa-engineer)

> **E2E mocks NOTHING** (root `AGENTS.md`). The authoritative QA gate runs the real
> frontend → real backend → **real OpenRouter** with a real API key. The `stub-llm`
> profile is only a fast dev/sanity lane and is **never** the sign-off gate. Use **real
> images** from `assets/example-images-for-tests/` everywhere (never synthetic JPEGs).

1. **5.0 Real-stack boot** — boot the **real backend** (`./mvnw spring-boot:run`, real `OPENROUTER_API_KEY`) + FE. (Optionally also boot a `stub-llm` instance as a separate, clearly-labelled non-authoritative fast lane.)
2. **5.1 Manual smoke (Playwright MCP) — MANDATORY, against the real stack.** Walk form→decision→chat→follow-up with a **real photo**; screenshot each step; compare every screen to `docs/design-guidelines.md` **and `assets/homepage.png`** (logo present, navy header, no broken icons, Polish, disclaimer on every decision). File bugs. "Tests pass" ≠ "app works" — this step is required, not optional.
3. **5.2 Automated E2E (real stack, real LLM).** Assert **structure, not LLM wording**: navigation form→chat, decision bubble shows **one of the four** categories, **disclaimer always present**, incremental SSE render, off-topic decline-and-redirect, validation 400/413/415, LLM-unavailable 502/503 with **retry + data preserved**. Use real images covering JPEG/PNG/WebP.
4. **5.3 Deterministic category coverage lives at the BE integration layer (WireMock), not E2E.** The four category-logic outcomes (signs-of-use ⇒ NOT_ELIGIBLE, etc.) are asserted in BE integration tests where the LLM response is controlled; E2E must not pin a live model to a specific category (models are nondeterministic).
5. **5.4 Completion gate:** the real-LLM path must have been exercised end-to-end at least once (5.1 + 5.2) and manually confirmed (5.1) **before** anyone claims the goal is complete.

---

## Phase 6 — Integration polish & docs

- Fix any defects QA filed (routed to be/fe-developer with the failing test reproduced first).
- `README` run instructions (two dev processes + proxy + env vars).
- **Design fidelity pass (gated, not cosmetic):** compare each screen to `assets/homepage.png` + `docs/design-guidelines.md` — NBP navy header + gold `logo.svg` on both screens, palette/typography from `design-tokens.json`, **icon font loaded (no broken glyphs)**, Polish `<title>`/`lang="pl"`/favicon. Sign off with side-by-side screenshots.
- Final merge to `electronic-complains-chat-app`. (No push unless explicitly requested.)

---

## Dependency matrix

| Task | Agent | Depends on |
|---|---|---|
| 0.1 BE scaffold + mvnw | be | — |
| 0.2 BE test harness | be | 0.1 |
| 0.3 FE scaffold | fe | — |
| 0.4 contract artifact | orchestrator | — |
| 0.5 fixtures + SSE transcript | orchestrator | 0.4 |
| 0.6 policy-path + gitignore | orchestrator | — |
| **M0 merge + fork** | orchestrator | 0.1–0.6 |
| Phase 1 (BE core, stub) | be | M0 |
| Phase 2 (FE core) | fe | M0 (**not** Phase 1) |
| Phase 3 (LLM) | be | M1 |
| Phase 4.1–4.2 (BE stream) | be | M1 |
| **M4 rejoin** | orchestrator | Phase 3, 4.1–4.2, M2 |
| 4.3 FE re-point | fe | M4 |
| 5.0 stub run profile | qa (+ be 1.11) | M4 |
| 5.1–5.3 E2E | qa | M4 + 5.0 |
| Phase 6 | all | Phase 5 |

---

## LLM usage by phase

| Phase | LLM source |
|---|---|
| 0–2 | None (BE in-memory stub gateway; FE uses fixtures only — FE never touches the LLM, TAC-003-10/TAC-09) |
| 1 / 3 / 4 integration tests | **WireMock** (canned structured outputs, 5xx fail-closed, SSE stream, `/responses`-never). This is where deterministic all-category coverage lives. |
| 3 prompt iteration | **Live** (manual, outside the suite) |
| 5.1 manual smoke + 5.2 automated E2E | **Live OpenRouter (real stack)** — E2E mocks nothing. Assert structure, not wording. |
| `stub-llm` profile | Dev/demo/fast-sanity lane only — **never** the E2E sign-off gate. |

---

## Worktree / merge hazards

- Disjoint `app/backend` vs `app/frontend` ⇒ near-zero code conflicts. Real risk is **shared root/docs files**: `.gitignore`, `.gitattributes`, `.env*`, `docs/**`, `README.md`, `app/README.md`, `.mcp.json`, `.claude/**`.
- **Rule:** dev worktrees never edit root/docs files; orchestrator owns them and merges down before each fork.
- If policies are *copied* into `backend/src/main/resources` (0.6), `docs/policies` stays the single source — keep them in sync, don't diverge.
- `mvnw` generation must honor `.gitattributes` line endings (Windows host + Git Bash).

---

## Top risks & mitigations

1. **Missing `mvnw` breaks every BE/QA command** → Phase-0 gate 0.1; verify `./mvnw -v` before any other BE step.
2. **No deterministic backend profile → flaky/expensive E2E, can't hit all 4 categories** → make `stub-llm` profile a Phase-1 deliverable (1.11), reused by QA (5.0).
3. **SSE wire-format drift between isolated worktrees** → freeze byte-level SSE fixture in Phase 0 (0.5); steps 1.10 and 2.4 both test against it.
4. **FE blocking on BE** → contract (0.4) + fixtures (0.5) make Phase 2 fully concurrent; only 4.3 needs a live backend.
5. **Structured-output schema mismatch (ImageAnalysis/DecisionResult)** → derive both BE classes and FE models from the single contract artifact; per-category fixtures catch drift.
6. **Live-model nondeterminism on category boundaries** → assert *category logic* deterministically at the **BE integration layer (WireMock)**; in **E2E (real LLM)** assert only *structure* (a valid category is shown, disclaimer present, streaming works, off-topic redirects). Never stub the LLM in E2E to force a category — that hides whether the app actually works.
7. **Stubbed E2E creating false confidence** (this happened) → E2E mocks NOTHING; the real-LLM path must run end-to-end at least once and be manually confirmed (Playwright MCP) before "complete" is claimed. `stub-llm` is never the E2E gate.
8. **UI ships off-brand / with broken icons** (this happened: no logo, no header, unloaded icon font) → visual fidelity is a gated acceptance criterion; compare every screen to `assets/homepage.png` + `docs/design-guidelines.md`; verify no `<mat-icon>` renders as broken text.
9. **Synthetic test images hide real bugs** (this happened: BUG-001) → always use real photos from `assets/example-images-for-tests/` in both manual and automated tests.

---

## Verification (end-to-end, before final merge)

- **Backend:** `cd app/backend && ./mvnw test && ./mvnw clean package` — all green; TAC-001-* and TAC-002-* covered.
- **Frontend:** `cd app/frontend && npm test && npm run lint && npm run build` — all green; TAC-003-* covered.
- **E2E (real stack, real LLM):** boot **real BE** (`./mvnw spring-boot:run`, real key) + FE, run Playwright suite — full form→decision→chat journey + validation/error/retry/off-topic paths green. Structure asserted, not LLM wording. Real images from `assets/example-images-for-tests/`.
- **Manual (Playwright MCP, real stack):** walk every PRD §4 flow by hand with a real photo; confirm Polish UI, NBP branding (logo + navy header), no broken icons, disclaimer on every decision, retry preserves form data; compare to `assets/homepage.png`.
- **Completion gate:** do not claim "complete" until the real-LLM path has run end-to-end and been manually confirmed.

---

## Post-implementation remediation log (2026-06-25)

The first implementation pass shipped with **stubbed E2E tests that mocked the core LLM
functionality**, hiding that the app did not actually work end-to-end. A real-verification pass
(real backend → real OpenRouter → real device photos, plus a manual Playwright MCP walkthrough)
found and fixed the following. All BE (53) + FE (34) unit tests and the rewritten E2E suite (9,
real stack) are green, and every flow was manually confirmed.

- **R1 — LLM 401 (app never reached the model).** `application.yaml` resolved the key as
  `${OPENAI_API_KEY:${OPENROUTER_API_KEY:}}`, so a machine-global personal `OPENAI_API_KEY` was
  sent to the OpenRouter base URL → `401: Missing Authentication header` → every submit 502'd.
  Fixed to `api-key: ${OPENROUTER_API_KEY:}` (OpenRouter only). Plan §3.1 corrected.
- **R2 — empty AI output.** Prompts said "respond in JSON per the schema" but **never gave a
  schema**; with `json_object` mode the model invented its own keys (`{decision:…}`,
  `{damage_visible:…}`), so `justification`/`nextSteps` and the whole `ImageAnalysis` parsed to
  empty. Fixed by embedding the exact field/enum schema in `PromptCatalog` (both prompts).
- **R3 — WebP uploads 400'd.** The JDK `ImageIO` cannot decode WebP, an advertised format. Added
  `com.github.usefulness:webp-imageio:0.10.0` (bundles patched libwebp, post CVE-2023-4863).
- **R4 — streamed chat text was mangled.** Backend emitted raw `data:<token>`; the FE parser
  `.trim()`-ed each frame, destroying token spacing ("Chętnie pomogę" → "Chętniepomogę"). Fixed by
  **JSON-encoding each SSE delta** on the backend and JSON-parsing on the FE (preserves spaces,
  newlines, unicode). SSE unit tests + fixture updated.
- **R5 — manual Polish date entry failed validation.** The datepicker used the default en-US
  `NativeDateAdapter`, which can't parse `15.01.2026`; the placeholder promised `DD.MM.RRRR`. Added
  a `PlDateAdapter` + `pl-PL` locale; typed Polish dates now parse and the calendar is localized.
- **R6 — off-brand, "very ugly" UI.** No NBP header/logo, off-brand amber Material accent, and the
  Material icon font wasn't loaded (icons rendered as literal text, e.g. "upload"). Added an NBP
  navy shell (gold `logo.svg` header + footer disclaimer), NBP-blue accent, Material Icons font,
  `lang="pl"` + proper `<title>`. Verified against `assets/homepage.png`.
- **R7 — silent failures / leaked internals.** A failed submit gave the user no feedback, and the
  502 body leaked the upstream error text. FE now shows a prominent error banner (data preserved,
  scrolled into view, button re-enabled); BE returns a safe generic Polish message and logs the
  real cause server-side. Raw-LLM DEBUG logging is opt-in (`APP_LOG_LEVEL`, default INFO).
- **R8 — E2E rewritten** to the real stack with real images and structural assertions (valid
  decision is one-of-four, disclaimer present, incremental streaming, off-topic redirect,
  validation paths, date regression). The old stub-prefix routing was removed.

> **Runtime note:** the app requires **Java 21** (the machine default is Java 25); build/run with
> `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. Spring does not auto-load `.env`; source it
> (`set -a; . ./.env; set +a`) before `spring-boot:run`.

---

## Per-task delegation template (how each agent is briefed)

Every delegated task prompt contains exactly these blocks (and nothing more):

```
TASK: <one step, e.g. "1.1 ImageCompressor">
CONTEXT SLICE: <only the ACs/TACs + contract fields this step needs, quoted>
FILES YOU OWN: <specific paths under app/backend or app/frontend>
DO NOT TOUCH: root files, docs/**, the other app/ subtree
TDD: write the failing test first (cite the AC/TAC), confirm red, implement minimum, verify green
VERIFY: <exact command>
COMMIT: <exact message, e.g. "Backend: add ImageCompressor with downscale + JPEG re-encode">
REPORT BACK: red→green evidence + the commit hash
```

**Conflict rule for every agent:** if the task, an ADR, or this plan contradicts the root
or nested `AGENTS.md` (e.g. "stub the LLM in E2E"), **STOP and flag it** — do not silently
comply. The `AGENTS.md` files win.
