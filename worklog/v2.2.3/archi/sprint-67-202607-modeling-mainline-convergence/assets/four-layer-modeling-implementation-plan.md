# Sprint-67 Four-Layer Modeling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved `DimensionDefinition → ModelSpecRevision → ModelImplementation → PhysicalAssetRevision` minimum loop, converge database and API Landing inputs on one implementation path, and replace the mixed modeling form with a lightweight create flow plus logical-design/data-implementation/physical-asset stages.

**Architecture:** Add `DimensionDefinition` as the only owner of business-dimension semantics. Keep ModelSpec as logical design, extend the existing model implementation ledger for revision-pinned inputs and dbt artifacts, and continue to use Catalog Dataset/Table/Column plus WarehousePlan SourceBinding as the current physical-asset truth. API data becomes modelable only after a real Landing relation is registered in Catalog. Normal mode emits a system-managed ephemeral STG artifact during compilation; advanced dbt mode may register only successfully materialized STG relations.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, Liquibase, PostgreSQL, React, TypeScript, Ant Design, Vite, dbt artifacts, Node test runner, Vitest, Biome, Playwright/Chrome 95, Docker Compose, GitNexus.

## Global Constraints

- Preserve user-owned `AGENTS.md` and `CLAUDE.md`; never stage them.
- Do not recreate or reuse `modeling_business_object`.
- Do not add a fifth ModelSpec type for ODS or STG.
- Do not require a source to save a logical DRAFT.
- Do not let connection tests create plan sources or implementation inputs.
- Do not accept user-entered IDs, versions, codes, schemas, or table names when a canonical selector exists.
- API authentication, pagination, checkpointing, and Landing writes remain owned by `dts-ingestion`.
- Catalog Dataset/Table/Column and WarehousePlan SourceBinding remain the current physical-asset truth; this batch does not introduce a parallel generic metadata store.
- Normal-mode STG is `ephemeral`, system-managed, and never registered as a physical asset.
- Advanced dbt STG becomes a Catalog physical asset only after successful run evidence confirms `view`, `table`, or `incremental`.
- Existing ModelSpec snapshots and revisions stay readable. New canonical writes use the four-layer boundary.
- Run GitNexus upstream impact before modifying every existing code symbol. Stop and report before a HIGH or CRITICAL-risk edit.
- Use focused RED/GREEN tests while implementing. Run the full backend suite, frontend production build, and browser batch once only after T08-T10 code is complete.
- Run GitNexus `detect_changes` and `git diff --check` before each commit.

---

## 1. File Responsibility Map

| Boundary | Primary files | Final responsibility |
|---|---|---|
| Business dimension | new `DimensionDefinitionContract`, repository, service, resource | business name, definition, category, owner, reuse and hierarchy semantics |
| Logical design | `ModelSpecContract`, decoders, codec, repository, application service | four model types, logical fields, grain, SCD policy and stable dimension reference |
| Data implementation | `ModelLifecycleContract`, repository, service, compiler projection | revision-pinned implementation inputs, mappings, mode, artifacts and validation |
| Physical input | WarehousePlan source resolver and Catalog schema | confirmed, current physical relations and schema fingerprints |
| API Landing | `ApiIngestionExecutor`, `OdsTableMappingSyncService` | real Landing evidence and Catalog Table/Column registration |
| dbt/STG | `ModelingDbtCompiler`, compiler adapter, `DbtAssetSyncService` | ephemeral technical artifact or verified materialized STG relation |
| Frontend create/catalog | `DimensionCatalogPage`, `ModelCenterPage`, `ModelSpecCreateDrawer` | concept catalog and minimum DRAFT creation |
| Frontend stages | `ModelSpecDetailPage` and three new stage components | isolated logical, implementation and physical-asset workflows |
| Migration | two forward-only Liquibase changelogs and migration services | expand, dry-run, idempotent backfill, compatibility and reconciliation |
| Release evidence | Sprint-67 focused scripts and `it/evidence/` | real API, DB, PostgreSQL and Chrome 95 proof |

---

## 2. F3-T08 — DimensionDefinition Contract and Persistence

### Task 2.1: Add the pure business-dimension contract

**Files**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionContract.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionContractTest.java`

**Contract**

```java
public final class DimensionDefinitionContract {
    public enum Status { DRAFT, CURRENT, RETIRED }
    public enum ReuseScope { PLAN, DOMAIN, TENANT }

    public record HierarchyLevelSemantic(String code, String name, int order) {}
    public record HierarchySemantic(
        String code,
        String name,
        List<HierarchyLevelSemantic> levels
    ) {}
    public record FieldIssue(String field, String code, String message) {}

    public record CreateCommand(
        UUID domainId,
        String name,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        String idempotencyKey
    ) {}

    public record UpdateCommand(
        String name,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies
    ) {}

    public record View(
        UUID id,
        String systemCode,
        UUID domainId,
        String name,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        Status status,
        int revision,
        String checksum,
        long usageCount,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
```

The contract exposes `CREATE_FIELDS` and `UPDATE_FIELDS` allowlists matching the record input fields, plus `validateCreate(CreateCommand)` and `validateUpdate(UpdateCommand)`. Validation returns these stable issue codes:

```text
DIMENSION_DEFINITION_DOMAIN_REQUIRED
DIMENSION_DEFINITION_NAME_REQUIRED
DIMENSION_DEFINITION_DEFINITION_REQUIRED
DIMENSION_DEFINITION_OWNER_REQUIRED
DIMENSION_DEFINITION_REUSE_SCOPE_REQUIRED
DIMENSION_DEFINITION_IDEMPOTENCY_KEY_REQUIRED
DIMENSION_DEFINITION_HIERARCHY_INVALID
```

Hierarchy codes and level codes use `^[A-Z][A-Z0-9_]{0,63}$`; names are non-blank; each hierarchy contains at least one level; level order starts at 1 and is contiguous; codes and orders are unique within their parent. Hierarchies carry business labels only and contain no ModelSpec field reference.

`systemCode` is generated only by the service as `dim_` plus a UUID without hyphens. Create/update request fields never include it. The allowlists exclude source, target-layer, SCD, materialization, SQL and dbt properties; JSON unknown-field rejection is implemented at the REST decoder boundary in Task 2.3, not guessed by this pure record.

**Steps**

- [ ] Run GitNexus impact for the nearest reference contract `ModelSpecContract` and record that this task creates a separate low-coupling type.
- [ ] Write tests for valid create/update, exact allowlists, blank business properties, hierarchy invariants and absence of implementation properties.
- [ ] Run the focused test and capture the expected compilation failure:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=DimensionDefinitionContractTest test
```

- [ ] Implement the contract and deterministic validation issues.
- [ ] Re-run the same command; expect exit code 0.

### Task 2.2: Add forward-only schema and repositories

**Files**

- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260724_01_dimension_definition.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/DimensionDefinitionRepository.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionLiquibaseTest.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/DimensionDefinitionRepositoryIT.java`

**Schema**

- `modeling_dimension_definition`: current head, tenant, immutable ID/system code, domain, lifecycle, revision/checksum and timestamps.
- `modeling_dimension_definition_revision`: immutable snapshot per `(tenant_id, dimension_definition_id, revision)`.
- `modeling_dimension_definition_legacy_map`: unique old ModelSpec-to-definition migration mapping with batch and classification.
- Nullable `dimension_definition_id` and `dimension_definition_revision` on `modeling_model_spec` and `modeling_model_spec_revision`.
- Tenant-scoped unique index on system code.
- Non-cascading tenant-aware foreign keys; the head has a current-revision FK, so it cannot point at a missing immutable revision; retirement never deletes ModelSpec or artifacts.
- The legacy-map revision FK has a matching child-side composite index.
- Audit instants use PostgreSQL `timestamptz`; the project `${datetimeType}` currently resolves to timezone-less `datetime` and is not valid for `Instant` persistence.
- Database check permits a non-null definition reference only on `model_type='DIMENSION'`; historical DIMENSION rows may remain null until migration.
- Both changesets are explicitly forward-only: Liquibase rollback fails with a clear message and recovery must be a new forward changeset.

**Repository API**

```java
Optional<StoredDimensionDefinition> findCurrent(String tenantId, UUID id);
List<StoredDimensionDefinition> listCurrent(String tenantId, UUID domainId, Status status);
Optional<StoredDimensionDefinition> findByIdempotencyKey(String tenantId, String key);
int insert(String tenantId, String actorId, CreateCommand command, View view, String requestHash);
int compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement);
long usageCount(String tenantId, UUID id);
```

`insert` uses one PostgreSQL CTE statement with `INSERT ... ON CONFLICT DO NOTHING RETURNING` so only the winning concurrent caller creates the head and revision 1 atomically. A caller that does not insert immediately reads the committed row by tenant/idempotency key: the same request hash returns `0`, while a different hash fails as an idempotency conflict. PostgreSQL conflict handling waits for the competing insert before the follow-up read, so this path needs neither an advisory lock nor system-column heuristics.

`compareAndSet` validates `replacement.id == expected.id`, `replacement.revision == expected.revision + 1`, and requires `systemCode`, `domainId`, and `createdAt` to match the existing head before it writes the CAS head update plus immutable replacement revision in one statement. Any mismatch or revision conflict rolls back the whole statement. There is no public unguarded append method.

The database system-code check is exactly `^dim_[0-9a-f]{32}$`, matching the service-generated `dim_` plus UUID-without-hyphens format.

**Steps**

- [ ] Write structural Liquibase tests for tables, indexes, FKs, non-cascade behavior and master include.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=DimensionDefinitionLiquibaseTest,ModelSpecV2ExpandLiquibaseTest,ModelLifecycleLiquibaseTest test
```

- [ ] Implement the changelog and repository.
- [ ] Add PostgreSQL integration cases for generated-code uniqueness, atomic head/revision insert and CAS, immutable code/domain/createdAt, contiguous revision, sequential and concurrent idempotency, and non-cascade retirement. Add negative cases for cross-tenant/missing revisions, both half-null combinations on head and revision rows, non-DIMENSION references, invalid revision snapshots, legacy-map FKs and deleting referenced heads/revisions.
- [ ] Run only `DimensionDefinitionRepositoryIT` against the existing integration-test profile; expect exit code 0.

### Task 2.3: Add application and REST lifecycle

**Files**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionApplicationService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DimensionDefinitionResource.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionApplicationServiceTest.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/DimensionDefinitionResourceTest.java`

**Endpoints**

```text
POST   /api/modeling/dimension-definitions
GET    /api/modeling/dimension-definitions
GET    /api/modeling/dimension-definitions/{id}
PUT    /api/modeling/dimension-definitions/{id}
POST   /api/modeling/dimension-definitions/{id}/confirm
POST   /api/modeling/dimension-definitions/{id}/retire
```

Updates and lifecycle actions require the same strong `If-Match` pattern used by ModelSpec. Confirm requires `DRAFT`; retire requires `CURRENT`; a retired definition remains readable.

**Steps**

- [ ] Write service/resource RED tests for generated code, authenticated owner, category visibility, idempotent create, CAS conflict, lifecycle transitions and permission denial.
- [ ] Implement service and resource using the existing modeling maintainer authority and actor provider.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=DimensionDefinitionApplicationServiceTest,DimensionDefinitionResourceTest test
```

---

## 3. F3-T08 — Stable ModelSpec Reference and Migration

### Task 3.1: Add a revision-pinned `dimensionDefinitionRef` without breaking historical snapshots

**Files**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCreateRequestDecoder.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecUpdateRequestDecoder.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecSnapshotCodec.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelSpecRepository.java`
- Modify: existing focused contract, decoder, codec and repository tests.

**Rules**

- Add nullable `DimensionDefinitionRef(UUID dimensionDefinitionId, int revision)` to create/update/view/content.
- Canonical DIMENSION creation requires the CURRENT head and stores its exact revision; FACT/SUMMARY/APPLICATION reject the reference.
- The reference is immutable after creation.
- `dimensionProfile.hierarchies/scdPolicy` remain logical design.
- `dimensionProfile.dimensionCode/reuseScope` are legacy-read fields and are rejected on new canonical writes.
- Omit null from serialized historical snapshots so their checksum does not change.

**Steps**

- [ ] Run GitNexus upstream impact for `ModelSpecContract`, both decoder `decode` methods, `ModelSpecSnapshotCodec.toView`, and repository insert/CAS methods. Warn before proceeding if any result is HIGH/CRITICAL.
- [ ] Add failing tests for type matrix, immutable reference, null historical decoding and checksum stability.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=ModelSpecContractTest,ModelSpecCreateRequestDecoderTest,ModelSpecRequestDecoderTest,ModelSpecSnapshotCodecTest,ModelSpecRepositoryIT test
```

- [ ] Implement contract, decode, snapshot and persistence changes.
- [ ] Re-run the same focused batch.

### Task 3.2: Validate the referenced business dimension

**Files**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecApplicationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCompatibilityReader.java`
- Modify: corresponding service/stage/compatibility tests.

**Rules**

- Create/update must resolve the pinned definition revision in the same tenant and visible domain. Creation may pin only the CURRENT head.
- DIMENSION new writes return `DIMENSION_DEFINITION_REQUIRED` when absent and `DIMENSION_DEFINITION_NOT_CURRENT` when stale/retired.
- `DRAFT_SAVE` does not require source or implementation.
- `IMPLEMENTATION_READY` rechecks that the pinned revision still exists and the definition lifecycle is CURRENT; a newer definition revision does not silently rewrite or invalidate the ModelSpec.
- Historical snapshots stay readable; compatibility projection may resolve a migration mapping but never rewrites old snapshot JSON.

**Steps**

- [ ] Run GitNexus impact for `ModelSpecApplicationService.create/update`, `ModelSpecStageGateService.evaluateAll` and `ModelSpecCompatibilityReader`.
- [ ] Add RED tests for all rules above, including deletion/retirement non-cascade.
- [ ] Implement repository-backed validation and stable error details.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=ModelSpecApplicationServiceTest,ModelSpecStageGateServiceTest,ModelSpecCompatibilityReaderTest test
```

### Task 3.3: Add dry-run and idempotent backfill

**Files**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/migration/DimensionDefinitionMigrationService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DimensionDefinitionMigrationResource.java`
- Create: corresponding service/resource tests.

**Migration classification**

```text
AUTO_MIGRATABLE
REUSABLE_EXISTING_DEFINITION
NAME_DOMAIN_CONFLICT
MISSING_DOMAIN
INVALID_LEGACY_PAYLOAD
ALREADY_MIGRATED
```

Dry-run reports total, automatic, reusable, conflicts, orphans and reasons. Execute accepts the dry-run batch ID, locks the same source revisions, writes a definition/mapping/reference idempotently, and never changes a historical ModelSpec snapshot or revision number.

**Steps**

- [ ] Write RED tests for classification, execute-after-drift rejection, rerun idempotency, counts/checksums and rollback-safe read compatibility.
- [ ] Implement dry-run and execute endpoints:

```text
POST /api/modeling/migrations/dimension-definitions/dry-run
POST /api/modeling/migrations/dimension-definitions/{batchId}/execute
```

- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=DimensionDefinitionMigrationServiceTest,DimensionDefinitionMigrationResourceTest test
```

- [ ] Run GitNexus `detect_changes`, `git diff --check`, review the T08 diff, and commit only T08 files:

```text
feat(F3/T08): separate business dimensions from logical models
```

---

## 4. F3-T09 — Revisioned ModelImplementation Inputs

### Task 4.1: Expand the existing implementation ledger

**Files**

- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260724_02_model_implementation_inputs.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelLifecycleContract.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelLifecycleRepository.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelImplementationInputsLiquibaseTest.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/ModelImplementationRepositoryIT.java`

**Contract**

```java
enum InputMode { PHYSICAL_ASSET, UPSTREAM_MODEL, GENERATED }

record PhysicalAssetInput(UUID sourceBindingId, String resolvedVersion) {}
record UpstreamModelInput(UUID modelSpecId, int revision) {}
record GeneratedInput(String generatorType, Map<String, Object> config) {}

record SaveImplementationCommand(
    InputMode inputMode,
    List<ImplementationInput> inputs,
    List<FieldMapping> fieldMappings,
    Map<String, Object> settings,
    ImplementationMode ownership,
    String materialization,
    String idempotencyKey
) {}
```

Add an append-only `modeling_model_implementation_revision`, current revision/checksum/input JSON/mapping/settings to the implementation head, and implementation revision/node kind/materialization/optional physical asset reference to artifacts. Backfill each current implementation as revision 1.

**Steps**

- [ ] Run GitNexus impact for `ModelLifecycleContract.ImplementationView` and repository `findImplementation/claimImplementation/saveArtifacts`.
- [ ] Add schema/contract RED tests for the three disjoint input modes and append-only revisions.
- [ ] Implement changeset, contract and repository.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=ModelImplementationInputsLiquibaseTest,ModelLifecycleLiquibaseTest,ModelLifecycleContractTest,ModelImplementationRepositoryIT test
```

### Task 4.2: Add compatibility projection and fail-closed gates

**Files**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelImplementationCompatibilityAdapter.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelLifecycleService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java`
- Modify: lifecycle resource/request decoding and focused tests.

**Rules**

- Legacy `sourceRefs` project to `PHYSICAL_ASSET`.
- Legacy `dependsOn` project to `UPSTREAM_MODEL`.
- Legacy `generationStrategy` projects to `GENERATED`.
- Mixed legacy kinds are a migration conflict; do not select one silently.
- DIMENSION accepts all three modes.
- FACT accepts `PHYSICAL_ASSET` or `UPSTREAM_MODEL`.
- SUMMARY/APPLICATION accept only `UPSTREAM_MODEL`.
- Physical input requires a current, confirmed SourceBinding and exact resolved version.
- Upstream input requires the pinned revision, same plan visibility, legal type/layer, no self-reference and no cycle.
- Generated input accepts only registered generator types; the initial registry includes the date-dimension generator.
- DBT-managed implementation cannot be overwritten by the normal form. Returning to normal mode creates a new implementation revision explicitly.

**Steps**

- [ ] Run GitNexus impact for `ModelLifecycleService.claim/compile` and `ModelSpecStageGateService.implementationBlockers`.
- [ ] Add RED tests for the matrix, stale/cross-plan/forged/cyclic input and mode conversion.
- [ ] Implement compatibility projection, save/validate/convert endpoints and stable errors:

```text
MODEL_IMPLEMENTATION_INPUT_REQUIRED
MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED
MODEL_IMPLEMENTATION_INPUT_STALE
MODEL_IMPLEMENTATION_SELF_REFERENCE
PHYSICAL_ASSET_NOT_CONFIRMED
```

- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=ModelImplementationCompatibilityAdapterTest,ModelLifecycleServiceTest,ModelSpecStageGateServiceTest,ModelLifecycleResourceTest test
```

---

## 5. F3-T09 — API Landing Registration

### Task 5.1: Emit real API Landing evidence

**Files**

- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/etl/api/ApiIngestionExecutor.java`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/infra/PlatformInfraClient.java`
- Modify: focused executor/client tests.

On successful Landing commit, `execution.targetTables` must contain the actual qualified relation, resource ID, `sourceType=api`, `landingMode=raw_ods`, raw-record and technical columns from `ApiSourceContracts.defaultLandingPolicy()`, cursor, rows written, execution ID and the real task revision or normalized config checksum.

Do not modify the thin Airflow DAG, HTTP authentication/pagination code, or Landing/checkpoint transaction.

**Steps**

- [ ] Run GitNexus impact for `ApiIngestionExecutor.applyLineageSnapshot/targetDataset` and `PlatformInfraClient.syncIngestionExecutionLineage`.
- [ ] Add RED tests proving successful evidence and proving failed writes do not advance checkpoint or emit modelable evidence.
- [ ] Implement the evidence payload.
- [ ] Run:

```bash
cd source
./mvnw -ntp --batch-mode -pl dts-ingestion \
  -Dtest=ApiIngestionExecutorTest,ApiRawLandingServiceTest,AirflowDagServiceTest,IngestionTaskServiceTest test
```

### Task 5.2: Register API Landing Catalog Table and Columns

**Files**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/OdsTableMappingSyncService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/lineage/IngestionLineageWriter.java`
- Modify: corresponding tests.
- Extend: `SourceReferenceResolverAdapterTest`.

For `task.sourceType=API`, consume `execution.targetTables`, upsert the real Catalog Dataset/Table/Columns, persist task/config/execution/checkpoint evidence in existing detail/tags, and write lineage with `origin=API`. Replay must be idempotent. A changed field snapshot/config checksum must yield a new schema fingerprint and stale the old SourceBinding.

**Steps**

- [ ] Run GitNexus impact for `OdsTableMappingSyncService.syncFromIngestionPayload` and `IngestionLineageWriter.writeAddaxLineage`.
- [ ] Add RED cases for API create, replay, fingerprint drift, API origin and SourceBinding resolution.
- [ ] Implement a narrow `writeIngestionLineage(..., origin)` overload; retain ADDAX behavior for existing callers.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=OdsTableMappingSyncServiceTest,IngestionLineageWriterTest,SourceReferenceResolverAdapterTest test
```

---

## 6. F3-T09 — Materialization and STG

### Task 6.1: Compile normal mode through ephemeral STG

**Files**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecCompilerProjection.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/CanonicalModelLifecycleCompilerAdapter.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingDbtCompiler.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelLifecycleRepository.java`
- Modify: corresponding compiler/adapter/repository tests.

Normal mode output for one implementation revision:

```text
stg_<model>.sql  artifactType=STG_SQL  materialization=ephemeral
<model>.sql      artifactType=SQL      ref('stg_<model>')
schema.yml       artifactType=SCHEMA
tests            artifactType=TEST
```

The STG SQL begins with `{{ config(materialized='ephemeral') }}` and is generated from validated mappings/JOIN/filter/type conversion/dedup settings. `GENERATED` compiles without `SOURCE_REQUIRED`. DBT-managed mode validates and records user artifacts but never overwrites them.

**Steps**

- [ ] Run GitNexus impact for the projection, compiler `compile/renderSql`, adapter `compile/artifactType`, and artifact save.
- [ ] Add RED tests for artifact set, deterministic replay, generated dimension, target ref and advanced-mode non-overwrite.
- [ ] Implement `renderEphemeralStg` and implementation-revision projection.
- [ ] Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=ModelSpecCompilerProjectionTest,ModelingDbtCompilerTest,CanonicalModelLifecycleCompilerAdapterTest,ModelLifecycleServiceTest test
```

### Task 6.2: Register only real dbt relations

**Files**

- Modify: the current `DbtAssetSyncService.java` in `dts-platform`.
- Modify: `CanonicalModelReleaseRegistrationAdapter.java`.
- Modify: corresponding asset-sync/release tests.

`DbtAssetSyncService.syncFromManifest` reads `node.config.materialized`:

- `ephemeral`: retain technical DAG evidence, skip Catalog Dataset/Table upsert.
- `view/table/incremental`: require successful run result and real relation, then register a technical STG asset with `warehouseLayer=STG`, modelSpec/revision/implementationRevision/dbtUniqueId tags.

Publish registration is not materialization proof. Target and materialized STG physical truth comes from manifest/run-results sync.

**Steps**

- [ ] Run GitNexus impact for asset sync and release registration.
- [ ] Add RED tests for ephemeral exclusion, successful materialized STG inclusion and failed-run exclusion.
- [ ] Implement filtering and tags.
- [ ] Run focused asset-sync/release tests.
- [ ] Run GitNexus `detect_changes`, `git diff --check`, review the T09 diff, and commit only T09 files:

```text
feat(F3/T09): unify model inputs and landing materialization
```

---

## 7. F3-T10 — Frontend Contracts and Lightweight Creation

### Task 7.1: Add frontend canonical APIs

**Files**

- Create: `source/dts-platform-webapp/src/pages/modeling/dimensionDefinitionContract.ts`
- Create: `source/dts-platform-webapp/src/api/dimensionDefinitionApi.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/modelImplementationContract.ts`
- Create: `source/dts-platform-webapp/src/api/modelImplementationApi.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/modelSpecV2Contract.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/modelSpecWorkbench.ts`
- Create: source-contract and pure contract tests.

The ModelSpec create command contains only plan, domain, type, name, optional description, DIMENSION-only `dimensionDefinitionRef`, and idempotency key. Backend defaults target layer, implementation mode and empty logical collections.

**Steps**

- [ ] Run GitNexus impact for `CreateModelSpecCommand`, `buildModelSpecCreateCommand` and API consumers.
- [ ] Add RED tests for the minimum draft, generated codes being read-only, three implementation modes and exact API paths.
- [ ] Implement types/API adapters.
- [ ] Run the new Node source-contract and pure contract tests only.

### Task 7.2: Switch the dimension catalog

**Files**

- Modify: `source/dts-platform-webapp/src/pages/modeling/DimensionCatalogPage.tsx`
- Create: `source/dts-platform-webapp/src/pages/modeling/components/DimensionDefinitionCreateDrawer.tsx`
- Modify: `dimensionCatalogViewState` tests.

The table contains name, system code, category, status, logical-model usage count and updated time. It does not contain plan, grain key, DWD layer, SCD, source, SQL or materialization. Row actions are create dimension table, references, edit and retire.

**Steps**

- [ ] Run GitNexus impact for `DimensionCatalogPage`.
- [ ] Add source-contract RED tests proving it uses only DimensionDefinition APIs and contains no ModelSpec create form.
- [ ] Implement the catalog and 520–560px create drawer.
- [ ] Run focused tests and Biome on these files.

### Task 7.3: Reduce ModelSpec creation to a minimum DRAFT

**Files**

- Modify: `source/dts-platform-webapp/src/pages/modeling/ModelCenterPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecCreateDrawer.tsx`
- Modify: existing workbench and draft-journey tests.

Remove source inventory, field, grain, SCD, dependency, generator, implementation mode, materialization and dbt inputs from creation. DIMENSION creation chooses an existing business dimension. Creation navigates to `?activeStage=logical`.

**Steps**

- [ ] Run GitNexus impact for both components.
- [ ] Replace the two known stale assertions in `dimensionDraftJourney.source-contract.test.ts` and `modelSpecDetailNavigation.test.ts` with the approved stage contract; do not count them as new RED evidence.
- [ ] Add RED tests for the minimum form, plan/category recovery and dimension preselection.
- [ ] Implement the drawer and remove candidate-model/source loading from the center page.
- [ ] Run focused Node tests and Biome.

---

## 8. F3-T10 — Three-Stage Detail

### Task 8.1: Add stage navigation and one-primary-action projection

**Files**

- Modify: `source/dts-platform-webapp/src/pages/modeling/modelSpecDetailNavigation.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/modelSpecDetailStageProjection.ts`
- Create: corresponding pure tests.

```ts
export type ModelSpecDetailStage = "logical" | "implementation" | "physical";
```

Read old `tab=design|fields|standards` as `logical` and `tab=release` as `physical`. New URLs write only `activeStage`. Stage projection returns exactly one of:

```text
保存逻辑设计
配置数据实现
验证实现
生成并发布
```

**Steps**

- [ ] Run GitNexus impact for navigation helpers.
- [ ] Add RED tests for old deep links, invalid stages, permissions, blockers and one-primary-action invariant.
- [ ] Implement both pure helpers.
- [ ] Run their Node tests.

### Task 8.2: Split logical, implementation and physical components

**Files**

- Create: `components/ModelSpecLogicalDesignStage.tsx`
- Create: `components/ModelSpecImplementationStage.tsx`
- Create: `components/ModelSpecPhysicalAssetStage.tsx`
- Modify: `ModelSpecDetailPage.tsx`
- Stop direct use of: `components/ModelSpecEditorFields.tsx`
- Reuse: fields, standards, dependency, blocker and source-inventory components.

**Ownership**

- Logical: ModelSpec fields, grain, business key, fact/time semantics, dimension references, SCD logical policy and standards.
- Implementation: canonical input selector, mappings/settings, drift repair, Landing repair, implementation mode and system preprocessing summary.
- Physical: target, DDL/artifacts, compile/test/deploy, actual assets, lineage and timeline.

Source API failure is local to implementation and never disables logical save. Physical-load failure does not clear either earlier stage. A 409 preserves the current stage and form.

**Steps**

- [ ] Run GitNexus impact for `ModelSpecDetailPage` and the reused editor components.
- [ ] Add source-contract RED tests for stage isolation, local errors, 409 recovery, read-only and 390px layout classes.
- [ ] Implement the page orchestrator and three stage components.
- [ ] In normal mode show a read-only system preprocessing summary: managed, ephemeral, no physical table.
- [ ] In physical stage filter out artifacts without a real `physicalAssetId`.
- [ ] Run focused tests and Biome only.

### Task 8.3: Connect advanced dbt without broadening routes

**Files**

- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: the current `ModelEditDrawer.tsx`
- Modify: `OpsInstancesPage.tsx`
- Modify: existing lifecycle navigation/source-contract tests.

Carry and verify `implementationRevision`; return to `activeStage=implementation`. Add `ephemeral` to explicit STG materialization choices. Keep editing in `/studio/sql-modeling`; do not add a new canonical route for `DbtFileBrowserPage`.

**Steps**

- [ ] Run GitNexus impact for the exact context parsing, lifecycle binding and return-navigation symbols.
- [ ] Add RED tests for revision pinning, safe return, ephemeral option and non-overwrite warning.
- [ ] Implement the narrow route/context changes.
- [ ] Run focused Node/Vitest tests.
- [ ] Run GitNexus `detect_changes`, `git diff --check`, review the T10 diff, and commit only T10 files:

```text
feat(F3/T10): split modeling into three guided stages
```

---

## 9. F6-T08 — One Final Verification Batch

### Task 9.1: Focused automated regression

- [ ] Run all focused backend tests from Tasks 2–6 once as a batch.
- [ ] Run the focused frontend suite:

```bash
cd source/dts-platform-webapp
node --experimental-strip-types --test \
  src/api/dimensionDefinitionApi.source-contract.test.ts \
  src/api/modelImplementationApi.source-contract.test.ts \
  src/api/modelSpecApi.source-contract.test.ts \
  src/pages/modeling/dimensionCatalogViewState.test.ts \
  src/pages/modeling/modelSpecDetailNavigation.test.ts \
  src/pages/modeling/modelSpecDetailStageProjection.test.ts \
  src/pages/modeling/modelSpecWorkbench.test.ts \
  src/pages/modeling/modelSpecV2Contract.test.ts \
  src/pages/modeling/dimensionDraftJourney.source-contract.test.ts \
  src/pages/modeling/modelLifecycleNavigation.source-contract.test.ts \
  src/pages/modeling/modelSpecThreeStageDetail.source-contract.test.ts
```

- [ ] Run focused Biome on the touched frontend files.

### Task 9.2: Full module build only after the feature batch is complete

```bash
cd source/dts-platform && npm run backend:unit:test
cd source && ./mvnw -ntp --batch-mode -pl dts-ingestion test
cd source/dts-platform-webapp && pnpm exec tsc --noEmit && pnpm build
```

Each command must exit 0. Record the logs once under `it/evidence/backend-contract/` and `it/evidence/build/`.

### Task 9.3: Real migration and API materialization evidence

- [ ] Run dimension migration dry-run twice and prove identical classification/checksums.
- [ ] Execute once, reconcile counts/references, replay idempotently and exercise the supported rollback/read-adapter path.
- [ ] Reuse the Sprint-38 live checkpoint path:

```bash
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-dag-migration.sh
```

- [ ] Add and run `it/scripts/f3-t09-api-landing-materialization.sh` to prove:
  - real API task and Landing table;
  - Landing/checkpoint transaction continuity;
  - Catalog Dataset/Table/Columns;
  - WarehousePlan `CATALOG_TABLE` confirmation;
  - implementation input revision;
  - ephemeral STG artifact with no fake Catalog asset;
  - real target relation after dbt run;
  - reverse trace from target to ModelSpec, Landing, ingestion task and API connection.

### Task 9.4: Real Chrome 95 acceptance

- [ ] Use real login, Spring Security, dts-platform, dts-ingestion and PostgreSQL; mock responses count only as layout regression.
- [ ] Verify desktop and 390px:
  1. register a business dimension without a connection;
  2. create a DIMENSION DRAFT from it;
  3. complete logical design without a source;
  4. implement a generated date dimension;
  5. implement a database physical input;
  6. implement an API Landing physical input;
  7. compile normal-mode ephemeral STG and prove no fake asset;
  8. convert to advanced dbt, materialize a real STG view/table and prove the technical asset;
  9. pin FACT → SUMMARY → APPLICATION revisions and repair drift;
  10. refresh, return, 409, read-only, permission denial and source-load failure recovery.

### Task 9.5: Close the Sprint tasks truthfully

- [ ] Save API/DB IDs, screenshots, SQL and logs under the existing F6-T08 evidence directories.
- [ ] Run GitNexus `detect_changes`, inspect unexpected flows and run `git diff --check`.
- [ ] Update F3-T02/T08/T09/T10 and F6-T08 checkboxes strictly from recorded evidence.
- [ ] Update Sprint counts and Go/No-Go, separating code, tests, deployment, migration and browser status.
- [ ] Commit evidence/status only after verification:

```text
test(F6/T08): verify four-layer modeling journeys
```

## 10. Required Execution Order

```text
T08 contract
→ T08 schema/CRUD
→ ModelSpec stable reference
→ migration/compatibility
→ T09 implementation revisions and input gates
→ API Landing Catalog Table/Column registration
→ ephemeral STG compile
→ real dbt asset filtering/registration
→ T10 frontend contracts
→ dimension catalog and lightweight create
→ three-stage detail and advanced dbt handoff
→ one focused regression batch
→ one full backend/frontend build batch
→ migration/API/PostgreSQL evidence
→ one real Chrome 95 batch
→ status reconciliation
```

T08 must not edit materialization. T09 must freeze its DTOs before T10 consumes them. No task may claim DONE from screenshots or mock API alone.
