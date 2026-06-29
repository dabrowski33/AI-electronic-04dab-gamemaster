# API Contract — Hardware Service Decision Copilot PoC

> Status: **FROZEN** — Phase 0.4. Both BE and FE tracks derive their types from this document.
> Do not edit after M0 merge without coordinating both tracks.

---

## 1. Enums

### CaseType
```
REKLAMACJA   — complaint (rękojmia / gwarancja)
ZWROT        — return (prawo odstąpienia od umowy)
```

### EquipmentCategory (PRD §8)
```
SMARTFONY_I_TELEFONY       — Smartfony i telefony
LAPTOPY_I_KOMPUTERY        — Laptopy i komputery
TABLETY                    — Tablety
TELEWIZORY_I_MONITORY      — Telewizory i monitory
AUDIO                      — Audio (słuchawki, głośniki)
KONSOLE_I_GAMING           — Konsole i gaming
SMARTWATCHE_I_WEARABLES    — Smartwatche i wearables
APARATY_I_FOTOGRAFIA       — Aparaty i fotografia
MALE_AGD                   — Małe AGD
AKCESORIA                  — Akcesoria (ładowarki, kable, etui)
INNE                       — Inne
```

### DecisionCategory
```
ELIGIBLE              — Kwalifikuje się
NOT_ELIGIBLE          — Nie kwalifikuje się
NEEDS_HUMAN_REVIEW    — Wymaga weryfikacji przez konsultanta
MORE_INFO_REQUIRED    — Wymagane dodatkowe informacje
```

### ImageAnalysis — confidence
```
LOW
MEDIUM
HIGH
```

### ImageAnalysis — tristate flags (return scenario)
```
YES
NO
UNCERTAIN
```

### ImageAnalysis — likelyCause (complaint scenario)
```
MANUFACTURING_DEFECT
USER_CAUSED
NORMAL_WEAR
INCONCLUSIVE
```

---

## 2. DTO Shapes

### 2.1 Submit Case Request — `POST /api/v1/cases` (multipart/form-data)

| Field        | Type              | Constraints                                                          |
|---|---|---|
| type         | CaseType          | required                                                             |
| category     | EquipmentCategory | required                                                             |
| model        | string            | required, non-blank, max 120 chars                                   |
| purchaseDate | string (yyyy-MM-dd)| required, not in the future                                          |
| reason       | string            | required+non-blank when type=REKLAMACJA; optional when ZWROT; max 2000 |
| image        | file              | required; content-type image/jpeg, image/png, image/webp; ≤ 10 MB   |

### 2.2 Submit Case Response — `200 OK` (application/json)

```json
{
  "sessionId": "string (UUID)",
  "decision": {
    "category": "DecisionCategory",
    "justification": "string (Polish)",
    "nextSteps": "string (Polish)",
    "missingInfo": ["string"]
  },
  "firstMessage": "string (markdown, Polish — greeting + decision + justification + nextSteps + disclaimer)",
  "caseSummary": {
    "type": "CaseType",
    "category": "EquipmentCategory",
    "model": "string",
    "purchaseDate": "string (yyyy-MM-dd)"
  }
}
```

Note: `decision.missingInfo` is present only when `category = MORE_INFO_REQUIRED`. May be omitted or empty array otherwise.

### 2.3 Chat Message Request — `POST /api/v1/cases/{sessionId}/messages` (application/json)

```json
{
  "message": "string — required, non-blank, max 2000 chars"
}
```

### 2.4 Chat Message Response — `200 OK` (text/event-stream / SSE)

See §4 SSE Frame Format.

### 2.5 Case Summary Response — `GET /api/v1/cases/{sessionId}` (application/json)

```json
{
  "caseSummary": {
    "type": "CaseType",
    "category": "EquipmentCategory",
    "model": "string",
    "purchaseDate": "string (yyyy-MM-dd)"
  },
  "transcript": [
    {
      "role": "assistant | user",
      "content": "string (markdown)"
    }
  ]
}
```

### 2.6 ImageAnalysis (internal DTO — backend structured output from vision model)

```json
{
  "summary": "string — short description of what is visible",
  "observations": ["string"],
  "confidence": "LOW | MEDIUM | HIGH",

  "signsOfUse":      "YES | NO | UNCERTAIN",
  "visibleDamage":   "YES | NO | UNCERTAIN",
  "complete":        "YES | NO | UNCERTAIN",
  "resellableAsNew": "YES | NO | UNCERTAIN",

  "damageType":  "string",
  "likelyCause": "MANUFACTURING_DEFECT | USER_CAUSED | NORMAL_WEAR | INCONCLUSIVE"
}
```

Scenario-specific notes:
- **ZWROT (return)**: populate `signsOfUse`, `visibleDamage`, `complete`, `resellableAsNew`. Leave `damageType`/`likelyCause` null/absent.
- **REKLAMACJA (complaint)**: populate `visibleDamage`, `damageType`, `likelyCause`. Leave return-specific flags null/absent.

### 2.7 DecisionResult (internal DTO — backend structured output from decision model)

```json
{
  "category": "ELIGIBLE | NOT_ELIGIBLE | NEEDS_HUMAN_REVIEW | MORE_INFO_REQUIRED",
  "justification": "string (Polish)",
  "nextSteps": "string (Polish)",
  "missingInfo": ["string"]
}
```

`missingInfo` only populated for `MORE_INFO_REQUIRED`.

---

## 3. Error Model

All error responses use `application/json` with this shape:

```json
{
  "code": "string — machine code (see below)",
  "message": "string — safe, UI-displayable (Polish-safe)",
  "fields": {
    "fieldName": "error message"
  }
}
```

`fields` is only present for `VALIDATION_ERROR` (400).

### Error Codes & HTTP Statuses

| HTTP | code                    | When                                                                 |
|---|---|---|
| 400  | VALIDATION_ERROR        | Missing/blank required field, missing reason for REKLAMACJA, future purchaseDate, malformed date |
| 413  | PAYLOAD_TOO_LARGE       | Image file > 10 MB                                                   |
| 415  | UNSUPPORTED_MEDIA_TYPE  | Image content-type not image/jpeg, image/png, or image/webp          |
| 404  | SESSION_NOT_FOUND       | Unknown or expired sessionId                                         |
| 502  | LLM_UNAVAILABLE         | Upstream LLM failure after retries (or timeout)                      |
| 503  | LLM_UNAVAILABLE         | Upstream LLM temporary unavailable                                   |

---

## 4. SSE Frame Format

The `POST /api/v1/cases/{sessionId}/messages` endpoint returns `Content-Type: text/event-stream`.

### Normal token frame
```
data: <token text fragment>\n\n
```
One or more token frames are emitted per response.

### Terminal (completion) frame
```
event: done\ndata: \n\n
```
The client must close the stream after receiving this event.

### Error frame (mid-stream failure)
```
event: error\ndata: LLM_UNAVAILABLE\n\n
```
Emitted if the LLM call fails after the stream has opened. The client should show an inline error.

### Parsing rules for the frontend SSE parser
1. Read chunks from `ReadableStream` via `TextDecoder`.
2. Buffer incoming bytes; split on `\n\n` (double newline = frame boundary).
3. A single logical frame may span multiple network chunks — buffer until `\n\n` is found.
4. For each complete frame: check for `event:` line first, then `data:` line.
5. Strip `data: ` prefix to extract the token text.
6. On `event: done` → end stream, no further processing.
7. On `event: error` → surface the error, end stream.

### Example transcript (from `app/fixtures/sse-transcript.txt`)

```
data: Dziękujemy\n\n
data:  za\n\n
data:  zgłoszenie\n\n
data: .\n\n
event: done\ndata: \n\n
```

See `app/fixtures/sse-transcript.txt` for the complete test transcript including split-chunk and error variants.

---

## 5. Mandatory Disclaimer Text

The backend appends this disclaimer deterministically to every first message (never delegated to the model):

> „To wstępna, automatyczna ocena Twojego zgłoszenia, a nie wiążąca decyzja. Ostateczną decyzję podejmuje konsultant po weryfikacji zgłoszenia."

Tests must assert this exact substring is present in `firstMessage`.

---

## 6. Base Path

All API endpoints: `/api/v1`

Health: `GET /actuator/health` (Spring Boot Actuator)

The Angular dev server proxies `/api` → `http://localhost:8080` via `proxy.conf.json`.
