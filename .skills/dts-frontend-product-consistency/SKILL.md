---
name: dts-frontend-product-consistency
description: Use when changing DTS frontend pages, routes, menus, permissions, layouts, tables, filters, SQL IDE, logical modeling UI, workbench, dashboards, admin console, platform console, or analytics modern UI to keep product behavior and visual patterns consistent.
---

# DTS Frontend Product Consistency

Use this skill for DTS frontend work.

## Workflow

1. Load `references/frontend-patterns.md`.
2. Identify the target webapp:
   - `source/dts-admin-webapp`
   - `source/dts-platform-webapp`
   - `source/dts-analytics-webapp/modern`
3. Inspect nearby pages/components before adding new UI.
4. Keep routes, menus, permission checks, empty states, loading states, and error states aligned.
5. Prefer existing service clients, state patterns, table/filter components, icons, and layout primitives.
6. Verify with build/typecheck and browser screenshots or E2E checks for user-facing workflow changes.

## Product Rules

- Operational DTS pages should be dense, quiet, and scannable.
- Avoid marketing-style landing pages inside the product shell.
- Do not hide backend authorization gaps with frontend-only checks.
- Use stable dimensions for grids, toolbars, tables, and fixed controls.
- Keep Chinese business terminology consistent with existing pages when editing visible text.
- Do not introduce a new visual language unless the target webapp already uses it.
