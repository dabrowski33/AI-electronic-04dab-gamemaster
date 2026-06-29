# AGENTS.md — E2E tests (`app/e2e/`)

> Scope: Playwright end-to-end tests. This file **overrides** anything weaker in the
> root `AGENTS.md`, the ADRs, or the plan. If they conflict, the rule here and in the
> root `AGENTS.md` win — **STOP and flag the conflict to the user, do not silently pick one.**

## THE GOLDEN RULE: E2E mocks NOTHING

The root `AGENTS.md` test-strategy table is binding:

| Type | Mocks |
|---|---|
| E2E | **NOTHING (real stack)** |

**E2E means: real Angular frontend → real Spring Boot backend → real OpenRouter LLM.**

- E2E runs the backend with the **real `OpenRouterLlmGateway`** and a **real `OPENROUTER_API_KEY`** (or `OPENAI_API_KEY`). Set it via `.env` / environment before running.
- **NEVER** run the authoritative E2E suite against the `stub-llm` profile.
- **NEVER** mock, stub, or record the LLM in E2E. No WireMock, no canned JSON, no in-memory gateway. That is what integration tests are for — not E2E.
- The whole point of E2E is to prove the app **actually works against the real provider**. A stubbed E2E proves nothing and is worse than no E2E because it creates false confidence.

### Why a previous run got this wrong
An earlier implementation ran the entire E2E suite on `stub-llm` and drove decisions by a fake model-name prefix (`ELIGIBLE:`, `NOT_ELIGIBLE:`…). That asserted stub behavior, not real behavior, and the real API path was never executed. Do not repeat this.

### What about the `stub-llm` profile then?
`stub-llm` exists **only** for: fast local FE development, and deterministic *unit/integration* category coverage at the BE layer (WireMock). It is **NOT** an E2E backend. You may keep one clearly-labelled non-authoritative "smoke (stubbed)" lane for a quick dev sanity check, but the **sign-off E2E gate uses the real stack** and the user is told which is which.

## Real images — always

Use the **real device photos** in `assets/example-images-for-tests/`:
`laptop-1.png`, `laptop-2.webp`, `phone-1.jpg`, `phone-2.jpeg`, `phone-3.jpeg`.

- Never fabricate a synthetic / 1×1 / hex-blob JPEG. A real vision model returns garbage or errors on those, and they hide real bugs (they caused BUG-001).
- Cover the declared formats: at least one JPEG, one PNG, one WebP across the suite.

## Assert structure, not LLM wording

Real models are nondeterministic. Never assert exact assistant text. Assert:
- Navigation `form → /chat/:sessionId` happened.
- The decision bubble shows **one of the four** categories (ELIGIBLE / NOT_ELIGIBLE / NEEDS_HUMAN_REVIEW / MORE_INFO_REQUIRED) — not a specific one.
- The **mandatory disclaimer** substring is present (it is deterministic — appended by the backend).
- The chat streams **incrementally** (assistant bubble grows token-by-token).
- An **off-topic** follow-up is declined/redirected.
- Validation paths (400/413/415) and the LLM-unavailable retry path (data preserved) behave correctly.

Deterministic *category-logic* assertions (e.g. "signs of use ⇒ NOT_ELIGIBLE") belong in **BE integration tests with WireMock**, never in E2E against a live model.

## QA must do BOTH — automated AND manual

"Tests pass" ≠ "the app works." Before any QA sign-off:

1. **Automated** Playwright suite — green against the **real stack**.
2. **Manual Playwright MCP** walkthrough — drive the real app by hand, take screenshots at each step (form → filled → decision → chat → follow-up), using **real images**, and:
   - confirm every PRD flow in `docs/PRD-Product-Requirements-Document.md` §4 actually works,
   - compare each screen against `docs/design-guidelines.md` **and `assets/homepage.png`** for visual/brand fidelity,
   - confirm no broken icons/glyphs, the NBP logo is present, Polish everywhere, disclaimer on every decision.
3. File any gap as a bug with a reproduction; route fixes to be/fe-developer (failing test first).

A task is **not** complete until the real-LLM path has been exercised end-to-end at least once and manually confirmed.

## Run
```bash
# Terminal 1 — REAL backend (needs OPENROUTER_API_KEY in env/.env)
cd app/backend && ./mvnw spring-boot:run

# Terminal 2 — frontend
cd app/frontend && npm start

# Terminal 3 — E2E
cd app/e2e && npx playwright test
```
