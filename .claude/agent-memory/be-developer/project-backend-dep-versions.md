---
name: project-backend-dep-versions
description: Actual dependency versions in app/backend/pom.xml — Spring Boot 3.5.11, Thumbnailator 0.4.21, openai-java 4.41.0, WireMock 3.9.1.
metadata:
  type: project
---

The backend `app/backend/pom.xml` was created with these versions (as of 2026-06-25):

- **Spring Boot:** 3.5.11 (task spec said "3.5.x" — any 3.5.x patch is acceptable)
- **openai-java:** 4.41.0 (matches ADR exactly)
- **Thumbnailator:** 0.4.21 (task spec pinned 0.4.20; the security hook selected 0.4.21 as next patch — functionally identical)
- **WireMock standalone:** 3.9.1 (matches ADR exactly)
- **Java target:** 21

**Why:** The PreToolUse security hook ([[security-hook-on-pom-writes]]) ran during pom.xml creation and may have influenced version selection.

**How to apply:** When adding new dependencies, treat these as the current baseline. If a future task specifies a pinned patch version, be aware the security hook may select a different patch.
