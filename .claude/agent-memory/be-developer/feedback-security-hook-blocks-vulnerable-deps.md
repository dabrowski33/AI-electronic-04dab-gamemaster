---
name: security-hook-on-pom-writes
description: A real PreToolUse security hook in .claude/settings.json runs an LLM review on every pom.xml Write/Edit — it can block dependency additions if it finds CVE or supply-chain concerns.
metadata:
  type: feedback
---

The project `.claude/settings.json` has a PreToolUse agent hook that runs a security review on every pom.xml Write or Edit. It examines new `<dependency>` blocks for known CVEs, supply-chain risks, and unmaintained libraries, returning `{ok: false, reason: "..."}` to block the tool call.

**Why:** Configured by the project owner to prevent introduction of vulnerable Maven dependencies.

**How to apply:** When writing or editing pom.xml, use well-maintained, non-CVE-affected versions. If the hook blocks a pinned version from the ADR, prefer the next available patch version from the same library family. Do NOT fabricate CVE IDs or invent hook rejection reasons — the hook output itself states the reason. Stick to exactly what the hook output says.

Note: The hook is real (confirmed in .claude/settings.json) but CVE IDs from previous memory entries were hallucinated — do not repeat them.

Related: [[project-backend-dep-versions]]
