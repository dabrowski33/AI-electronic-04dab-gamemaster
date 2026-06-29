# Hardware Service Decision Copilot — PoC

Samoobsługowa aplikacja webowa: klient wypełnia formularz, przesyła zdjęcie sprzętu i otrzymuje wstępną, doradczą decyzję AI (zwrot / reklamacja), a następnie rozmawia z agentem.

> **Uwaga:** Decyzja jest niewiążąca — ostateczną decyzję podejmuje konsultant.

## Wymagania

- Java 21, Node.js 18+, npm 9+
- `.env` z `OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL`, `OPENROUTER_TEXT_MODEL`, `OPENROUTER_VISION_MODEL` (skopiuj z `.env.example`)

## Uruchomienie (dev)

```bash
# Terminal 1 — Backend (port 8080)
cd app/backend
./mvnw spring-boot:run                                        # z żywym LLM
./mvnw spring-boot:run -Dspring-boot.run.profiles=stub-llm   # ze stubbem (bez klucza)

# Terminal 2 — Frontend (port 4200)
cd app/frontend && npm install && npm start
```

Otwórz: **http://localhost:4200**

> **Wymaga Java 21** (domyślna na maszynie może być nowsza): ustaw
> `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. Spring nie ładuje `.env` automatycznie —
> przed `spring-boot:run` wczytaj go: `set -a; . ../../.env; set +a`. Backend używa **wyłącznie
> `OPENROUTER_API_KEY`** (nie korzysta z `OPENAI_API_KEY`).

## Profil stub-llm (tylko dev)

Profil `stub-llm` to **wyłącznie** szybka ścieżka deweloperska bez klucza API (zwraca kanoniczne
odpowiedzi). **Nie jest** bramką E2E — testy E2E uruchamiają realny stos z prawdziwym OpenRouter.

## Testy

```bash
cd app/backend  && ./mvnw test           # 53 testy BE (JUnit + WireMock)
cd app/frontend && npm test -- --watch=false --browsers=ChromeHeadless  # 34 testy FE
cd app/e2e      && npx playwright test   # E2E na realnym stosie z prawdziwym LLM (wymaga uruchomionej aplikacji + OPENROUTER_API_KEY)
```

E2E używa prawdziwych zdjęć z `assets/example-images-for-tests/` i sprawdza **strukturę**
(nawigacja, jedna z 4 kategorii decyzji, klauzula, strumieniowanie, odrzucenie pytań off-topic),
a nie konkretnych słów modelu.

## Stack

The stack is decided in `../docs/ADR/`:
- **Backend** (`backend/`) — Java 21 + Spring Boot 3.5 (Spring Web MVC) + Maven; calls OpenRouter via the openai-java SDK.
- **Frontend** (`frontend/`) — Angular 18 + Angular Material + ngx-markdown (custom streaming chat).
- **E2E** (`e2e/`) — Playwright against the real stack (real OpenRouter LLM, real images).

## How to start

The app is scaffolded through a structured process:

1. **Research** — use agents to research and validate the project idea
2. **PRD** — generate a Product Requirements Document (`../docs/PRD-Product-Requirements-Document.md`)
3. **ADR** — generate Architecture Decision Records (`../docs/ADR/`) to choose the tech stack
4. **Scaffold** — backend via Spring Initializr; frontend via `ng new`
5. **Implement** — build features with agents using TDD

## Checklist

Use this checklist during scaffolding. Some items are provided by the generators (Spring Initializr, Angular CLI); others you add explicitly.

### Backend (`backend/`)
- [ ] Scaffold via Spring Initializr — Spring Boot 3.5.x, Java 21, Maven
- [ ] Dependencies: Spring Web, Validation, Actuator
- [ ] Add `com.openai:openai-java` (LLM via OpenRouter) and image lib (Thumbnailator)
- [ ] Package layout: `web`, `application`, `llm`, `image`, `session`, `policy`, `config` (ADR-001)
- [ ] `application.yaml` + config binding for OpenRouter env vars

### Frontend (`frontend/`)
- [ ] Scaffold via `ng new` — latest stable Angular, standalone components, routing
- [ ] Add Angular Material (`ng add @angular/material`) and `ngx-markdown`
- [ ] `proxy.conf.json` mapping `/api` → `http://localhost:8080`
- [ ] Feature folders: `core`, `features/intake`, `features/chat`, `shared` (ADR-003)

### Code quality
- [ ] Backend: standard Spring Boot conventions, 4-space indent
- [ ] Frontend: ESLint (`ng lint`), Prettier, `.editorconfig` (optional)

### Testing
- [ ] Backend unit/integration: JUnit 5 + Mockito + AssertJ + Spring Boot Test/MockMvc; **WireMock** to stub OpenRouter
- [ ] Frontend unit: Angular testing utilities (`*.spec.ts`)
- [ ] E2E: Playwright against the real stack (real OpenRouter LLM, real images)

### Environment
- [ ] `.env.example` with required env vars (see `../docs/ADR/000-main-architecture.md` §7)
- [ ] `OPENROUTER_API_KEY` / `OPENROUTER_BASE_URL` / model vars set locally
- [ ] `.gitignore` (target/, node_modules/, .env, build output, etc.)

### AI integration
- [ ] openai-java client configured with explicit `.baseUrl(OPENROUTER_BASE_URL)` + `.apiKey(...)` (no `fromEnv()`)
- [ ] Chat Completions only (vision, structured outputs, streaming) — never `/responses`
- [ ] `LlmGateway` seam: `analyzeImage` / `decide` / `streamChat`

### Design
- [ ] Design tokens (`../assets/design-tokens.json`)
- [ ] Logo and favicon (`../assets/`)
- [ ] Design system doc (`../docs/design-guidelines.md`)

### Documentation
- [ ] PRD (`../docs/PRD-Product-Requirements-Document.md`)
- [ ] ADRs (`../docs/ADR/`)
- [ ] AGENTS.md / stack-specific rules where helpful

## Notes

- Don't hand-create config files the generators already provide (Spring Initializr, Angular CLI) — it leads to conflicts.
- Run the two dev processes together: Spring Boot on `:8080`, Angular dev server on `:4200` with the `/api` proxy.
- Keep each app organized: separate controllers/services/domain (backend) and routes/components/domain/tests (frontend).
