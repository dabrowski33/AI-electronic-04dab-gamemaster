---
name: validator-timezone-fix
description: futureDateForbidden validator must normalize both dates to midnight, not just today
metadata:
  type: feedback
---

When implementing `futureDateForbidden`, comparing a date-only input (e.g., `new Date()` with time 14:10) against `today` set to midnight will falsely report the date as "in the future" because 14:10 > 00:00.

**Why:** `new Date(control.value)` preserves the time component. If the user picks "today", the picked date has the current clock time, which is > midnight today.

**How to apply:** Always call `picked.setHours(0, 0, 0, 0)` on the picked value as well as on `today` before the `>` comparison in any date-only validator.

```typescript
export function futureDateForbidden(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const picked = new Date(control.value);
  picked.setHours(0, 0, 0, 0);  // <-- required
  return picked > today ? { futureDate: true } : null;
}
```
