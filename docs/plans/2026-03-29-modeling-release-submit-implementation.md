# Modeling Release Submit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current multi-request modeling release flow with one backend-driven `release/submit` flow that returns quickly and handles blockers/warnings/submission in a single contract.

**Architecture:** Add a backend orchestration service and one new REST endpoint, then simplify the modeling page to call that endpoint once and only re-submit when the backend explicitly returns warnings requiring confirmation.

**Tech Stack:** Spring Boot, JUnit 5, React, TypeScript, Ant Design

---

### Task 1: Lock backend release-submit contract with tests

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtReleaseSubmissionServiceTest.java`

**Steps:**
1. Write a failing service test for `WARNING` when gates warn and `confirmWarnings=false`.
2. Run the targeted test and verify it fails because the service does not exist yet.
3. Write a failing service test for `BLOCKED` when DAG is not ready.
4. Write a failing service test for `SUBMITTED` when `confirmWarnings=true`.
5. Add/adjust resource tests to cover the new endpoint shape.

### Task 2: Implement backend unified release submission service

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtReleaseSubmissionService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`

**Steps:**
1. Implement minimal service types: request, result, status enum/record.
2. Add quick DAG lookup helper that does not wait.
3. Reuse `DbtQualityGateService` and `DbtReleaseGateService` inside the service.
4. Only trigger Airflow after blockers are empty and warnings are confirmed.
5. Expose `POST /etl/dbt/release/submit`.
6. Run targeted backend tests and keep iterating until green.

### Task 3: Lock frontend contract with tests

**Files:**
- Create: `source/dts-platform-webapp/src/api/platformApi.source-contract.test.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelReleaseSubmit.helpers.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelReleaseSubmit.helpers.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModeling.types.ts`

**Steps:**
1. Write a failing source-contract test for `submitDbtRelease`.
2. Write helper tests for mapping backend result to UI branches (`BLOCKED`, `WARNING`, `SUBMITTED`).
3. Run tests and verify failure before implementation.

### Task 4: Implement frontend unified release submission flow

**Files:**
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModeling.types.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelReleaseSubmit.helpers.ts`

**Steps:**
1. Add `submitDbtRelease` API wrapper with modeling timeout.
2. Add frontend types for the new response payload.
3. Replace `submitRun`’s serial gate calls with one `submitDbtRelease`.
4. Keep the warning-confirm flow, but re-submit through the same API with `confirmWarnings=true`.
5. Remove now-unused imports from the release submit path.
6. Run targeted frontend tests.

### Task 5: Verification

**Files:**
- No code changes required

**Steps:**
1. Run `./mvnw -q -Dtest=EtlResourceTest,DbtReleaseSubmissionServiceTest test` in `source/dts-platform`.
2. Run `./mvnw -q -DskipTests compile` in `source/dts-platform`.
3. Run `node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/api/platformApi.source-contract.test.ts src/pages/modeling/sqlModelReleaseSubmit.helpers.test.ts src/api/modelingRequestTimeout.test.ts` in `source/dts-platform-webapp`.
4. If full `pnpm build` is still blocked by unrelated existing issues, record the exact failure and do not misreport it as this change’s regression.
