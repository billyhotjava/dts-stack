# Transform Detail Realtime Card Design

**Goal:** Remove customer-line UI noise by showing the ETL realtime status card only for `cdc` tasks.

## Context

`source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx` currently renders a `"实时状态（预留）"` card for every ingestion task. On the `2.2.1` customer line, the main onsite flow is still batch-oriented (`full_refresh` and `incremental`). Showing a reserved realtime panel on those tasks adds noise and suggests a capability that is not active for the customer baseline.

## Options

1. Only show the realtime card for `cdc` tasks.
   - Pros: matches current product capability boundary; fastest and cleanest customer-line UI.
   - Cons: non-`cdc` tasks no longer show the placeholder panel.
2. Keep the card for all tasks, but downgrade it to a lighter informational note.
   - Pros: keeps future capability visible.
   - Cons: still leaves customer-facing noise in the main detail page.

## Decision

Use option 1.

## Scope

- Gate realtime-status data loading by `task.syncMode === "cdc"`.
- Render the realtime-status card only when the current task is `cdc`.
- Leave incremental checkpoint behavior unchanged.
- Record the change in the customer hardening backlog as a UI polish item.

## Verification

- `pnpm -C source/dts-platform-webapp build`
- confirm the page still compiles with the conditional card logic
