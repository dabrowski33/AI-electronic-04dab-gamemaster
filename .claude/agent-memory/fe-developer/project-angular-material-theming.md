---
name: project-angular-material-theming
description: Angular Material 18 uses m2-prefixed theming functions (m2-define-palette, m2-define-light-theme, m2-define-typography-config, $m2-indigo-palette). Old names without prefix don't exist.
metadata:
  type: project
---

Angular Material 18 renamed theming functions to use the `m2-` prefix:

- `mat.define-palette(...)` → `mat.m2-define-palette(...)`
- `mat.define-light-theme(...)` → `mat.m2-define-light-theme(...)`
- `mat.define-typography-config(...)` → `mat.m2-define-typography-config(...)`
- `mat.$indigo-palette` → `mat.$m2-indigo-palette`
- `mat.$amber-palette` → `mat.$m2-amber-palette`

**Why:** Breaking change introduced to support M3 theme system alongside M2. The old un-prefixed names were removed.

**How to apply:** Any time you write SCSS theming for Angular Material 18+, always use the `m2-` prefix for all M2 theme functions and palette variables.
