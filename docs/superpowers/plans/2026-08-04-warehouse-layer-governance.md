# Warehouse Layer Governance and Model Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore prototype-aligned global custom warehouse-layer creation and deletion while keeping the six platform system layers immutable, and let every current ModelSpec select exactly one compatible system or custom layer.

**Architecture:** Keep `Sprint64GovernanceContract` as the code-owned system-layer dictionary and add a global, custom-only `modeling_warehouse_layer` registry. A new canonical `/api/modeling/warehouse-layers` resource merges both sources. ModelSpec stores `warehouseLayerCode` for the user's selection and preserves the existing canonical `layer` for validation, dbt generation, lifecycle, and materialization. Deletion is logical, strictly audited, and blocked while any non-archived ModelSpec references the custom code.

**Tech Stack:** Java 17, Spring Boot, Spring JDBC, PostgreSQL/Liquibase, JUnit 5/Mockito/MockMvc, React 18, TypeScript 5.6, Vitest/jsdom, Vite, Playwright, existing DTS audit outbox and permission model.

## Global Constraints

- The registry is global shared state. Do not add `tenant_id`, workspace, plan, domain, or browser-local ownership to `modeling_warehouse_layer`.
- Built-in codes remain `ODS_RAW`, `ODS_STANDARDIZED`, `STG`, `DWD`, `DWS`, and `ADS`; they are readable but never inserted, updated, or deleted through the custom registry.
- Every custom row has one immutable `systemLayerCode` from the six built-ins.
- Current ModelSpec selection is limited by canonical target: DIMENSION/FACT → `DWD`, SUMMARY → `DWS`, APPLICATION → `ADS`. ODS/STG custom layers appear only in the planning page until a corresponding ModelSpec owner exists.
- Persist both values: `layer` remains canonical execution semantics; `warehouseLayerCode` preserves the user-selected built-in or custom code.
- Missing `warehouseLayerCode` from old callers defaults to the canonical target layer. Historical snapshots missing the field read as their stored canonical `layer` without rewriting history.
- Custom codes use `[A-Z][A-Z0-9_]{1,63}` and remain reserved after logical deletion. Optional naming prefixes use `[a-z][a-z0-9_]{0,63}`.
- Delete returns `409 WAREHOUSE_LAYER_BUILTIN_PROTECTED` for built-ins, `409 WAREHOUSE_LAYER_IN_USE` with a count for active references, and `404 WAREHOUSE_LAYER_NOT_FOUND` for absent/deleted custom rows.
- Successful create/delete and rejected in-use delete emit strict public-audit evidence. A strict-audit failure rolls back successful writes. The in-use rejection transaction uses `noRollbackFor = WarehouseLayerException.class` so its FAIL audit remains durable.
- Keep `GET /api/governance/sprint64/warehouse-layers` as a system-only compatibility endpoint. The production page must move to `/api/modeling/warehouse-layers`.
- Preserve unrelated dirty-worktree changes. Stage only files named by the active task.
- Before editing an existing symbol, run GitNexus upstream impact analysis and report the blast radius. Stop and warn on HIGH/CRITICAL risk. Before every commit run `gitnexus_detect_changes()`.
- Follow strict RED → GREEN TDD. Run focused tests per task; defer broad builds and the single focused E2E until all code tasks are green.
- Do not mark Sprint-84 `REAL/DELIVERED` while credentials, writable test data, deployment, or authenticated menu-click acceptance are missing.

---

## File Map

### Create

- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260804_01_modeling_warehouse_layer.xml` — custom-layer table, ModelSpec selection column, backfill, constraints, and indexes.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerGovernanceLiquibaseTest.java` — static migration contract.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/WarehouseLayerRepositoryIT.java` — PostgreSQL persistence, global visibility, permanent code reservation, and active-reference count.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerContract.java` — canonical commands, views, stored row, and resolved selection.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerException.java` — stable error code/kind/details.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/WarehouseLayerRepository.java` — custom-only persistence and ModelSpec reference queries.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerApplicationService.java` — merged read, create, delete, resolution, and strict audit.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerApplicationServiceTest.java` — domain/service tests.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/WarehouseLayerResource.java` — canonical REST boundary.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/WarehouseLayerResourceTest.java` — status, permission annotation, actor, and error mapping tests.
- `source/dts-platform-webapp/src/api/warehouseLayerApi.ts` — canonical frontend contracts and GET/POST/DELETE calls.
- `source/dts-platform-webapp/src/api/warehouseLayerApi.test.ts` — endpoint and payload tests.
- `source/dts-platform-webapp/e2e/sprint84-warehouse-layer-governance.spec.ts` — one authenticated menu/UI/API closure journey.

### Modify

- `source/dts-platform/src/main/resources/config/liquibase/master.xml` — include the new migration.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java` — add `warehouseLayerCode` to create/update/view contracts.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCreateRequestDecoder.java` — accept the optional selection on create.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecUpdateRequestDecoder.java` — accept the optional selection on full replacement.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecSnapshotCodec.java` — persist and read the selection with historical fallback.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecApplicationService.java` — resolve and validate selection on create/update/reclassification.
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelSpecRepository.java` — write `warehouse_layer_code` on insert/CAS.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCreateRequestDecoderTest.java` — create decoding.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelSpecRequestDecoderTest.java` — update decoding.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelSpecSnapshotCodecTest.java` — current and historical snapshots.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelSpecApplicationServiceTest.java` — default, match, mismatch, deleted, and reclassification behavior.
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/ModelSpecRepositoryIT.java` — current-head persistence.
- `source/dts-common/src/main/resources/config/audit-action-catalog.json` — register create/delete actions.
- `source/dts-platform/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json` — keep platform fallback identical.
- `source/dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json` — keep admin fallback identical.
- `source/dts-common/src/test/java/com/yuzhi/dts/common/audit/AuditActionCatalogResourceTest.java` — assert both actions are catalogued.
- `source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.ts` — add `warehouseLayerCode` to create/update/view types and validation fields.
- `source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.test.ts` — contract validation tests.
- `source/dts-platform-webapp/src/api/modelSpecApi.source-contract.test.ts` — JSON field ownership regression.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/planningProjectionService.ts` — use the canonical merged list and remove the whole-page read-only reason.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/planningProjectionService.catalog.test.ts` — merged projection regression.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/PlanningPage.tsx` — custom create/delete editor and protected built-in rows.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/PlanningPage.test.tsx` — form, permission, confirmation, busy, conflict, and refresh tests.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.ts` — load/filter/select/save warehouse layers.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts` — draft and command propagation tests.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelingWorkbenchEditor.tsx` — compatible layer selector for ModelSpec drafts only.
- `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx` — option filtering and deleted-selection display tests.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/README.md` — canonical owner, gate, and completion boundary.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/features/F1-规划与建模概览真实化/README.md` — correct F1 owner/status.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/features/F1-规划与建模概览真实化/T01-接入规划真实目录和动作.md` — warehouse-layer slice tasks/evidence.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/features/F1-规划与建模概览真实化/T02-接入概览真实投影.md` — current projection contract.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/features/F1-规划与建模概览真实化/T03-规划概览状态与契约测试.md` — focused test matrix.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/assets/page-capability-matrix.md` — page owner and delivery state.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/assets/button-component-matrix.md` — create/delete ownership.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/assets/prototype-conformance-review-20260803.md` — corrected prototype alignment.
- `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities/it/README.md` — IT-84-01 closure journey and fail-closed inputs.
- `worklog/v2.2.3/sprint-queue.md` — Sprint-83/84 status without overstating E2E.

---

### Task 1: Add the Global Registry and ModelSpec Selection Schema

**Files:**

- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerGovernanceLiquibaseTest.java`
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260804_01_modeling_warehouse_layer.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`

**Interfaces:**

- Produces global table `modeling_warehouse_layer` with logical deletion and permanent code uniqueness.
- Produces non-null `modeling_model_spec.warehouse_layer_code` backfilled from `layer`.
- Does not create a foreign key from ModelSpec to the custom-only table because built-in codes are code-owned and valid selections.

- [ ] **Step 1: Run impact analysis before modifying `master.xml`**

Run `gitnexus_impact({target: "master.xml", direction: "upstream"})`. Report the migration-loading blast radius. If the index cannot resolve XML symbols, record that limitation and inspect the immediately adjacent includes only; do not broaden into unrelated changelogs.

- [ ] **Step 2: Write the failing structural migration test**

The test must require all invariants, not only table existence:

```java
@Test
void addsGlobalCustomLayerRegistryAndBackfillsModelSelections() throws Exception {
    String master = resource("config/liquibase/master.xml");
    String migration = resource("config/liquibase/changelog/20260804_01_modeling_warehouse_layer.xml");

    assertThat(master).contains("20260804_01_modeling_warehouse_layer.xml");
    assertThat(migration)
        .contains("modeling_warehouse_layer")
        .contains("system_layer_code")
        .contains("status in ('ACTIVE', 'DELETED')")
        .contains("warehouse_layer_code")
        .contains("update modeling_model_spec")
        .contains("set warehouse_layer_code = layer")
        .contains("nullable=\"false\"")
        .doesNotContain("tenant_id");
}
```

- [ ] **Step 3: Run the test and verify RED**

From `source/dts-platform`:

```bash
./mvnw -Dtest=WarehouseLayerGovernanceLiquibaseTest test
```

Expected: FAIL because the migration and master include do not exist.

- [ ] **Step 4: Implement the additive migration**

Create the custom-only table with these exact columns and constraints:

```xml
<createTable tableName="modeling_warehouse_layer">
    <column name="id" type="uuid"><constraints primaryKey="true" nullable="false"/></column>
    <column name="code" type="varchar(64)"><constraints nullable="false" unique="true"/></column>
    <column name="name" type="varchar(128)"><constraints nullable="false"/></column>
    <column name="system_layer_code" type="varchar(32)"><constraints nullable="false"/></column>
    <column name="description" type="varchar(1000)"/>
    <column name="naming_prefix" type="varchar(64)"/>
    <column name="status" type="varchar(16)" defaultValue="ACTIVE"><constraints nullable="false"/></column>
    <column name="version" type="integer" defaultValueNumeric="1"><constraints nullable="false"/></column>
    <column name="created_by" type="varchar(128)"><constraints nullable="false"/></column>
    <column name="created_date" type="timestamp with time zone"><constraints nullable="false"/></column>
    <column name="last_modified_by" type="varchar(128)"><constraints nullable="false"/></column>
    <column name="last_modified_date" type="timestamp with time zone"><constraints nullable="false"/></column>
</createTable>
```

Add PostgreSQL checks for the custom-code regex, prefix regex, the six system codes, status, and `version >= 1`. Add indexes on `(status, system_layer_code)` and `modeling_model_spec(warehouse_layer_code, status)`. Add the ModelSpec column nullable, backfill with `layer`, assert no null rows in a precondition, then add the not-null constraint. Use a forward-only rollback guard rather than dropping business data.

- [ ] **Step 5: Run the migration test and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 6: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only the three Task 1 files, then commit:

```bash
git commit -m "feat(modeling): add warehouse layer registry schema"
```

---

### Task 2: Implement Custom-Layer Persistence and Domain Rules

**Files:**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerContract.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerException.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/WarehouseLayerRepository.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerApplicationService.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerApplicationServiceTest.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/WarehouseLayerRepositoryIT.java`

**Interfaces:**

```java
public record CreateWarehouseLayerCommand(
    String code,
    String name,
    String systemLayerCode,
    String description,
    String namingPrefix
) {}

public record WarehouseLayerView(
    String code,
    String name,
    String systemLayerCode,
    String kind,
    String responsibility,
    List<String> namingPrefixes,
    boolean optional,
    boolean builtin,
    boolean deletable,
    String disabledReason
) {}

public record ResolvedWarehouseLayer(String code, ModelSpecContract.Layer canonicalLayer, boolean builtin) {}
```

Repository methods are global and receive no tenant parameter:

```java
List<StoredWarehouseLayer> findAllActive();
Optional<StoredWarehouseLayer> findByCode(String code);
boolean codeExists(String code);
int insert(StoredWarehouseLayer row);
long countActiveModelReferences(String code);
int softDelete(String code, int expectedVersion, String actorId, Instant modifiedAt);
```

- [ ] **Step 1: Write failing service tests**

Cover merged reads, system protection, custom creation, permanent uniqueness, type resolution, and in-use delete:

```java
@Test
void resolvesCustomSelectionToItsImmutableSystemLayer() {
    when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(active("FIN_DETAIL", "DWD")));

    ResolvedWarehouseLayer resolved = service.resolveSelection("FIN_DETAIL", Layer.DWD);

    assertThat(resolved.code()).isEqualTo("FIN_DETAIL");
    assertThat(resolved.canonicalLayer()).isEqualTo(Layer.DWD);
}

@Test
void rejectsASelectionWhoseSystemTypeDoesNotMatchTheModelTarget() {
    when(repository.findByCode("FIN_SUMMARY")).thenReturn(Optional.of(active("FIN_SUMMARY", "DWS")));

    assertThatThrownBy(() -> service.resolveSelection("FIN_SUMMARY", Layer.DWD))
        .isInstanceOf(WarehouseLayerException.class)
        .extracting("code")
        .isEqualTo("MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH");
}

@Test
void auditsAndRejectsDeleteWhileAnActiveModelReferencesTheLayer() {
    when(repository.findByCode("FIN_DETAIL")).thenReturn(Optional.of(active("FIN_DETAIL", "DWD")));
    when(repository.countActiveModelReferences("FIN_DETAIL")).thenReturn(2L);

    assertThatThrownBy(() -> service.delete("alice", "FIN_DETAIL"))
        .isInstanceOf(WarehouseLayerException.class)
        .extracting("code")
        .isEqualTo("WAREHOUSE_LAYER_IN_USE");
    verify(audit).auditActionStrict(
        eq("MODELING_WAREHOUSE_LAYER_DELETE"), eq(AuditStage.FAIL), eq("FIN_DETAIL"),
        eq(Map.of("referenceCount", 2L, "reason", "IN_USE"))
    );
    verify(repository, never()).softDelete(anyString(), anyInt(), anyString(), any());
}
```

Also assert built-ins are first in contract order, custom rows follow by `systemLayerCode/name/code`, deleted rows do not list, ODS/STG custom rows resolve for history/planning but fail current ModelSpec target matching, and blank actor IDs are rejected before writes.

- [ ] **Step 2: Run the service test and verify RED**

```bash
./mvnw -Dtest=WarehouseLayerApplicationServiceTest test
```

Expected: FAIL because the contract, repository, service, and exception do not exist.

- [ ] **Step 3: Implement normalization and error semantics**

Use one normalizer in the service:

```java
private static String normalizeCode(String value) {
    String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
        throw error("WAREHOUSE_LAYER_CODE_INVALID", BAD_REQUEST, Map.of("field", "code"));
    }
    return code;
}
```

Create rejects a built-in collision before querying custom rows, then rejects any historical custom collision through `codeExists`. `systemLayerCode` must resolve through `Sprint64GovernanceContract.resolveLayer`. Prefix normalization is lower-case and optional. Store `status=ACTIVE`, `version=1`, one server UUID, one server timestamp, and the authenticated actor.

Use these stable validation/conflict codes: `WAREHOUSE_LAYER_CODE_INVALID` for a malformed code, `WAREHOUSE_LAYER_NAME_REQUIRED` for a blank name, `WAREHOUSE_LAYER_SYSTEM_TYPE_INVALID` for an unknown built-in mapping, `WAREHOUSE_LAYER_PREFIX_INVALID` for a malformed prefix, and `WAREHOUSE_LAYER_CODE_CONFLICT` with kind `CONFLICT` for collisions with either a built-in or any active/deleted custom code.

- [ ] **Step 4: Implement merged views and selection resolution**

System views set `builtin=true`, `deletable=false`, and `disabledReason="平台内置分层不可删除"`. Active custom views set `builtin=false`, `deletable=true`, use their mapped system contract for `kind/optional`, and use custom description/prefix for display.

Selection resolution follows this order:

```java
public ResolvedWarehouseLayer resolveSelection(String requestedCode, Layer expectedLayer) {
    String effectiveCode = requestedCode == null || requestedCode.isBlank() ? expectedLayer.name() : normalizeCode(requestedCode);
    Optional<WarehouseLayerDto> builtin = Sprint64GovernanceContract.resolveLayer(effectiveCode);
    String systemCode = builtin.map(WarehouseLayerDto::code)
        .orElseGet(() -> activeCustom(effectiveCode).systemLayerCode());
    if (!systemCode.equals(expectedLayer.name())) {
        throw error("MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH", UNPROCESSABLE,
            Map.of("warehouseLayerCode", effectiveCode, "expectedSystemLayerCode", expectedLayer.name()));
    }
    return new ResolvedWarehouseLayer(effectiveCode, expectedLayer, builtin.isPresent());
}
```

Use `MODEL_SPEC_WAREHOUSE_LAYER_NOT_FOUND` for absent codes and `MODEL_SPEC_WAREHOUSE_LAYER_INACTIVE` for a logically deleted row found by `findByCode`.

- [ ] **Step 5: Implement delete transaction semantics**

Annotate delete with `@Transactional(noRollbackFor = WarehouseLayerException.class)`. Check built-ins first, then active custom row, then active reference count. Emit strict FAIL audit before throwing `WAREHOUSE_LAYER_IN_USE`. For a clean delete, perform optimistic `softDelete`, emit strict SUCCESS audit, and let any audit failure roll back the row update.

- [ ] **Step 6: Write and run the PostgreSQL repository IT**

The IT must prove:

1. a custom row created without tenant data is visible to independent actor calls;
2. logical deletion does not release the unique code;
3. `countActiveModelReferences` counts DRAFT/DESIGNING/VALIDATING/READY_TO_PUBLISH/PUBLISHED and excludes ARCHIVED;
4. optimistic delete with a stale version updates zero rows.

Run:

```bash
./mvnw -Dtest=WarehouseLayerApplicationServiceTest,WarehouseLayerRepositoryIT test
```

Expected: PASS against the repository's PostgreSQL test fixture.

- [ ] **Step 7: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 2 files, and commit:

```bash
git commit -m "feat(modeling): govern global custom warehouse layers"
```

---

### Task 3: Expose the Canonical REST Resource and Audit Catalog

**Files:**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/WarehouseLayerResource.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/WarehouseLayerResourceTest.java`
- Modify: `source/dts-common/src/main/resources/config/audit-action-catalog.json`
- Modify: `source/dts-platform/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json`
- Modify: `source/dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json`
- Modify: `source/dts-common/src/test/java/com/yuzhi/dts/common/audit/AuditActionCatalogResourceTest.java`

**Interfaces:**

```java
@RestController
@RequestMapping("/api/modeling/warehouse-layers")
public class WarehouseLayerResource {
    @GetMapping ApiResponse<List<WarehouseLayerView>> list();
    @PostMapping @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    ResponseEntity<ApiResponse<WarehouseLayerView>> create(@RequestBody CreateWarehouseLayerCommand command);
    @DeleteMapping("/{code}") @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    ResponseEntity<Void> delete(@PathVariable String code);
}
```

- [ ] **Step 1: Run impact analysis before editing the audit catalogs**

Run upstream impact for `AuditActionCatalogResourceTest` and inspect the catalog's enclosing modeling group. Report that all three runtime copies must remain byte-for-byte synchronized for the two new entries.

- [ ] **Step 2: Write failing MockMvc and catalog tests**

Require 200 GET, 201 POST, 204 DELETE, actor propagation, and stable errors:

```java
mockMvc.perform(post("/api/modeling/warehouse-layers")
        .contentType(APPLICATION_JSON)
        .content("""
            {"code":"FIN_DETAIL","name":"财务明细层","systemLayerCode":"DWD",
             "description":"财务域明细","namingPrefix":"fin_dwd_"}
            """))
    .andExpect(status().isCreated())
    .andExpect(header().string("Location", "/api/modeling/warehouse-layers/FIN_DETAIL"))
    .andExpect(jsonPath("$.data.code").value("FIN_DETAIL"))
    .andExpect(jsonPath("$.data.builtin").value(false));

mockMvc.perform(delete("/api/modeling/warehouse-layers/DWD"))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value("WAREHOUSE_LAYER_BUILTIN_PROTECTED"));
```

The catalog test must assert exact action codes `MODELING_WAREHOUSE_LAYER_CREATE` and `MODELING_WAREHOUSE_LAYER_DELETE` in all three files.

- [ ] **Step 3: Run tests and verify RED**

```bash
./mvnw -Dtest=WarehouseLayerResourceTest test
```

From `source/dts-common`:

```bash
./mvnw -Dtest=AuditActionCatalogResourceTest test
```

Expected: both commands FAIL because the resource and catalog entries do not exist.

- [ ] **Step 4: Implement the resource and error handler**

Use the same maintainer expression and actor provider as `ModelSpecResource`. Map `BAD_REQUEST → 400`, `UNPROCESSABLE → 422`, `FORBIDDEN → 403`, `NOT_FOUND → 404`, and `CONFLICT → 409` into the existing `ApiResponse` shape. Do not accept `X-Tenant-Id` and do not include a tenant parameter in the service call.

- [ ] **Step 5: Register strict audit actions**

Add the same two machine-audit definitions to the canonical catalog and both fallbacks. Use customer-readable Chinese labels, `resourceType="WAREHOUSE_LAYER"`, and write severity consistent with neighboring ModelSpec actions. Do not add a browser-side audit call.

- [ ] **Step 6: Run tests and verify GREEN**

Run both commands from Step 3. Expected: PASS.

- [ ] **Step 7: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 3 files, and commit:

```bash
git commit -m "feat(modeling): expose warehouse layer governance api"
```

---

### Task 4: Persist and Validate ModelSpec Warehouse-Layer Selection

**Files:**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCreateRequestDecoder.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecUpdateRequestDecoder.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecSnapshotCodec.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecApplicationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelSpecRepository.java`
- Modify: the five focused ModelSpec tests listed in the File Map.

**Interfaces:**

- `CreateModelSpecCommand`, `UpdateModelSpecCommand`, and `ModelSpecView` each gain `String warehouseLayerCode` immediately after canonical `Layer layer` where the record contains `layer`.
- Create requests allow `warehouseLayerCode`; update requests allow `warehouseLayerCode`.
- The current row writes `warehouse_layer_code`; revision snapshots carry `warehouseLayerCode`.

- [ ] **Step 1: Run impact analysis for every existing symbol to be edited**

Run upstream impact separately for `CreateModelSpecCommand`, `UpdateModelSpecCommand`, `ModelSpecView`, `ModelSpecSnapshotCodec`, `ModelSpecApplicationService`, `insertV2`, and `compareAndSetV2`. Summarize constructor/test churn and execution-flow risk before editing. Warn and stop if any result is HIGH/CRITICAL.

- [ ] **Step 2: Write failing decoder, codec, application, and repository tests**

Required assertions:

```java
assertThat(createDecoder.decode(json("""
    {"planId":"%s","domainId":"%s","modelType":"FACT","name":"预算执行明细",
     "warehouseLayerCode":"FIN_DETAIL","idempotencyKey":"create-1"}
    """.formatted(PLAN_ID, DOMAIN_ID))).command().warehouseLayerCode())
    .isEqualTo("FIN_DETAIL");

assertThat(codec.readView(historicalSnapshotWithoutWarehouseLayerCode).warehouseLayerCode())
    .isEqualTo("DWD");

verify(warehouseLayers).resolveSelection("FIN_DETAIL", Layer.DWD);
assertThat(created.modelSpec().warehouseLayerCode()).isEqualTo("FIN_DETAIL");
assertThat(created.modelSpec().layer()).isEqualTo(Layer.DWD);
```

Add negative application tests for NOT_FOUND, INACTIVE, and TYPE_MISMATCH codes. Add an old-call compatibility test where create/update omit the field and the resulting selection is canonical `DWD/DWS/ADS`. Add a reclassification test proving FACT `FIN_DETAIL` → SUMMARY resets `warehouseLayerCode` to `DWS` while canonical `layer` becomes `DWS`; it must never retain an incompatible custom code silently.

The repository IT must read `warehouse_layer_code` directly from `modeling_model_spec` after insert and CAS update.

- [ ] **Step 3: Run focused tests and verify RED**

```bash
./mvnw -Dtest=ModelSpecCreateRequestDecoderTest,ModelSpecRequestDecoderTest,ModelSpecSnapshotCodecTest,ModelSpecApplicationServiceTest,ModelSpecRepositoryIT test
```

Expected: FAIL because the field and resolver wiring do not exist.

- [ ] **Step 4: Extend strict request and response contracts**

Add the field to allowed-field sets without making it required:

```java
private static final Set<String> INTERACTIVE_CREATE_FIELDS = Set.of(
    "planId", "domainId", "modelType", "name", "description", "dimensionDefinitionRef",
    "warehouseLayerCode", "idempotencyKey", "dataMartId", "variantCode"
);
```

Preserve every compatibility constructor by delegating `warehouseLayerCode` to `layer == null ? null : layer.name()`. Do not bump the ModelSpec contract version: this is an additive optional input and additive response field with a deterministic historical fallback.

- [ ] **Step 5: Resolve selection before hashing or persistence**

In create/update, compute the expected canonical target with `ModelSpecContract.targetLayer(modelType)`, resolve the effective selection, and build the effective command before request hash/checksum generation:

```java
Layer canonicalLayer = ModelSpecContract.targetLayer(command.modelType());
ResolvedWarehouseLayer selection = warehouseLayers.resolveSelection(command.warehouseLayerCode(), canonicalLayer);
CreateModelSpecCommand effective = command.withLayerSelection(canonicalLayer, selection.code());
```

This order ensures idempotency and checksums represent the normalized persisted selection. Reclassification intentionally resets to the new canonical code because its command has no explicit replacement layer choice.

- [ ] **Step 6: Preserve historical checksum compatibility**

When deserializing an old `ModelSpecView`, use a compact constructor or codec normalizer that substitutes `layer.name()` only when `warehouseLayerCode` is blank. Extend `matchesStoredContentChecksum` with a historical projection that omits `warehouseLayerCode`, just as existing display-name compatibility does. Do not rewrite stored revision JSON or stored checksums.

- [ ] **Step 7: Write the current-head column**

Add `warehouse_layer_code` to both SQL statements and bind `view.warehouseLayerCode()`:

In the insert column list, place `warehouse_layer_code` immediately after `layer`; in the bound arguments, place `view.warehouseLayerCode()` immediately after `view.layer().name()`. The CAS assignment prefix becomes:

```sql
set layer = ?, warehouse_layer_code = ?, model_type = ?, implementation_mode = ?, name = ?
```

Bind `replacement.warehouseLayerCode()` immediately after `replacement.layer().name()`.

Revision JSON remains the immutable history owner; no duplicate revision-table column is added.

- [ ] **Step 8: Run focused tests and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 9: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 4 files, and commit:

```bash
git commit -m "feat(modeling): persist warehouse layer selection"
```

---

### Task 5: Add the Frontend API and Merged Planning Projection

**Files:**

- Create: `source/dts-platform-webapp/src/api/warehouseLayerApi.ts`
- Create: `source/dts-platform-webapp/src/api/warehouseLayerApi.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/planningProjectionService.ts`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/planningProjectionService.catalog.test.ts`

**Interfaces:**

```ts
export type WarehouseSystemLayerCode = "ODS_RAW" | "ODS_STANDARDIZED" | "STG" | "DWD" | "DWS" | "ADS";

export type WarehouseLayerView = {
	code: string;
	name: string;
	systemLayerCode: WarehouseSystemLayerCode;
	kind: string;
	responsibility: string;
	namingPrefixes: string[];
	optional: boolean;
	builtin: boolean;
	deletable: boolean;
	disabledReason?: string | null;
};

export type CreateWarehouseLayerCommand = {
	code: string;
	name: string;
	systemLayerCode: WarehouseSystemLayerCode;
	description?: string;
	namingPrefix?: string;
};
```

- [ ] **Step 1: Run upstream impact for `loadPlanningProjection`**

Report all planning pages/tests that consume its `readOnlyReason`, `headers`, `rows`, or `source` fields before editing.

- [ ] **Step 2: Write failing API and projection tests**

```ts
it("uses the canonical global warehouse-layer resource", async () => {
	await listWarehouseLayers();
	expect(api.get).toHaveBeenCalledWith(expect.objectContaining({ url: "/modeling/warehouse-layers" }));
	await createWarehouseLayer(command);
	expect(api.post).toHaveBeenCalledWith(expect.objectContaining({ url: "/modeling/warehouse-layers", data: command }));
	await deleteWarehouseLayer("FIN_DETAIL");
	expect(api.delete).toHaveBeenCalledWith(expect.objectContaining({ url: "/modeling/warehouse-layers/FIN_DETAIL" }));
});

it("projects built-in and custom layers as maintainable catalog rows", async () => {
	mocks.listWarehouseLayers.mockResolvedValue([builtinDwd, customFinDetail]);
	const projection = await loadPlanningProjection("layers");
	expect(projection.readOnlyReason).toBeNull();
	expect(projection.rows.map((row) => row.id)).toEqual(["DWD", "FIN_DETAIL"]);
	expect(projection.rows[1]?.source).toEqual(customFinDetail);
});
```

Delete the old assertion that the entire layer page is read-only.

- [ ] **Step 3: Run tests and verify RED**

From `source/dts-platform-webapp`:

```bash
pnpm exec vitest run \
  src/api/warehouseLayerApi.test.ts \
  src/pages/data-modeling/prototype/services/planningProjectionService.catalog.test.ts
```

Expected: FAIL because the canonical API module is absent and the projection still reads Sprint-64 directly.

- [ ] **Step 4: Implement API functions and projection**

Export exactly:

```ts
export const listWarehouseLayers = () => quietGet<WarehouseLayerView[]>("/modeling/warehouse-layers");
export const createWarehouseLayer = (data: CreateWarehouseLayerCommand) =>
	quietPost<WarehouseLayerView>("/modeling/warehouse-layers", data);
export const deleteWarehouseLayer = (code: string) =>
	quietDelete<void>(`/modeling/warehouse-layers/${encodeURIComponent(code)}`);
```

The layer projection sets `readOnlyReason: null`, stores each full `WarehouseLayerView` in `row.source`, and uses headers `["分层编码", "分层名称", "所属系统类型", "加工责任", "命名前缀", "来源"]`. Render source as `系统` or `自定义`; do not expose backend enum `kind` as the user-facing ownership label.

- [ ] **Step 5: Run tests and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 6: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 5 files, and commit:

```bash
git commit -m "feat(modeling-ui): load governed warehouse layers"
```

---

### Task 6: Restore Prototype-Aligned Create/Delete UI

**Files:**

- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/PlanningPage.test.tsx`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/PlanningPage.tsx`

**Interfaces:**

- Produces a `WarehouseLayerEditor` rendered only for `route.view === "layers"`.
- Consumes canonical create/delete functions and `WarehouseLayerView` rows.
- Keeps generic search/table rendering and server-authoritative refresh.

- [ ] **Step 1: Run upstream impact for `PlanningPage`**

Report its route consumers and the shared `PlanningProjection` table path. Confirm the change is confined to the `layers` branch.

- [ ] **Step 2: Write failing component tests**

Mock `useDataModelingMenuGrant` as true and render a projection with DWD plus FIN_DETAIL. Assert:

```ts
for (const label of ["分层编码", "分层名称", "所属系统类型", "说明", "命名前缀"])
	expect(container.textContent).toContain(label);
expect(button("新建数仓分层")).toBeDefined();
expect(options("删除数仓分层")).toEqual(["选择要删除的自定义分层…", "财务明细层 · FIN_DETAIL"]);
expect(container.textContent).not.toContain("系统分层字典由平台内置并统一生效，当前版本只读");
```

Submit a valid form and assert `createWarehouseLayer` receives normalized UI values, then `loadPlanningProjection("layers")` runs again. Select FIN_DETAIL for deletion, reject the first confirmation, accept the second, and assert one DELETE plus one reload. Mock `409 WAREHOUSE_LAYER_IN_USE` and assert the error remains visible while the rows remain intact. With maintain permission false, all write controls are disabled and the explanation is shown once.

- [ ] **Step 3: Run the component test and verify RED**

```bash
pnpm exec vitest run src/pages/data-modeling/prototype/PlanningPage.test.tsx
```

Expected: FAIL because the layer editor is not rendered.

- [ ] **Step 4: Implement `WarehouseLayerEditor`**

Use controlled state for `code`, `name`, `systemLayerCode`, `description`, `namingPrefix`, `busy`, and `error`. The system-layer select contains all six built-ins and defaults to `DWD`. Validate client-side code/prefix formats before POST, but keep the server as authority.

Render deletion choices from:

```ts
const customLayers = rows
	.map((row) => row.source)
	.filter((source): source is WarehouseLayerView => Boolean(source && "builtin" in source && !source.builtin && source.deletable));
```

Confirm with `确认删除自定义分层“${layer.name}（${layer.code}）”？`. Disable all actions while busy. On success clear the create form or delete selection and await the parent's `onChanged`, which refreshes the authoritative list. Do not optimistically mutate rows.

- [ ] **Step 5: Run the component test and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 6: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 6 files, and commit:

```bash
git commit -m "feat(modeling-ui): manage custom warehouse layers"
```

---

### Task 7: Let ModelSpec Workbench Select Compatible Layers

**Files:**

- Modify: `source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.ts`
- Modify: `source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.test.ts`
- Modify: `source/dts-platform-webapp/src/api/modelSpecApi.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.ts`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelingWorkbenchEditor.tsx`
- Modify: `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx`

**Interfaces:**

- `CreateModelSpecCommand`, `UpdateModelSpecCommand`, and `ModelSpecView` gain `warehouseLayerCode`.
- `ModelWorkbenchContext` gains `warehouseLayers: WarehouseLayerView[]`.
- `ModelSpecDraft` gains `warehouseLayerCode: string`; `ConceptDimensionDraft` remains unchanged.

- [ ] **Step 1: Run upstream impact for contract and workbench symbols**

Run impact for `ModelSpecView`, `ModelSpecDraft`, `emptyModelDraft`, `modelDraftFromView`, `loadModelWorkbenchContext`, `saveModelDraft`, and `ModelingWorkbenchEditor`. Report affected create/update/import consumers. Stop on HIGH/CRITICAL.

- [ ] **Step 2: Write failing contract/service/editor tests**

Required service assertions:

```ts
expect(emptyModelDraft("fact", context)).toMatchObject({ warehouseLayerCode: "DWD" });
expect(modelDraftFromView({ ...factView, warehouseLayerCode: "FIN_DETAIL" })).toMatchObject({
	warehouseLayerCode: "FIN_DETAIL",
});

await saveModelDraft({ ...factDraft, warehouseLayerCode: "FIN_DETAIL" }, saveContext);
expect(createModelSpec).toHaveBeenCalledWith(expect.objectContaining({ warehouseLayerCode: "FIN_DETAIL" }));
expect(updateModelSpec).toHaveBeenLastCalledWith(
	expect.anything(), expect.objectContaining({ layer: "DWD", warehouseLayerCode: "FIN_DETAIL" }),
);
```

Editor tests must prove a FACT draft lists DWD and FIN_DETAIL but excludes DWS/FIN_SUMMARY; a SUMMARY draft lists DWS/FIN_SUMMARY; an APPLICATION draft lists ADS custom rows. A saved model whose custom row is now deleted keeps a disabled `已删除分层 · CODE` option so its value is not silently replaced. Concept-dimension creation continues to show the fixed disabled DWD label and no selector.

- [ ] **Step 3: Run focused frontend tests and verify RED**

```bash
pnpm exec vitest run \
  src/features/modeling/contracts/modelSpecV2Contract.test.ts \
  src/api/modelSpecApi.source-contract.test.ts \
  src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts \
  src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx
```

Expected: FAIL because selection is absent from types, drafts, commands, and editor.

- [ ] **Step 4: Extend frontend contracts**

Add `warehouseLayerCode` to both allowed-field arrays and shapes:

```ts
type CreateModelSpecBase = {
	planId: string;
	domainId: string;
	name: string;
	description?: string | null;
	warehouseLayerCode?: string | null;
	idempotencyKey: string;
	dataMartId?: string | null;
	variantCode?: string | null;
};

export type WarehouseLayerSelection = {
	layer: ModelSpecLayer;
	warehouseLayerCode: string;
};
```

Keep `UpdateModelSpecCommand`'s current properties and add the two properties from `WarehouseLayerSelection` in their current `layer` position. `ModelSpecView.warehouseLayerCode` is required because the server always returns an effective code.

- [ ] **Step 5: Load, default, and persist the selection**

Load layers in the existing `Promise.all`. For new ModelSpec drafts default to `MODEL_KIND_CONFIG[kind].layer`; for persisted models use `model.warehouseLayerCode`. Add `warehouseLayerCode` to `buildUpdate`, non-dimension create, and the nested `CreateDimensionModelCommand.modelSpec`. Validate a nonblank code before save.

Do not add the field to `ConceptDimensionDraft`: concept dimensions are `DimensionDefinition`, not ModelSpec.

- [ ] **Step 6: Render the compatible selector**

Filter with canonical system ownership:

```ts
const eligibleLayers = context.warehouseLayers.filter(
	(layer) => layer.systemLayerCode === MODEL_KIND_CONFIG[draft.createKind].layer,
);
```

Replace the disabled ModelSpec layer input with a select labelled `数仓分层`. Option text is `${layer.name}（${layer.code}）${layer.builtin ? " · 系统" : " · 自定义"}`. Keep the rest of the form unchanged. Persisted missing/deleted values are display-only and must block saving until the user chooses an active compatible layer.

- [ ] **Step 7: Run focused tests and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 8: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only Task 7 files, and commit:

```bash
git commit -m "feat(modeling-ui): select governed warehouse layers"
```

---

### Task 8: Synchronize Sprint-84 Truth and Acceptance Contracts

**Files:**

- Modify: the nine Sprint/worklog files listed in the File Map.

**Interfaces:**

- Makes WarehouseLayerApplicationService/ModelSpec the canonical owner statement.
- Removes stale statements that `PlanningWorkspace` or WarehousePlan owns every planning leaf.
- Adds IT-84-01 create → select → in-use reject → model cleanup → layer delete → audit journey.

- [ ] **Step 1: Write the documentation assertions before editing prose**

Use a focused source-contract check in `T03-规划概览状态与契约测试.md` and the normal review checklist to require these exact facts:

```text
系统分层：代码字典，只读
自定义分层：modeling_warehouse_layer，全局共享，可新增/逻辑删除
模型选择：warehouseLayerCode；执行语义：layer
删除保护：非 ARCHIVED ModelSpec 引用返回 WAREHOUSE_LAYER_IN_USE
真实交付状态：未完成认证 E2E 前保持 PENDING/BLOCKED_E2E_INPUT
```

- [ ] **Step 2: Correct F1 and matrices**

Replace old `PlanningWorkspace`/WarehousePlan blanket-owner claims only where they concern the current planning page. Keep legitimate WarehousePlan references for relationship graph and other owned capabilities. Mark the layer slice `CODE_COMPLETE` only after Tasks 1–7 tests pass; otherwise use `IMPLEMENTATION_PENDING`.

- [ ] **Step 3: Define IT-84-01 inputs and cleanup**

Document required environment inputs: `E2E_BASE_URL`, `E2E_USERNAME`, `E2E_PASSWORD`, `E2E_MODELING_PLAN_ID`, and `E2E_MODELING_DOMAIN_ID`. The journey creates a unique `E2E_DWD_<UTC timestamp>` code, never uses a pre-existing business code, archives/deletes its temporary draft model, deletes its temporary layer, and records audit event IDs. Missing inputs fail closed; they do not skip and do not permit `REAL/DELIVERED`.

- [ ] **Step 4: Review status consistency**

Run:

```bash
rg -n "PlanningWorkspace|系统分层.*只读|暂无独立的分层策略配置入口|WAREHOUSE_LAYER|IT-84-01|REAL/DELIVERED" \
  worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities \
  worklog/v2.2.3/sprint-queue.md
```

Expected: remaining `PlanningWorkspace` references describe history/prohibition only; no statement says all warehouse layers are read-only; delivery claims match available evidence.

- [ ] **Step 5: Detect scope and commit**

Run `gitnexus_detect_changes()`, stage only the Task 8 documentation files, and commit:

```bash
git commit -m "docs(sprint-84): align warehouse layer delivery truth"
```

---

### Task 9: Run Final Verification, Review, and One Focused E2E

**Files:**

- Create: `source/dts-platform-webapp/e2e/sprint84-warehouse-layer-governance.spec.ts`
- Modify only if verification exposes a defect: files already owned by Tasks 1–8.

- [ ] **Step 1: Add the fail-closed Playwright journey**

The spec must:

1. authenticate with explicit credentials;
2. enter `/data-modeling/planning/layers` by clicking the real “数据建模 → 数仓规划 → 数仓分层” menu anchors, not direct URL navigation;
3. create a unique custom DWD layer through the visible form;
4. verify the row and its custom badge;
5. open a ModelSpec create flow and verify the new layer is selectable for FACT/DIMENSION and absent for SUMMARY;
6. use the authenticated API context with configured plan/domain IDs to create a temporary FACT draft referencing the code;
7. verify UI delete returns the in-use conflict and keeps the row;
8. delete/archive the temporary draft with its strong ETag, then delete the custom layer in UI;
9. verify both strict audit action codes through the authorized audit surface;
10. clean up in `finally`, redacting identity, roles, permissions, tokens, and cookies from artifacts.

At startup throw a clear error if any required environment input is absent. Do not use `test.skip` for missing credentials or writable IDs.

- [ ] **Step 2: Run focused backend verification**

From `source/dts-platform`:

```bash
./mvnw -Dtest=WarehouseLayerGovernanceLiquibaseTest,WarehouseLayerApplicationServiceTest,WarehouseLayerRepositoryIT,WarehouseLayerResourceTest,ModelSpecCreateRequestDecoderTest,ModelSpecRequestDecoderTest,ModelSpecSnapshotCodecTest,ModelSpecApplicationServiceTest,ModelSpecRepositoryIT test
```

From `source/dts-common`:

```bash
./mvnw -Dtest=AuditActionCatalogResourceTest test
```

Expected: PASS with zero failures/errors.

- [ ] **Step 3: Run focused frontend verification and formatting**

From `source/dts-platform-webapp`:

```bash
pnpm exec vitest run \
  src/api/warehouseLayerApi.test.ts \
  src/features/modeling/contracts/modelSpecV2Contract.test.ts \
  src/api/modelSpecApi.source-contract.test.ts \
  src/pages/data-modeling/prototype/services/planningProjectionService.catalog.test.ts \
  src/pages/data-modeling/prototype/PlanningPage.test.tsx \
  src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts \
  src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx

pnpm exec biome check \
  src/api/warehouseLayerApi.ts \
  src/api/warehouseLayerApi.test.ts \
  src/features/modeling/contracts/modelSpecV2Contract.ts \
  src/pages/data-modeling/prototype/services/planningProjectionService.ts \
  src/pages/data-modeling/prototype/PlanningPage.tsx \
  src/pages/data-modeling/prototype/services/modelWorkbenchService.ts \
  src/pages/data-modeling/prototype/ModelingWorkbenchEditor.tsx
```

Expected: all focused tests and formatting checks PASS.

- [ ] **Step 4: Run module builds and Chrome 95 regression**

Use the `dts-chrome95-regression` skill, then run:

```bash
cd source/dts-platform && ./mvnw -DskipTests package
cd ../dts-platform-webapp && LEGACY_BROWSER_BUILD=1 pnpm build
```

Expected: backend package and legacy-browser production bundle succeed. Record bundle warnings separately from failures.

- [ ] **Step 5: Request independent code review**

Use the required code-reviewer for all code changes. Review specifically for global/tenant leakage, strict-audit transaction behavior, snapshot checksum compatibility, ModelSpec/dbt canonical-layer invariants, delete races, permission enforcement, and UI fail-closed behavior. Resolve actionable findings with focused RED → GREEN tests, then rerun Steps 2–4.

- [ ] **Step 6: Run the single focused E2E only after code/build gates pass**

```bash
pnpm exec playwright test e2e/sprint84-warehouse-layer-governance.spec.ts --project=chromium --workers=1
```

Expected with all authorized inputs and deployed current build: PASS. If inputs or deployment are missing, record `G4=BLOCKED_E2E_INPUT` or `G4=BLOCKED_DEPLOYMENT`; do not report the feature as real-delivered.

- [ ] **Step 7: Final scope detection and delivery handoff**

Run `gitnexus_detect_changes()` and `git diff --check`. Confirm only the new warehouse-layer owner, ModelSpec selection, planning/workbench UI, focused tests, audit catalog, migration, E2E, and Sprint-84 truth files changed. Commit any final test/E2E-only changes:

```bash
git commit -m "test(modeling): verify warehouse layer governance journey"
```

Report separately:

- code/contracts and focused tests;
- database migration application;
- backend/frontend builds;
- deployment state;
- authenticated real-menu E2E and audit acceptance;
- rollback anchor and cleanup outcome.

Do not collapse these into a single “完成” statement.
