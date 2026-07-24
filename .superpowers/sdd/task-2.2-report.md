# Task 2.2 Report — DimensionDefinition schema and repository

## Status

Completed with strict RED → GREEN verification.

## RED evidence

### Liquibase structure

Command:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=DimensionDefinitionLiquibaseTest,ModelSpecV2ExpandLiquibaseTest,ModelLifecycleLiquibaseTest test
```

Expected failure:

```text
Tests run: 6, Failures: 4, Errors: 0, Skipped: 0
BUILD FAILURE
```

The four new tests failed because `20260724_01_dimension_definition.xml` and its master include did not exist.

### Repository integration

Command:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dit.test=DimensionDefinitionRepositoryIT \
  test-compile failsafe:integration-test failsafe:verify
```

Expected failure:

```text
package com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository does not exist
cannot find symbol: class DimensionDefinitionRepository
BUILD FAILURE
```

## GREEN evidence

### Liquibase structure and existing regressions

Command:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dtest=DimensionDefinitionLiquibaseTest,ModelSpecV2ExpandLiquibaseTest,ModelLifecycleLiquibaseTest test
```

Result:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### PostgreSQL repository integration

Command:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dit.test=DimensionDefinitionRepositoryIT \
  test-compile failsafe:integration-test failsafe:verify
```

Result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The integration tests used the existing `test,testdev` PostgreSQL Testcontainers profile.

## Schema invariants

- Current heads are tenant scoped and keep immutable UUID/system code, domain, lifecycle, revision, checksum, idempotency metadata, actor metadata, and timestamps.
- Immutable revisions are unique by `(tenant_id, dimension_definition_id, revision)` and retain both complete head fields and the JSON snapshot.
- System codes and idempotency keys are unique within a tenant.
- The legacy map uniquely binds one tenant-scoped old ModelSpec to a pinned definition revision with migration batch and classification.
- Every created foreign key includes `tenant_id`; none specifies delete cascade.
- Both ModelSpec tables receive nullable definition ID/revision pairs and use tenant + definition ID + revision foreign keys.
- Head references are either both null or both non-null on a DIMENSION ModelSpec.
- Revision references are either both null or both non-null with `contract_version=2` and
  `coalesce(snapshot_json ->> 'modelType', '')='DIMENSION'`, preserving historical null rows and failing closed for canonical references.
- The changelog performs no data backfill and adds no business service.

## Repository behavior

- Implements the required current lookup/list, idempotency lookup, insert, checksum/revision CAS, append-only revision, and usage count API.
- Adds `findRevision` so immutable revisions can restore a complete `StoredDimensionDefinition`.
- `StoredDimensionDefinition` contains every View head field plus current/revision checksum, snapshot, idempotency, and timestamp metadata.
- PostgreSQL tests cover tenant isolation, idempotent insert, generated system-code uniqueness, CAS convergence, immutable revision recovery, usage count, retirement, and non-cascading references.

## Changed files

- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260724_01_dimension_definition.xml`
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/DimensionDefinitionRepository.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/DimensionDefinitionLiquibaseTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/DimensionDefinitionRepositoryIT.java`
- `.superpowers/sdd/task-2.2-report.md`

## Self-review and concerns

- Pre-edit GitNexus upstream impact for the reference `ModelSpecRepository` was MEDIUM: 14 direct import dependents, 33 total impacted nodes, and no identified execution flow; the reference symbol was not modified.
- Targeted `git diff --check` passed before staging.
- No scope outside the five task files and this report was intentionally edited.
- Existing Maven warnings remain: duplicate compiler-plugin declaration, dependency convergence warning, deprecations, and multiple SLF4J providers. They did not fail either targeted gate and are outside Task 2.2.
