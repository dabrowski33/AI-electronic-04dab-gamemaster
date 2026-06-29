---
name: project-e2e-setup
description: Where the E2E suite lives and the one rule that was gotten wrong before
metadata:
  type: project
---

E2E suite is at `app/e2e/` (Playwright + TypeScript, Chromium headless).

**Key learning:** E2E runs against the **REAL** stack incl. a live LLM — **not** the `stub-llm` profile. An earlier run stubbed the LLM in E2E and hid real bugs. The authoritative E2E rules live in `app/e2e/AGENTS.md`; run/setup commands live in `app/README.md`. Don't restate their details here — read them.
