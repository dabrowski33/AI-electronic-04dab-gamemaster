# AGENTS.md — Backend (`app/backend/`)

> Scope: Spring Boot backend. Complements the root `AGENTS.md`. If any ADR or the plan
> conflicts with the root `AGENTS.md`, **STOP and flag it to the user** — do not silently
> follow the weaker rule. (A real example: ADR-000 §10 once said E2E may stub the LLM,
> which contradicts root `AGENTS.md` "E2E mocks NOTHING". Root `AGENTS.md` wins.)

## LLM integration (ADR-002 is binding)

- Use the **openai-java SDK pointed at OpenRouter** via explicit `.baseUrl(...)` + `.apiKey(...)`. **Never `fromEnv()`**, never hit `api.openai.com`.
- Key precedence: `OPENAI_API_KEY` if set, else `OPENROUTER_API_KEY`. Read from config; **never hardcode or log a key**.
- Use **`/chat/completions` only** — never `/responses` (TAC-002-08).
- **Structured outputs:** ADR-002 / TAC-002 require strict `json_schema` (`responseFormat(<schema>)`), not plain `json_object` mode. If you must temporarily use `json_object`, that is a **deviation — document it and flag it**, because plain JSON mode lets a real model drift field names and silently break the parser. (The current code uses `ResponseFormatJsonObject`; this is a known gap to close, not a pattern to copy.)
- Model routing: vision → `OPENROUTER_VISION_MODEL`, decide/chat → `OPENROUTER_TEXT_MODEL`, fallback `OPENROUTER_MODEL` (TAC-002-03).
- Image sent as base64 `data:image/jpeg;base64,...` `image_url` part (TAC-002-06).
- Retry transient 5xx/429/timeout to a bound, then fail closed → 502/503 with **no session persisted** (TAC-002-07).

## The `stub-llm` profile is a DEV/DEMO tool only

- `StubLlmGateway` exists for fast FE dev and as a deterministic helper. It is **NOT** evidence that anything works.
- **A backend feature touching the LLM is not "done" until it has been run once against the REAL OpenRouter** with a **real image from `assets/example-images-for-tests/`**, and the real structured output parsed correctly. WireMock + stub passing is necessary but **not sufficient**.
- Keep the `stub-llm` decision routing (model-name prefix) out of any real-behavior claim.

## Testing layers (root `AGENTS.md` table)

| Layer | Mocks | Tool |
|---|---|---|
| Unit | all deps | JUnit 5 + Mockito + AssertJ |
| Integration | **only** the external LLM (HTTP) | Spring Boot Test + MockMvc + **WireMock** |
| E2E | **nothing** (real stack incl. real LLM) | handled in `app/e2e` |

- Integration tests stub OpenRouter **at the HTTP boundary with WireMock** — never by swapping in the in-memory stub gateway. Assert: request shape, model routing, policy text present in payload (TAC-002-04), enum coercion, `/responses` never called, SSE stream + mid-stream error.
- TDD: write the failing test first, confirm red, implement minimum, verify green.

## Verification (before every commit)
```bash
./mvnw test            # all green
./mvnw clean package   # build succeeds
```
If the change affects runtime behavior, **start the app and exercise it** (real profile when LLM is involved) — passing tests ≠ working app.

## Conventions
- Package layout: `web`, `application`, `llm`, `image`, `session`, `policy`, `config`, `model`, `dto` (ADR-001). Dependency direction strictly inward.
- The mandatory disclaimer is appended **deterministically by `MessageComposer`** — never delegated to the model (AC-24, TAC-005).
- Policies are loaded from `src/main/resources/policies/` (copies of `docs/policies/*.md`); keep them in sync, don't diverge.
