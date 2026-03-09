# Customer Baseline 2.2.1 Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Switch the customer development line from `v2.2.3` to `2.2.1`, establish a clean customer branch, verify the baseline, and create a hardening backlog for bug fixes and UI polish.

**Architecture:** The work stays inside the `2.2.1` product and deployment model. The current workspace is reused because the user explicitly rejected an extra worktree; isolation is provided by switching to `2.2.1` and creating a same-workspace customer branch. Hardening work is split into `BUG`, `UI`, and `OPS` tracks, with selective backports allowed only for small, verifiable patches.

**Tech Stack:** Git, Maven, pnpm, Java Spring services, React/Vite webapps, markdown worklog docs

---

### Task 1: Stabilize Current Workspace Before Branch Switch

**Files:**
- Modify: `0`
- Modify: ` .Destination}}{{end}}`
- Delete: `services/dts-dbt/models/erp/stg_erp_orders.sql`

**Step 1: Restore tracked deletions temporarily so branch switch is safe**

Run:

```bash
git checkout -- ' .Destination}}{{end}}' 0
```

Expected: both tracked files are restored into the working tree.

**Step 2: Remove the leftover untracked temp directory**

Run:

```bash
rm -rf services/dts-dbt/models/erp
```

Expected: `services/dts-dbt/models/erp` no longer exists.

**Step 3: Verify the workspace is clean enough to switch baselines**

Run:

```bash
git status --short --branch
```

Expected: no remaining unexpected deletions or untracked temporary directories that would block branch switching.

**Step 4: Commit**

No commit in this task. This task is only for preparing a safe baseline switch.

### Task 2: Switch to `2.2.1` and Create Customer Branch

**Files:**
- Modify: `0`
- Modify: ` .Destination}}{{end}}`

**Step 1: Switch the workspace to the `2.2.1` baseline**

Run:

```bash
git switch 2.2.1
```

Expected: current branch becomes `2.2.1`.

**Step 2: Create the customer branch in the same workspace**

Run:

```bash
git switch -c customer/2.2.1
```

Expected: current branch becomes `customer/2.2.1`.

**Step 3: Delete the two confirmed-useless tracked files on the customer branch**

Run:

```bash
git rm --force -- ' .Destination}}{{end}}' 0
```

Expected: both files are staged for deletion on `customer/2.2.1`.

**Step 4: Verify branch and deletion state**

Run:

```bash
git status --short --branch
```

Expected: branch is `customer/2.2.1`; only the intentional deletions are present.

**Step 5: Commit**

Run:

```bash
git commit -m "chore: start customer 2.2.1 baseline"
```

Expected: one commit capturing the customer baseline branch start and removal of the two useless files.

### Task 3: Create Customer Hardening Backlog Skeleton

**Files:**
- Create: `worklog/v2.2.1/customer-hardening/README.md`
- Create: `worklog/v2.2.1/customer-hardening/status-board.md`
- Create: `worklog/v2.2.1/customer-hardening/baseline-findings.md`

**Step 1: Create a backlog readme for the customer line**

Add a markdown file that records:

```md
# Customer Hardening Backlog (v2.2.1)

## Tracks
- BUG
- UI
- OPS

## Priority Order
1. platform development / modeling / ETL
2. platform governance / access
3. admin configuration / ops
4. optional BI local fixes only
```

**Step 2: Create a status board**

Add a markdown table like:

```md
# Status Board

| ID | Type | Module | Summary | Status | Notes |
|---|---|---|---|---|---|
```

**Step 3: Create a baseline findings document**

Add a markdown template like:

```md
# Baseline Findings

## Build Findings

## Runtime Findings

## UI Findings

## Candidate First-Wave Fixes
```

**Step 4: Verify files exist and are readable**

Run:

```bash
sed -n '1,80p' worklog/v2.2.1/customer-hardening/README.md
sed -n '1,80p' worklog/v2.2.1/customer-hardening/status-board.md
sed -n '1,80p' worklog/v2.2.1/customer-hardening/baseline-findings.md
```

Expected: all three files render the intended structure.

**Step 5: Commit**

Run:

```bash
git add worklog/v2.2.1/customer-hardening
git commit -m "docs: add customer 2.2.1 hardening backlog"
```

Expected: one commit with the backlog scaffold only.

### Task 4: Run Minimal `2.2.1` Baseline Verification

**Files:**
- Modify: `worklog/v2.2.1/customer-hardening/baseline-findings.md`

**Step 1: Verify `admin` backend compilation**

Run:

```bash
mvn -f source/dts-admin/pom.xml -DskipTests compile
```

Expected: Maven compile succeeds with exit code `0`.

**Step 2: Verify `platform` backend compilation**

Run:

```bash
mvn -f source/dts-platform/pom.xml -DskipTests compile
```

Expected: Maven compile succeeds with exit code `0`.

**Step 3: Verify `admin-webapp` build**

Run:

```bash
pnpm -C source/dts-admin-webapp build
```

Expected: production build succeeds with exit code `0`.

**Step 4: Verify `platform-webapp` build**

Run:

```bash
pnpm -C source/dts-platform-webapp build
```

Expected: production build succeeds with exit code `0`.

**Step 5: Record fresh results in the findings document**

Append sections like:

```md
## Build Findings
- dts-admin: PASS / FAIL
- dts-platform: PASS / FAIL
- dts-admin-webapp: PASS / FAIL
- dts-platform-webapp: PASS / FAIL

## Candidate First-Wave Fixes
- BUG-001 ...
- UI-001 ...
```

**Step 6: Commit**

Run:

```bash
git add worklog/v2.2.1/customer-hardening/baseline-findings.md
git commit -m "docs: record customer 2.2.1 baseline verification"
```

Expected: one commit with baseline verification evidence.

### Task 5: Select and Define First-Wave Fixes

**Files:**
- Modify: `worklog/v2.2.1/customer-hardening/status-board.md`
- Create: `worklog/v2.2.1/customer-hardening/issues/BUG-001.md`
- Create: `worklog/v2.2.1/customer-hardening/issues/UI-001.md`

**Step 1: Pick the highest-priority functional bug from baseline verification**

Create `BUG-001.md` using this structure:

```md
# BUG-001

## Module

## Symptom

## Reproduction

## Expected Behavior

## Candidate Fix Area

## Verification Command
```

**Step 2: Pick the highest-priority UI polish item**

Create `UI-001.md` using this structure:

```md
# UI-001

## Module

## Affected Page

## Current Behavior

## Expected Behavior

## Candidate Fix Area

## Verification Method
```

**Step 3: Register both items on the status board**

Append rows like:

```md
| BUG-001 | BUG | platform | short summary | planned | first-wave fix |
| UI-001 | UI | admin/platform | short summary | planned | first-wave polish |
```

**Step 4: Verify the backlog is actionable**

Run:

```bash
sed -n '1,120p' worklog/v2.2.1/customer-hardening/status-board.md
sed -n '1,160p' worklog/v2.2.1/customer-hardening/issues/BUG-001.md
sed -n '1,160p' worklog/v2.2.1/customer-hardening/issues/UI-001.md
```

Expected: first-wave tasks are specific enough to implement next without re-triage.

**Step 5: Commit**

Run:

```bash
git add worklog/v2.2.1/customer-hardening/status-board.md worklog/v2.2.1/customer-hardening/issues
git commit -m "docs: define first-wave customer hardening fixes"
```

Expected: one commit that freezes the first execution batch.
