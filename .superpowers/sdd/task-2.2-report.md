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

## 2026-07-24 database review remediation

### Atomic repository changes

- `insert` now validates an initial revision of exactly `1` and uses one PostgreSQL data-modifying CTE to insert the current head and immutable revision `1`.
- `insert` uses `ON CONFLICT (tenant_id, idempotency_key) DO NOTHING RETURNING`; only the winning head row feeds revision `1`.
- A `0` result immediately reads the committed head request hash. Matching replays return `0`; a different hash raises `DataIntegrityViolationException` without changing either ledger table.
- `compareAndSet` validates replacement identity and contiguous revision before SQL, then uses one data-modifying CTE to update the guarded head and insert the immutable replacement revision.
- An immutable revision conflict aborts the entire CAS statement and leaves the head at its prior revision/checksum.
- The public unguarded `appendRevision` method was removed.

### RED evidence

Initial full repository IT:

```text
Tests run: 11, Failures: 2, Errors: 9, Skipped: 0
BUILD FAILURE
```

Observed PostgreSQL error before the atomic CTE:

```text
ERROR: insert or update on table "modeling_dimension_definition"
violates foreign key constraint "fk_dimension_definition_current_revision"
Detail: Key (...) is not present in table "modeling_dimension_definition_revision".
```

After the atomic CTE, the remaining repository-contract assertion exposed Spring exception translation:

```text
Tests run: 11, Failures: 1, Errors: 0, Skipped: 0
Expected IllegalArgumentException but was InvalidDataAccessApiUsageException
```

The assertion now verifies the pre-SQL `IllegalArgumentException` root cause. A focused RED for an invalid initial revision then produced:

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting code to raise a throwable.
```

### GREEN evidence

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=DimensionDefinitionLiquibaseTest test
```

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dit.test=DimensionDefinitionRepositoryIT \
  test-compile failsafe:integration-test failsafe:verify
```

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The PostgreSQL 17.4 integration run proves both data-modifying CTE statements satisfy the existing non-deferrable current-revision FK. No FK was changed to `DEFERRABLE`.

### Coverage and impact

- IT coverage includes atomic insert/CAS, stale and conflicting CAS rollback, initial/jumped/backward revisions, cross-tenant and missing revisions, both paired half-null forms, non-DIMENSION heads, fail-closed revision contract/snapshot/modelType checks, both legacy-map FKs, referenced head/revision deletion, and idempotency hash mismatch.
- Refreshed GitNexus upstream impact for `DimensionDefinitionRepository`, `insert`, and `compareAndSet` was LOW: zero indexed direct callers and zero affected execution flows.
- Staged GitNexus detection covered exactly four files, reported 33 changed symbols, zero affected processes, and LOW risk.
- `git diff --cached --check` passed.

## 2026-07-24 concurrent idempotency closeout

### Remaining RED evidence

The initial concurrent regression returned two apparent winners:

```text
Tests run: 14, Failures: 1, Errors: 0, Skipped: 0
Expecting actual: [1, 1]
to contain exactly in any order: [0, 1]
BUILD FAILURE
```

The test profile configures Hikari with `auto-commit: false`. The two executor threads had called the repository outside a transaction, so each connection returned an insert count before its uncommitted work was discarded on release. The concurrent helper now runs each caller in its own `REQUIRES_NEW` transaction, exercising the real PostgreSQL conflict wait and commit boundary.

### Final GREEN evidence

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=DimensionDefinitionLiquibaseTest test
```

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode \
  -Dit.test=DimensionDefinitionRepositoryIT \
  test-compile failsafe:integration-test failsafe:verify
```

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The final implementation uses neither advisory locks nor PostgreSQL system columns such as `xmax`. Existing CAS identity predicates, timezone-aware audit columns, generated-code validation, retirement behavior, and negative reference tests remain covered by the 14-case integration suite.

Commit: `3a04583507922cdf3e95fd18b31d7a6b77ab33af` (`fix(F3/T08): close dimension ledger invariants`). The report remains intentionally unstaged.
