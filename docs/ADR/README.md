# Architecture Decision Records

Technical architecture for the **Hardware Service Decision Copilot** (see [`../PRD-Product-Requirements-Document.md`](../PRD-Product-Requirements-Document.md)).

| ADR | Area | Summary |
|---|---|---|
| [000-main-architecture](000-main-architecture.md) | System | SPA + REST, stack, modules, shared data models, env, end-to-end flows, global test strategy |
| [001-backend-api](001-backend-api.md) | Backend | Spring Boot MVC endpoints, validation, image compression, session store, SSE |
| [002-llm-integration](002-llm-integration.md) | LLM | openai-java → OpenRouter (Chat Completions), prompts, structured outputs, streaming |
| [003-frontend](003-frontend.md) | Frontend | Angular + Material, custom chat UI, file upload, SSE consumption, ngx-markdown |
| [004-persistence](004-persistence.md) | Persistence | Durable session/transcript store in **H2** (file-backed) via Spring Data JPA; restore on reload; vectors deferred to a future ADR |

## Key decisions at a glance
- **Stack:** Java 21 + Spring Boot 3.5 (Web MVC) + Maven; Angular (latest stable) + Angular Material; OpenRouter via `com.openai:openai-java`.
- **API choice:** OpenRouter **Chat Completions** (not the beta/stateless Responses API).
- **Streaming:** first decision = structured + spinner; chat follow-ups = SSE (`SseEmitter` ↔ `fetch`+`ReadableStream`).
- **State:** server-side `SessionStore` interface with a durable **H2** file-backed JPA implementation (ADR-004); in-memory impl kept for tests. The future vector/RAG store is a **separate, deferred ADR** — not SQLite (ADR-004 §6/§8).
- **Chat UI:** custom Material component + `ngx-markdown` (no SaaS-coupled/stale chat library).
- **Run:** two local dev processes (Angular `:4200` → Spring Boot `:8080`) via dev proxy.

> Scope of this step: **ADRs only**. Project scaffolding/initialization (Maven Spring Boot backend + Angular frontend skeletons) is the next step.
