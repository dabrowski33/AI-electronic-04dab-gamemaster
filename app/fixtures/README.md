# Test Fixtures

Shared fixtures used by both BE (WireMock / integration tests) and FE (unit tests) tracks.

## Files

### SSE Transcripts
- `sse-transcript.txt` — complete normal SSE stream (tokens + `done` event)
- `sse-transcript-split-chunk.txt` — partial chunk (frame split across network chunks)

### ImageAnalysis fixtures (JSON — structured output of the vision model)
Format: `{scenario}-{decision-category}-image-analysis.json`
- `zwrot-eligible-image-analysis.json`
- `zwrot-not-eligible-image-analysis.json`
- `zwrot-needs-human-review-image-analysis.json`
- `zwrot-more-info-required-image-analysis.json`
- `reklamacja-eligible-image-analysis.json`
- `reklamacja-not-eligible-image-analysis.json`
- `reklamacja-needs-human-review-image-analysis.json`
- `reklamacja-more-info-required-image-analysis.json`

### DecisionResult fixtures (JSON — structured output of the decision model)
Format: `{scenario}-{decision-category}-decision-result.json`
- (same naming as above, `-decision-result.json` suffix)

### Large image generator
- `generate-large-image.sh` — generates `large-image-11mb.jpg` for 413 PAYLOAD_TOO_LARGE tests.
  Run once: `bash generate-large-image.sh`

## Usage in BE tests (WireMock)
Load fixture JSON as the body of stubbed OpenRouter `/chat/completions` responses.

## Usage in FE tests (Angular)
Import fixture JSON directly into `*.spec.ts` files; pass to mock API service responses.
