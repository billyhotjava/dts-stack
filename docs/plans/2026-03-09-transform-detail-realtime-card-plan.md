# Transform Detail Realtime Card Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show the ETL realtime status card only for `cdc` tasks in the customer `2.2.1` branch.

**Architecture:** Keep the change local to `TransformDetailPage`. Reuse the existing `syncMode` field to both suppress realtime-status fetching for non-`cdc` tasks and hide the reserved UI card from batch-oriented tasks. Record the change in the customer hardening backlog after verification.

**Tech Stack:** React 18, TypeScript, Ant Design, Vite

---

### Task 1: Scope the realtime card to `cdc`

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`

**Step 1: Establish the target behavior**

- Treat `task.syncMode === "cdc"` as the only case that should render realtime status UI.
- Keep non-`cdc` tasks limited to execution info and incremental checkpoints.

**Step 2: Write the minimal implementation**

- update the realtime-status `useEffect` so non-`cdc` tasks clear existing realtime data and do not call `getRealtimeStatus`
- add a local boolean guard for whether the current task is realtime-capable
- wrap the realtime-status card in that guard

**Step 3: Verify compilation**

Run: `pnpm -C source/dts-platform-webapp build`

Expected: `BUILD` succeeds without new TypeScript or Vite errors.

### Task 2: Record the customer hardening item

**Files:**
- Modify: `worklog/v2.2.1/customer-hardening/status-board.md`
- Modify: `worklog/v2.2.1/customer-hardening/baseline-findings.md`
- Create: `worklog/v2.2.1/customer-hardening/issues/UI-002.md`

**Step 1: Add the issue record**

- describe the current noisy behavior on batch tasks
- document the chosen `cdc`-only display rule

**Step 2: Update backlog summaries**

- add `UI-002` to the status board
- note the fix in baseline findings as the current next-wave UI polish item

**Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx worklog/v2.2.1/customer-hardening/status-board.md worklog/v2.2.1/customer-hardening/baseline-findings.md worklog/v2.2.1/customer-hardening/issues/UI-002.md docs/plans/2026-03-09-transform-detail-realtime-card-design.md docs/plans/2026-03-09-transform-detail-realtime-card-plan.md
git commit -m "fix: hide reserved realtime card for batch tasks"
```
