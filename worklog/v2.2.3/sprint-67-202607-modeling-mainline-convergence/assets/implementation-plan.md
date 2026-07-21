# Sprint-67 Modeling Mainline Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove business objects from the public and canonical modeling path while preserving business categories, direct dimension/four-table modeling, migration safety, and downstream model lifecycle evidence.

**Architecture:** Keep WarehousePlan as the planning aggregate and ModelSpec as the only model source of truth. Introduce contract changes through expand-migrate-contract, switch UI/menu/routes only after target APIs work, and retain legacy reads solely for audited migration until zero consumers.

**Tech Stack:** Java 21/Spring Boot/JdbcTemplate/Liquibase, React/TypeScript/Vite/Ant Design, PostgreSQL, pnpm/tsx/Playwright, Docker Compose, GitNexus.

## Global Constraints

- Do not create a replacement business-object or semantic-object entity.
- UI customer language is limited to the six canonical object groups in Sprint-67 F1.
- New write flows use planId/domainId/modelSpecId/revision and never require objectId.
- Business activity is optional FACT context and is not a global model gate.
- Database changes are forward-only Liquibase expand-migrate-contract changesets.
- Chrome 95 is a release target.
- Every code task starts with failing tests and ends with scoped verification and a Conventional Commit.
- Before modifying a symbol, run GitNexus upstream impact; before commit, run GitNexus detect_changes.

---

## 1. File responsibility map

| Area | Primary files/modules | Responsibility after Sprint-67 |
|---|---|---|
| Product contracts | `ModelingVNextContract.java`, target ModelSpec schema, `modelingVnextContract.ts` | ModelSpec v2 and type-specific validation |
| Planning | WarehousePlan contract/application/resource/stage projection | plan/category/policy/source and one next action |
| Catalog category | SubjectAreasPage and catalog domain APIs | single business-category truth |
| Dimension/model UI | new focused pages under `pages/modeling/` | DIMENSION catalog and four-table ModelSpec editor |
| Implementation lifecycle | modeling services/resources, SQL/dbt pages | artifact/run/review/publish/lineage tied to model revision |
| Menu/routes | dts-admin menu JSON, static/dynamic routes | stable IA and compatibility redirects |
| Metrics | MetricWorkbench, dts-metrics contracts | model/field anchored metrics |
| Migration | new Liquibase changesets and migration service/tests | audited legacy object migration and retirement |
| Verification | existing source-contract/e2e plus Sprint-67 evidence | prevent old object chain regression |

## 2. Execution waves

### Wave 1: Freeze product and domain contracts

- [x] Execute F1-T01: add failing customer-language/menu/page contract tests; implement the six-object mapping; verify retired-term audit.
- [x] Execute F1-T02: add migration-classification fixtures and ModelSpec-without-object contract tests; implement the field disposition contract.
- [x] Execute F1-T03: add modelType × domain/activity validation tests; implement domainId and optional activity boundary.

**Exit:** G1 passes; no implementation team may introduce a different object term or process gate.

### Wave 2: Make planning and ModelSpec targets real

- [x] Execute F2-T01: idempotent dual-start plan creation, authenticated actor, atomic initial sources and exact-plan recovery.
- [x] Execute F2-T02, then F2-T03-A source inventory. Do not begin F2-T03-B candidate confirmation until F3-T01 is green.
- [x] Execute F3-T01 objectless contract, then F2-T03-B candidate confirmation and F3-T02/T03; run DIMENSION and FACT work in parallel only after ModelSpec v2 tests pass.
  - F3-T01 uses a new canonical v2 DTO/service/resource over the existing `modeling_model_spec` table; it does not mutate the legacy vNext record in place and does not create a second model table.
  - The existing dbt compiler is a CRITICAL-impact boundary. v2 reaches it through a projection adapter after contract/persistence gates pass; no direct compiler rewrite belongs in the expand commit.
- [x] Execute F2-T04 after F2-T03-A and the canonical ModelSpec gates can provide truthful evidence; end with StageProjection action-path tests.
- [x] Execute F3-T04 after FACT dependency shape is stable; execute F3-T05 after all four type validators exist.

**Exit:** G2 passes; an API client can create each model type without objectId and receive stage-specific gates.

### Wave 3: Switch visible product surfaces

- [x] Execute F4-T01 menu/role dry-run before changing default targets.
- [x] Execute F4-T02 route table and compatibility redirects; prove no redirect loops and no new objectId URL writes.
- [x] Execute F4-T03/T04 page states, single primary actions, customer copy, Chrome 95 desktop/narrow checks.

**Exit:** G3 passes; default users no longer enter business-object UI.

### Wave 4: Migrate and freeze legacy paths

- [x] Execute F5-T01 read-only dry-run twice and record conflicts for controlled classification.
- [x] Execute F5-T02 write freeze/read adapter and observe callers before any contract/drop.
- [x] Execute F5-T03 expand then tenant-batched migrate; compare checksums; keep contract/drop closed because its explicit exit gate is `NO-DROP`.
- [x] Execute F5-T04 per module, removing canonical write consumers while preserving audited read compatibility.

**Exit:** G4 passes; new writes are zero, target references reconcile, and physical-retirement status is honestly reported.

### Wave 5: Close downstream loop and release

- [x] Execute F6-T01 four-standard owner surface (including measurement units), standard/metric handoffs and drift tests.
- [x] Execute F6-T02 artifact/run/review/publish/lineage lifecycle tests and failure-repair flow.
- [x] Execute F6-T03 three browser journeys on real Chrome 95 with API/DB evidence IDs; validate permissions, auto-map, tenant isolation and failure recovery at their executable security/integration/UI boundaries.
- [x] Execute F6-T04 final verification, rollback drill, GitNexus scope review, queue/status update and Go/No-Go.

**Exit:** G5 passes and the Sprint may move from READY/IN_PROGRESS to DONE.

## 3. Required verification commands

Implementation owners must resolve exact test selectors from current package scripts before editing and record them in the Task evidence. Minimum module commands are:

```bash
cd source/dts-platform && npm run backend:unit:test
cd source/dts-platform-webapp && pnpm build
cd source/dts-admin && npm run backend:unit:test
cd source/dts-metrics && mvn -q test
cd source/dts-metrics-webapp && pnpm build
git diff --check
```

Expected result for each shell command is exit code 0. Before every symbol edit, the implementation owner must use GitNexus `impact` and record the blast radius. Before committing, the owner must use the GitNexus `detect_changes` tool to confirm that only the intended symbols and execution flows changed, and attach the result to the Task evidence. A narrower failing-test command is required during implementation before each minimal code change; the Task evidence must include the initial expected failure and final pass.

## 4. Commit boundaries

Each Sprint Task is one reviewable commit unless its migration expand/migrate/contract steps require separate reversible commits. Commit examples:

```text
feat(F3/T01): add objectless ModelSpec v2 contract
feat(F4/T02): converge modeling routes and plan context
fix(F5/T03): preserve legacy join mappings during migration
test(F6/T03): cover objectless modeling journeys in Chrome 95
```

Do not combine unrelated Sprint-66 BI work, user-owned AGENTS/CLAUDE edits, or physical-drop approval with a feature implementation commit.
