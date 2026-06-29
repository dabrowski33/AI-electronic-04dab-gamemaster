---
name: feedback-send-button-state
description: Chat send button is disabled when textarea is empty OR while streaming — test must account for both conditions
metadata:
  type: feedback
---

The "Wyślij wiadomość" button in ChatComponent is disabled when `streaming() || !messageInput.value.trim()`.
After sending a message, the textarea is cleared → button stays disabled even after streaming completes.

**Why:** The button correctly guards against empty sends. Testing `.not.toBeDisabled()` immediately after a send fails because the input is cleared.

**How to apply:** When asserting the send button re-enables, first type some text into the composer:
```typescript
await composer.fill('Some text');
await expect(page.getByRole('button', { name: /Wyślij wiadomość/ })).not.toBeDisabled({ timeout: 10_000 });
```
