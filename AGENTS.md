# Repository Guidelines

## Project

This is a **course project** for the "AI dla programistów — od pomysłu do MVP" training by JSystems — a **dedicated (closed) course for NBP (Narodowy Bank Polski)**, 12 participants, starting **2026-06-22**. The app is a multimodal AI assistant built live during the course. The domain, tech stack, and architecture are decided by the group through a structured process: research → PRD → ADR → implementation with agents.

This started as the **base course repository**; the concrete stack and architecture were decided live with the group and are recorded in `docs/ADR/`.

**Decided stack (see `docs/ADR/`):** the app is the **Hardware Service Decision Copilot**.
- **Backend:** Java 21 + Spring Boot 3.5 (Spring Web MVC) + Maven, calling OpenRouter via the **openai-java** SDK (Chat Completions).
- **Persistence:** durable session/transcript store in embedded **H2** (file-backed) via Spring Data JPA (ADR-004). **Not SQLite** (evaluated and rejected — ADR-004 §6). The future vector/RAG store is a separate, deferred ADR.
- **Frontend:** Angular + Angular Material + ngx-markdown (custom streaming chat).
- **E2E:** Playwright against the real stack (nothing mocked, including a live LLM call).

(The base course materials reference an alternative TypeScript/Next.js + Vercel AI SDK demo stack; that path was **not** chosen for the NBP edition.)

All user-facing text in **Polish**.

**Key docs** (created during the course — load only when in doubt):
- `docs/PRD-Product-Requirements-Document.md` — product requirements and acceptance criteria
- `docs/ADR/` — Architecture Decision Records
- `docs/design-guidelines.md` — design system and tokens

---

## Repository Layout

```
app/                 Application built during the course (start: empty scaffold)
assets/              Design tokens, logo, favicon
docs/                PRD, ADR, design system
course-materials/    Notes, scripts, examples, research
```

---

## Agent Workflow

### Before Starting Any Task
1. Read the relevant PRD and ADR files for the affected area.
2. Define the expected behavior from the specification before writing or changing any code.

### TDD Rules
For every feature and bug fix:
1. Start from the specification, not the existing implementation.
2. Write or extend tests **before** production code.
3. Run the new tests and confirm they fail for the expected reason.
4. Implement the minimum code needed to make them pass.
5. Run the full verification suite for the changed scope.
6. Refactor only while tests stay green.

If the area has no suitable test infrastructure yet, add it as part of the task — do not silently skip tests.

### Verification (required before every commit)

Run the commands appropriate for the changed scope:

Backend (`app/backend/`):
```bash
./mvnw test            # JUnit tests pass
./mvnw clean package   # build succeeds
```

Frontend (`app/frontend/`):
```bash
npm test             # unit tests pass
npm run lint         # no lint errors
npm run build        # build succeeds
```

Verify only the scope relevant to your change. If the change affects runtime behavior, confirm the app starts correctly.

**Test Strategy:**
| Type | Mocks | Who |
|---|---|---|
| Unit | All deps | be/fe-dev |
| Integration | Only external LLM API | be-dev |
| E2E | NOTHING (real stack) | qa-engineer |

**Verification:** Always start the app before committing. Tests passing ≠ app working.

**Env Vars:** See `.env.example` (OPENROUTER_API_KEY or OPENAI_API_KEY required)

### Commit Rules
- Commit only after verification passes and the changed scope is in a working state.
- Keep commits focused: one logical change per commit.
- Format: `Area: short summary` (e.g. `Backend:`, `Frontend:`, `Docs:`)
- Do **not** push to remote unless the user explicitly asks.

### Completion Criteria
A task is complete only when:
- Implementation matches the relevant PRD, ADR, and design guidance
- Tests were written first and pass honestly
- Verification for the changed scope passed with no errors or warnings
- The commit message is focused and the repository is in a consistent, reviewable state

---

## Context7 MCP Library IDs

Common libraries (resolve via `resolve-library-id` if the ID changes):

| Library | Context7 ID |
|---|---|
| openai-java | `/openai/openai-java` |
| Spring Boot | `/spring-projects/spring-boot` |
| Angular | `/websites/angular_dev` |
| Angular Material | `/websites/material_angular_dev` |
| ngx-markdown | `/jfcere/ngx-markdown` |
