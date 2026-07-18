# Sprint-65a WarehousePlan Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有过程/分层粒度的 `modeling_warehouse_plan` 原地扩展为 canonical 方案级聚合，交付统一领域契约、五编辑单元乐观锁、双起点共用基线 API 和九站 StageProjection 后端。

**Architecture:** 保留现有表和 `ModelSpec.plan_id` 外键，不创建第三张计划主表。新增聚焦的 `warehouse` 领域/应用包承载新契约；现有 `/api/modeling/vnext/plans` 在本期末改为兼容适配器。计划头、业务范围、来源、来源映射和策略分别使用独立 ETag，所有仓储查询继续带服务端 tenantId 作用域。

**Tech Stack:** Java 21、Spring Boot/JdbcTemplate、PostgreSQL、Liquibase、JUnit 5、AssertJ、MockMvc。

**Sprint 归属:** [Sprint-65：经典数仓规划内核与黄金主线重构](../README.md)

## Global Constraints

- `modeling_warehouse_plan` 是唯一 canonical 运行态主表；禁止新增第三张计划主表。
- `tenantId` 由服务端注入，不接受 UI 请求体覆盖；所有查询、唯一键和引用校验仍包含 tenantId。
- 九站 `StageProjection` 是唯一完成状态源；URL、session 和页面访问不能写入完成状态。
- BUSINESS_FIRST 与 ASSET_FIRST 只影响首次编辑区，共用同一聚合、基线和状态机。
- 所有新行为严格执行 RED -> GREEN -> REFACTOR；未观察到预期失败前不得写生产代码。
- 不修改 Sprint-66、`AGENTS.md`、`CLAUDE.md` 或其他用户改动；不 commit/push，除非用户另行授权。

---

### Task 1: Canonical schema and migration contract

**Files:**
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanCanonicalLiquibaseTest.java`
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260718_01_warehouse_plan_canonical.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`

**Interfaces:**
- Consumes: existing `modeling_warehouse_plan.id` and `modeling_model_spec.plan_id` foreign key.
- Produces: plan-head columns, five edit-unit versions, binding/policy/version/review/evidence/legacy-mapping tables used by Tasks 3-6.

- [ ] **Step 1: Write the failing Liquibase structure test**

Create a test that loads `20260718_01_warehouse_plan_canonical.xml` and asserts:

```java
assertThat(master).contains("20260718_01_warehouse_plan_canonical.xml");
assertThat(xml).contains("name=\"code\"")
    .contains("name=\"onboarding_mode\"")
    .contains("name=\"lifecycle_status\"")
    .contains("name=\"business_scope_version\"")
    .contains("name=\"sources_version\"")
    .contains("name=\"source_mappings_version\"")
    .contains("name=\"policy_version\"");
for (String table : List.of(
    "modeling_warehouse_plan_domain",
    "modeling_warehouse_plan_process",
    "modeling_warehouse_plan_source",
    "modeling_warehouse_plan_source_mapping",
    "modeling_warehouse_plan_metric_need",
    "modeling_warehouse_plan_policy",
    "modeling_warehouse_plan_version",
    "modeling_warehouse_plan_review",
    "modeling_warehouse_plan_stage_evidence",
    "modeling_legacy_plan_mapping"
)) assertThat(xml).contains("tableName=\"" + table + "\"");
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd source/dts-platform
./mvnw -ntp -Dtest=WarehousePlanCanonicalLiquibaseTest test
```

Expected: FAIL because the changelog and master include do not exist.

- [ ] **Step 3: Add the forward-only canonical expansion**

The migration must:

```text
1. Add nullable canonical columns and five version columns with default 1.
2. Backfill code/name/onboarding_mode/lifecycle_status for every legacy row.
3. Add tenant_id + code uniqueness after backfill.
4. Drop uk_modeling_warehouse_plan_process_layer.
5. Relax legacy process_id/layer/modeling_mode NOT NULL constraints.
6. Create the ten child/evidence/mapping tables with tenant-scoped indexes and FKs.
7. Keep legacy columns; rollback drops only the new objects/columns and restores the old constraint only when safe.
```

- [ ] **Step 4: Verify GREEN**

Run the Task 1 test again, then:

```bash
./mvnw -ntp -Dtest=ModelingVNextLiquibaseTest,WarehousePlanCanonicalLiquibaseTest test
```

Expected: both test classes PASS.

---

### Task 2: Pure WarehousePlan domain contract

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanContract.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanContractTest.java`

**Interfaces:**
- Produces: `OnboardingMode`, `LifecycleStatus`, `EditUnit`, `WarehousePlanHeader`, `BusinessScope`, `SourceBinding`, `SourceBusinessMapping`, `PlanningPolicy`, `PlanningBaseline`, `DomainIssue` and `validateRequestedTenant(String)`.
- Consumed by: Tasks 3-6.

- [ ] **Step 1: Write failing lifecycle and baseline tests**

Required examples:

```java
assertThat(WarehousePlanContract.validateCreate(validBusinessFirst())).isEmpty();
assertThat(WarehousePlanContract.validateRequestedTenant("request-value"))
    .extracting(DomainIssue::code).contains("WAREHOUSE_PLAN_TENANT_NOT_ACCEPTED");
assertThat(WarehousePlanContract.evaluateBaseline(scopeReady, sourcesReady, mappingsReady, policyReady).ready())
    .isTrue();
assertThat(WarehousePlanContract.canTransition(DRAFT, BASELINE_READY)).isTrue();
assertThat(WarehousePlanContract.canTransition(PUBLISHED, DRAFT)).isFalse();
```

- [ ] **Step 2: Verify RED**

Run `./mvnw -ntp -Dtest=WarehousePlanContractTest test` and confirm compilation fails because the contract is missing.

- [ ] **Step 3: Implement the minimum pure contract**

Use immutable records and enums. `PlanningBaseline` computes readiness from confirmed facts and returns stable missing codes; it must not depend on Spring, JDBC or industry fixtures.

- [ ] **Step 4: Verify GREEN and refactor names only**

Run `WarehousePlanContractTest`; keep the domain free of `PJM`, `project`, e-commerce or customer-specific fields.

---

### Task 3: Plan-head persistence and tenant-scoped CRUD

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanApplicationService.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanApplicationServiceIT.java`

**Interfaces:**
- Consumes: `WarehousePlanContract.WarehousePlanHeader` and Task 1 schema.
- Produces:

```java
WarehousePlanHeader create(String serverTenantId, CreateWarehousePlanCommand command);
WarehousePlanHeader get(String serverTenantId, UUID planId);
List<WarehousePlanHeader> list(String serverTenantId, String lifecycleStatus);
WarehousePlanHeader updateHeader(String serverTenantId, UUID planId, int expectedVersion, UpdatePlanHeaderCommand command);
WarehousePlanHeader archive(String serverTenantId, UUID planId, int expectedVersion);
```

- [ ] **Step 1: Write failing PostgreSQL integration tests**

Cover tenant-scoped duplicate code, cross-tenant invisibility, server-owned tenantId, header conflict 409-domain-code semantics, and archive preserving the row.

- [ ] **Step 2: Verify RED**

Run `./mvnw -ntp -Dtest=WarehousePlanApplicationServiceIT test`; expected failure is the missing service/API, not database startup.

- [ ] **Step 3: Implement minimal JdbcTemplate persistence**

All SQL includes `tenant_id = ?`. Update uses `where id = ? and tenant_id = ? and version = ?`; zero updated rows is resolved into not-found versus `WAREHOUSE_PLAN_VERSION_CONFLICT` without overwriting.

- [ ] **Step 4: Verify GREEN**

Run the integration test and `ModelingVNextApplicationServiceIT` to prove existing model-plan references remain valid.

---

### Task 4: Five edit units and unified baseline

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanApplicationService.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanApplicationServiceIT.java`

**Interfaces:**
- Produces:

```java
Versioned<BusinessScope> saveBusinessScope(String tenantId, UUID planId, int expectedVersion, BusinessScope value);
Versioned<List<SourceBinding>> saveSources(String tenantId, UUID planId, int expectedVersion, List<SourceBinding> value);
Versioned<List<SourceBusinessMapping>> saveSourceMappings(String tenantId, UUID planId, int expectedVersion, List<SourceBusinessMapping> value);
Versioned<PlanningPolicy> savePolicy(String tenantId, UUID planId, int expectedVersion, PlanningPolicy value);
PlanningBaseline getBaseline(String tenantId, UUID planId);
PlanningBaseline confirmBaseline(String tenantId, UUID planId, int expectedPlanHeadVersion);
```

- [ ] **Step 1: Add RED tests for concurrency and confirmation**

Prove: same edit unit stale version fails; business-scope and sources can update from their own version 1 independently; confirm fails with stable missing codes; confirmed baseline transitions plan to `BASELINE_READY` atomically.

- [ ] **Step 2: Verify RED**

Run the Task 4 test methods and observe missing-method failures.

- [ ] **Step 3: Implement one transaction per command**

Replace each child collection only after its version CAS succeeds. Confirm locks/checks plan-head and rereads all child versions in the same transaction.

- [ ] **Step 4: Verify GREEN**

Run the full `WarehousePlanApplicationServiceIT` class.

---

### Task 5: Canonical REST resource and ETag contract

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/WarehousePlanResource.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/WarehousePlanResourceTest.java`

**Interfaces:**
- Route root: `/api/modeling/warehouse-plans`.
- Tenant: resolved by server-side provider/default configuration; request body cannot set it.
- Concurrency: response `ETag`; write request `If-Match`; stable 404/409 error codes.

- [ ] **Step 1: Write RED MockMvc tests**

Cover create/list/detail/update/archive and all four baseline edit endpoints. Assert `If-Match` is required for updates, ETag is returned, request `tenantId` is ignored/rejected, and conflict response contains `WAREHOUSE_PLAN_VERSION_CONFLICT` plus current ETag.

- [ ] **Step 2: Verify RED**

Run `./mvnw -ntp -Dtest=WarehousePlanResourceTest test`; expected failure is missing resource/routes.

- [ ] **Step 3: Implement the resource without duplicating domain rules**

The resource parses headers and delegates; validation and state changes stay in `WarehousePlanApplicationService`/contract.

- [ ] **Step 4: Verify GREEN**

Run `WarehousePlanResourceTest,ModelingVNextResourceTest,ModelingVNextResourceContractTest`.

---

### Task 6: Nine-stage StageProjection and vNext compatibility adapter

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanStageProjectionService.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanStageProjectionServiceTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextApplicationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingVNextResource.java`
- Modify: corresponding vNext tests.

**Interfaces:**
- Produces nine stable `StageCode` values and `StageProjection(planId, currentStage, primaryBlocker, nextAction, stages, computedAt)`.
- Existing `/api/modeling/vnext/plans` resolves/forwards canonical planId and no longer creates process/layer-grain plans as a second write model.

- [ ] **Step 1: Run GitNexus impact before touching vNext symbols**

Required targets: `ModelingVNextApplicationService.savePlan`, `listPlans`, `ModelingVNextResource.createPlan`, `updatePlan`.

- [ ] **Step 2: Write RED projection and adapter tests**

Assert deterministic primary blocker ordering, UNKNOWN/STALE never becoming COMPLETE, both onboarding modes producing the same stage codes, and legacy create/update resolving the canonical planId.

- [ ] **Step 3: Verify RED**

Run the focused projection/vNext test set and confirm expected assertion failures.

- [ ] **Step 4: Implement minimum projection and adapter**

Do not implement frontend mapping in 65a. Return backend facts and preserve legacy route compatibility/audit only.

- [ ] **Step 5: Verify the 65a checkpoint**

Run:

```bash
cd source/dts-platform
./mvnw -ntp -Dtest='WarehousePlan*Test,WarehousePlan*IT,ModelingVNext*Test,ModelingVNextApplicationServiceIT' test
./mvnw -ntp -DskipTests package
```

Then run `git diff --check` and GitNexus detect-changes. Record actual evidence in Sprint-65 IT docs; do not mark 65a complete if migration rehearsal or integration DB tests were not executed.
