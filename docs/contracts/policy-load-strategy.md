# Policy Load Strategy (Phase 0.6)

## Decision: Copy into `backend/src/main/resources`

**Chosen path:** Copy both policy documents into `app/backend/src/main/resources/policies/` at scaffold time.

| Path in repo | Path in classpath |
|---|---|
| `docs/policies/polityka-zwrotow.md` | `classpath:policies/polityka-zwrotow.md` |
| `docs/policies/polityka-reklamacji.md` | `classpath:policies/polityka-reklamacji.md` |

### Rationale
- Classpath loading is simpler and deployment-agnostic (no filesystem path assumptions).
- `PolicyProvider` uses `ClassPathResource` / `@Value("classpath:...")` — loads both at startup.
- Fast read access (once per process lifetime, then cached in a field).

### Trade-off
- `docs/policies/` is the single source of truth; the copies under `src/main/resources/` must be kept in sync manually if the policy docs change.
- Mitigation: document this in the `app/backend/src/main/resources/policies/` directory with a `README.md`.
- If policies need hot-reload or live editing, the backend can be changed to read from a configurable filesystem path via `@Value("${app.policy.path}")`.

### Rejected alternative: read from repo path at runtime
- Would require `../../../docs/policies/` relative path — breaks when deployed outside the monorepo structure.

### .gitignore additions
No new ignores needed. The policy copies in `src/main/resources/` are committed.

### Reminder for agents
- **be-developer**: copy the policy `.md` files into `app/backend/src/main/resources/policies/` as part of step 0.1 or when first implementing `PolicyProvider` (Phase 1.3). Do not modify `docs/policies/`.
- **orchestrator rule**: `docs/policies/` and all `docs/**` files are owned by the orchestrator; dev worktrees must not edit them.
