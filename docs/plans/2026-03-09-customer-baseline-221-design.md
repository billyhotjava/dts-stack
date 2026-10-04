# Customer Baseline 2.2.1 Design

**Date:** 2026-03-09
**Status:** Approved

## Goal

Rebase the customer delivery line from `v2.2.3` to `2.2.1`, then continue feature work and hardening on top of the older `admin + platform + traditional governance / ETL` architecture that better matches the onsite environment.

## Why `2.2.1`

- `v2.2.2` already introduces a sizable batch of `AI copilot`, unified LLM, modern analytics, ingestion expansion, and related platform changes.
- `v2.2.3` adds another layer of deployment, MDM, and ops-governance hardening that is useful but still newer than the onsite baseline.
- `2.2.0` is likely too old and would force re-adding useful governance, ELT, and onsite-hardening work already present in `2.2.1`.

Therefore, `2.2.1` is the best compromise between onsite compatibility and keeping enough mature platform capability to move quickly.

## Scope

### In Scope

- Switch the working baseline to `2.2.1`
- Keep using the current workspace directory
- Create a customer-specific branch from `2.2.1`
- Build a new hardening backlog for:
  - bug fixes
  - UI polish
  - small onsite ops patches
- Continue implementation only inside the `2.2.1` architecture and information architecture

### Out of Scope

- Bringing back `v2.2.2+` AI copilot and unified LLM as a package
- Bringing back `v2.2.3` infra / MDM / ops-governance changes as a package
- Bringing in `v2.3+` semantic layer, agent architecture, auto-classification, or Decision Twins direction
- Reworking deployment around newer bootstrap / k8s-native delivery models

## Baseline Switch Strategy

1. Clean the current workspace state enough to switch safely.
2. Delete confirmed-useless tracked artifacts `0` and ` .Destination}}{{end}}`.
3. Remove leftover temporary directories that do not belong to the `2.2.1` customer line.
4. Switch from current `v2.2.3` branch state to `2.2.1`.
5. Create a customer branch from `2.2.1` in the same workspace to preserve the original baseline branch.

Recommended branch name:

- `customer/2.2.1`

## Delivery Tracks After Switch

### Track 1: Bug Hardening

Priority area:

- `platform development / modeling / ETL`

Typical problems:

- broken or inconsistent API behavior
- run-flow failures
- state mismatches
- page-path dead ends

### Track 2: UI Polish

Priority area:

- `platform governance`
- `platform access`
- `admin configuration / ops`

Typical problems:

- missing validation
- weak empty/loading/error states
- inconsistent status display
- incomplete form guidance
- page usability issues

### Track 3: Small Onsite Ops Patches

Only small, low-risk patches are allowed:

- environment compatibility
- path / config fixes
- targeted operational safeguards

These must not pull in later-version architecture.

## Selective Backport Rules

A higher-version fix may be reused only if all conditions are true:

1. it does not change the `2.2.1` architecture
2. it does not introduce `AI`, `semantic`, or new analytics product direction
3. it can be verified independently
4. it solves a real onsite issue
5. rollback impact is small and obvious

## Execution Order

1. Switch baseline to `2.2.1`
2. Run minimal baseline verification before any feature work
3. Create customer hardening backlog grouped as `BUG`, `UI`, `OPS`
4. Fix issues module-by-module in this order:
   - `platform development / modeling / ETL`
   - `platform governance / access`
   - `admin configuration / ops`
   - optional `BI / analytics` local issues only if required
5. Perform selective backports only after a local fix is judged worse than a small cherry-picked patch

## Acceptance Rules

- Each bug must include:
  - symptom
  - module
  - reproduction steps
  - verification after fix
- Each UI item must include:
  - affected page
  - expected behavior
  - build verification
  - manual walkthrough evidence
- Each ops patch must include:
  - exact runtime impact
  - explicit rollback path
- No batch architectural migrations are allowed under the name of bug fixing

## Verification Strategy

- Java modules: compile or module-level tests for touched modules
- Web modules: `build` first, then focused manual verification
- Integration smoke only for flows directly affected by the change
- Broader regression only at milestone closure, not after every single patch

## Risks

- Hidden dependency on newer fixes from `v2.2.2` / `v2.2.3`
- Dirty workspace state causing incorrect branch switch results
- UI polish requests accidentally expanding into product redesign
- Selective backports drifting into architecture backports

## Risk Controls

- Switch baseline before new feature work
- Keep changes small and module-scoped
- Require explicit justification for any higher-version reuse
- Keep a customer-only backlog separate from historic `v2.2.1` task boards
