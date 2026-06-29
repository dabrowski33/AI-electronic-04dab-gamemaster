---
name: frontend-scaffold-complete
description: Angular 18 frontend Phase 2 implementation complete as of 2026-06-25
metadata:
  type: project
---

Phase 2 of the Hardware Service Decision Copilot frontend was implemented on 2026-06-25.

Files created:
- `src/app/core/models/index.ts` — TypeScript types mirroring API contract (CaseType, EquipmentCategory, DecisionCategory, DTOs)
- `src/app/shared/validators/index.ts` — futureDateForbidden, fileSizeMax, fileTypeAllowed
- `src/app/core/sse-parser.ts` — async generator reading SSE from ReadableStream
- `src/app/core/api.service.ts` — submitCase (HttpClient POST multipart) + streamChat (fetch + SSE)
- `src/app/features/intake/` — full Reactive Form with mat-datepicker, mat-select, file upload + preview
- `src/app/features/chat/` — signal-based streaming chat with ngx-markdown, decision badge, auto-scroll

**Why:** Course project for NBP, building live during course sessions.

**How to apply:** Next phase likely adds E2E tests (Playwright) and connects to the real backend. The intake form posts to `/api/v1/cases` and the chat streams from `/api/v1/cases/:sessionId/messages`.

Key constraints remembered:
- Angular component style budget was increased to `4kB` warning / `8kB` error in `angular.json` (from 2kB) because chat styles legitimately need the space.
- IntakeComponent uses `valueChanges` subscription (not `effect()`) for the dynamic reason validator because FormControls are not Angular signals.
- ChatComponent `effect()` reads `this.messages()` directly (no intermediate variable) to avoid the lint `no-unused-vars` error.
- `catch (err)` in `sendMessage` was changed to `catch` (no variable) since the error is not used.
