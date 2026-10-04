# Sprint-68 F1-T02 Manifest v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backward-compatible manifest v2 contract so uploaded and built-in standard packages have stable identity, semantic version, dependency order and SHA-256 integrity checks before preview/apply.

**Architecture:** Keep the existing `preview → apply → rollback` pipeline as the only mutation path. Add a focused manifest contract service that parses v2 package manifests, adapts the current v1 classpath catalog, validates dependencies and verifies file digests; `StandardPackageImportService` owns uploaded-package validation and `StandardPackageBuiltinService` owns catalog/install-order validation.

**Tech Stack:** Java 21, Spring Boot 3.4, Jackson, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Keep `/api/modeling/standard-packages/**` paths backward compatible.
- A ZIP without `manifest.json` remains a legacy v1 upload and receives an explicit compatibility summary.
- New v2 packages use `schemaVersion=2.0` and semantic versions shaped as `major.minor.patch`.
- Required v2 fields are `packageCode`, `packageName`, `packageVersion`, `category`, `industry`, `releasedAt`, `effectiveFrom`, `sourceRegisterRef`, `licenseConclusion` and `files`.
- Dependencies use `packageCode + minimumVersion`; self-dependency, missing dependency, insufficient version and cycles fail closed.
- Every declared content file has a lowercase 64-character SHA-256 digest; missing, undeclared or mismatched files fail closed.
- Reinstalling an equal version is allowed only when its content checksum is equal; version rollback or same-version content drift fails closed.
- T02 records manifest metadata in the existing run JSON. Database columns and record-level provenance belong to T03.
- T02 accepts the license enum but does not authorize publication; `APPROVED` enforcement belongs to T04.
- Preserve unrelated Sprint-67 changes in the shared worktree. Do not commit or push unless the user explicitly requests it.

---

### Task 1: Manifest domain contract and stable errors

**Files:**

- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageManifestContract.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageContractException.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/StandardPackageManifestContractTest.java`

**Interfaces:**

- Produces:
  - `Manifest parsePackage(byte[] json)`
  - `Catalog parseCatalog(byte[] json)`
  - `byte[] writePackage(Manifest manifest)`
  - `Manifest adaptLegacyPackage(String packageCode, String packageName, String category, Map<String, byte[]> entries)`
  - `Map<String, Object> summary(Manifest manifest)`
  - `record Dependency(String packageCode, String minimumVersion)`
  - `record Manifest(String schemaVersion, String packageCode, String packageName, String packageVersion, String category, String industry, String releasedAt, String effectiveFrom, List<Dependency> dependencies, List<String> replaces, boolean deprecated, String sourceRegisterRef, String licenseConclusion, Map<String, String> files, String contentChecksum)`
  - `record Catalog(String schemaVersion, List<Manifest> packages)`
- Errors expose `code()` and retain an `IllegalArgumentException` API boundary.

- [x] **Step 1: Write failing parse/validation tests**

```java
@Test
void parsePackage_acceptsValidV2Manifest() {
    Manifest manifest = contract.parsePackage(validManifestJson().getBytes(UTF_8));
    assertThat(manifest.packageCode()).isEqualTo("dts-core-person");
    assertThat(manifest.packageVersion()).isEqualTo("1.0.0");
    assertThat(manifest.dependencies()).containsExactly(new Dependency("dts-core-common-enum", "1.0.0"));
}

@Test
void parsePackage_rejectsUnsupportedSchemaWithStableCode() {
    assertThatThrownBy(() -> contract.parsePackage(validManifestJson().replace("\"2.0\"", "\"3.0\"").getBytes(UTF_8)))
        .isInstanceOfSatisfying(StandardPackageContractException.class,
            error -> assertThat(error.code()).isEqualTo("MANIFEST_SCHEMA_UNSUPPORTED"));
}

@Test
void parsePackage_rejectsInvalidSemanticVersion() {
    assertThatThrownBy(() -> contract.parsePackage(validManifestJson().replace("1.0.0", "v1").getBytes(UTF_8)))
        .isInstanceOfSatisfying(StandardPackageContractException.class,
            error -> assertThat(error.code()).isEqualTo("MANIFEST_VERSION_INVALID"));
}
```

- [x] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
cd source/dts-platform
./mvnw -Dtest=StandardPackageManifestContractTest test
```

Expected: compilation failure because the manifest contract types do not exist.

- [x] **Step 3: Implement the exception and immutable contract types**

```java
public final class StandardPackageContractException extends IllegalArgumentException {
    private final String code;

    public StandardPackageContractException(String code, String message) {
        super("[" + code + "] " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
```

`StandardPackageManifestContract` validates required text, package-code format `[a-z0-9]+(?:-[a-z0-9]+)*`, semantic version `\d+\.\d+\.\d+`, ISO date/time values, license enum `APPROVED|REVIEW_REQUIRED|REJECTED`, duplicate dependencies, self-dependencies and checksum format.

- [x] **Step 4: Run the focused test**

Run:

```bash
./mvnw -Dtest=StandardPackageManifestContractTest test
```

Expected: all manifest parse and stable-error tests pass.

### Task 2: File integrity, dependency graph and installed-version rules

**Files:**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageManifestContract.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/StandardPackageManifestContractTest.java`

**Interfaces:**

- Consumes: `Manifest`, `Catalog`, `Dependency`.
- Produces:
  - `void verifyFiles(Manifest manifest, Map<String, byte[]> entries)`
  - `List<Manifest> installationOrder(Catalog catalog)`
  - `void requireInstallable(Manifest candidate, Map<String, InstalledPackage> installed)`
  - `record InstalledPackage(String packageCode, String packageVersion, String contentChecksum)`
  - `int compareVersions(String left, String right)`

- [x] **Step 1: Write failing checksum and dependency tests**

```java
@Test
void verifyFiles_rejectsChecksumMismatch() {
    Manifest manifest = manifestWithFile("01-business-terms.csv", sha256("expected"));
    assertContractCode(
        "MANIFEST_CHECKSUM_MISMATCH",
        () -> contract.verifyFiles(manifest, Map.of("01-business-terms.csv", bytes("actual"), "manifest.json", bytes("{}")))
    );
}

@Test
void installationOrder_sortsDependenciesBeforeConsumers() {
    Catalog catalog = new Catalog("2.0", List.of(personPackage(), commonEnumPackage()));
    assertThat(contract.installationOrder(catalog))
        .extracting(Manifest::packageCode)
        .containsExactly("dts-core-common-enum", "dts-core-person");
}

@Test
void installationOrder_rejectsCycles() {
    assertContractCode("MANIFEST_DEPENDENCY_CYCLE", () -> contract.installationOrder(cyclicCatalog()));
}

@Test
void requireInstallable_rejectsVersionRollbackAndSameVersionDrift() {
    Map<String, InstalledPackage> installed = Map.of(
        "dts-core-person",
        new InstalledPackage("dts-core-person", "2.0.0", sha256("v2"))
    );
    assertContractCode("MANIFEST_VERSION_ROLLBACK", () -> contract.requireInstallable(personPackage("1.9.0"), installed));
    assertContractCode(
        "MANIFEST_VERSION_CONTENT_MISMATCH",
        () -> contract.requireInstallable(personPackage("2.0.0", sha256("other")), installed)
    );
}
```

- [x] **Step 2: Run tests and confirm the new cases fail**

Run:

```bash
./mvnw -Dtest=StandardPackageManifestContractTest test
```

Expected: missing-method compilation failures.

- [x] **Step 3: Implement integrity and dependency algorithms**

Implementation rules:

```java
private static String sha256(byte[] content) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(content));
}
```

- Exclude `manifest.json` itself from `files` to avoid a circular digest.
- Reject declared files that are absent, known content files absent from the manifest, and checksum mismatch.
- Use Kahn topological sorting with package code as the deterministic secondary order.
- Validate catalog package-code uniqueness and dependency minimum versions before sorting.
- `requireInstallable` checks all dependencies against installed versions, then compares the candidate with the last installed version.

- [x] **Step 4: Run focused tests**

Run:

```bash
./mvnw -Dtest=StandardPackageManifestContractTest test
```

Expected: checksum, graph and version-policy tests pass.

### Task 3: Uploaded ZIP manifest integration

**Files:**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageImportService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/StandardPackageImportRunRepository.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/StandardPackageImportServiceTest.java`

**Interfaces:**

- Consumes: `StandardPackageManifestContract`.
- Produces:
  - `FILE_MANIFEST = "manifest.json"`
  - preview fields `manifest`, `compatibilityMode`, `packageCode`, `packageVersion`, `contentChecksum`
  - payload field `manifest`
  - repository query `List<StandardPackageImportRun> findByStatusOrderByCreatedDateDesc(String status)`

- [x] **Step 1: Write failing uploaded-package tests**

```java
@Test
void preview_v2Package_returnsManifestSummaryAndPersistsIt() {
    Map<String, byte[]> files = validFilesWithManifest("dts-core-person", "1.0.0");
    Map<String, Object> preview = service.preview(files, "person.zip", "UPLOAD", "tester");
    assertThat(preview)
        .containsEntry("packageCode", "dts-core-person")
        .containsEntry("packageVersion", "1.0.0")
        .containsEntry("compatibilityMode", "V2");
    verify(runRepository).save(argThat(run -> run.getPayloadJson().contains("\"packageCode\":\"dts-core-person\"")));
}

@Test
void preview_legacyPackage_remainsSupportedAndIsExplicit() {
    Map<String, Object> preview = service.preview(validLegacyFiles(), "legacy.zip", "UPLOAD", "tester");
    assertThat(preview).containsEntry("compatibilityMode", "LEGACY_V1");
}

@Test
void preview_v2Package_rejectsChecksumMismatchBeforeRunPersistence() {
    assertContractCode(
        "MANIFEST_CHECKSUM_MISMATCH",
        () -> service.preview(v2FilesWithWrongChecksum(), "person.zip", "UPLOAD", "tester")
    );
    verify(runRepository, never()).save(any());
}
```

- [x] **Step 2: Run the import test and confirm failure**

Run:

```bash
./mvnw -Dtest=StandardPackageImportServiceTest test
```

Expected: constructor/signature or manifest-summary assertions fail.

- [x] **Step 3: Integrate manifest parsing before CSV validation**

Implementation sequence:

```java
Manifest manifest = entries.containsKey(FILE_MANIFEST)
    ? manifestContract.parsePackage(entries.get(FILE_MANIFEST))
    : manifestContract.adaptLegacyPackage(packageName, packageName, "LEGACY_UPLOAD", entries);
manifestContract.verifyFiles(manifest, entries);
manifestContract.requireInstallable(manifest, installedPackages());
```

Add the normalized summary to preview and payload before serializing the run. Legacy uploads stay accepted but are marked `LEGACY_V1`; v2 validation failures occur before `runRepository.save`.

- [x] **Step 4: Run import and baseline package tests**

Run:

```bash
./mvnw -Dtest=StandardPackageImportServiceTest,StandardPackageApplyServiceTest test
```

Expected: uploaded v2 and all existing apply/rollback tests pass.

### Task 4: Built-in v2 catalog and dependency-aware installation

**Files:**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageBuiltinService.java`
- Modify: `source/dts-platform/src/main/resources/standard-packages/manifest.json`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/StandardPackageBuiltinServiceTest.java`

**Interfaces:**

- Consumes: `Catalog`, `Manifest`, `installationOrder`, `requireInstallable`.
- Produces built-in list fields: `schemaVersion`, `packageCode`, `packageVersion`, `dependencies`, `contentChecksum`, `compatibilityMode`, `installed`, `installedVersion`, `dependencyReady`.

- [x] **Step 1: Compute current file digests**

Run:

```bash
find source/dts-platform/src/main/resources/standard-packages -mindepth 2 -maxdepth 2 -type f -name '*.csv' -print0 |
  sort -z |
  xargs -0 sha256sum
```

Expected: one lowercase SHA-256 digest for each shipped CSV.

- [x] **Step 2: Write failing built-in dependency tests**

```java
@Test
void listBuiltin_returnsV2MetadataInDependencyOrder() {
    List<Map<String, Object>> packages = service.listBuiltin();
    assertThat(packages).extracting(pkg -> pkg.get("code"))
        .containsSubsequence("gbt-2261-gender", "common-data-elements");
    assertThat(packageByCode(packages, "common-data-elements"))
        .containsKeys("packageVersion", "dependencies", "contentChecksum", "dependencyReady");
}

@Test
void install_rejectsMissingDependencyBeforePreview() {
    assertContractCode("MANIFEST_DEPENDENCY_MISSING", () -> service.install("common-data-elements", "tester"));
    verify(importService, never()).preview(anyMap(), anyString(), anyString(), anyString());
}
```

- [x] **Step 3: Run the built-in test and confirm failure**

Run:

```bash
./mvnw -Dtest=StandardPackageBuiltinServiceTest test
```

Expected: v2 metadata assertions and missing-dependency behavior fail.

- [x] **Step 4: Upgrade the catalog and service**

Each catalog entry uses this concrete shape:

```json
{
  "schemaVersion": "2.0",
  "packageCode": "common-data-elements",
  "packageName": "常用数据元",
  "packageVersion": "1.0.0",
  "category": "数据元",
  "industry": "COMMON",
  "releasedAt": "2026-07-23T00:00:00Z",
  "effectiveFrom": "2026-07-23",
  "dependencies": [
    {"packageCode": "gbt-2261-gender", "minimumVersion": "1.0.0"},
    {"packageCode": "gbt-4658-education", "minimumVersion": "1.0.0"},
    {"packageCode": "gbt-2260-region", "minimumVersion": "1.0.0"}
  ],
  "replaces": [],
  "deprecated": false,
  "sourceRegisterRef": "SOURCE-REGISTER.json#common-data-elements",
  "licenseConclusion": "REVIEW_REQUIRED",
  "files": {
    "01-business-terms.csv": "8e5c85a5e62fc213bc207f6b9c73fb02cbd310d984c0317cd72f268a8043f661",
    "02-data-elements.csv": "998d3878445868f176de1ae58f03af04545a45a510c371a2319def11dd0e38c0"
  }
}
```

At runtime, serialize the selected catalog entry to `manifest.json`, add it to the in-memory entry map, then call the unchanged preview/apply pipeline. The service computes installed package state from APPLIED run preview JSON and treats old Sprint-57 runs as legacy `1.0.0`.

- [x] **Step 5: Run all standard-package tests**

Run:

```bash
./mvnw -Dtest=StandardPackageManifestContractTest,StandardPackageImportServiceTest,StandardPackageApplyServiceTest,StandardPackageBuiltinServiceTest test
```

Expected: all tests pass with no failures.

### Task 5: Contract documentation and Sprint tracking

**Files:**

- Create: `worklog/v2.2.3/sprint-68-202607-standard-content-library/assets/manifest-v2-contract.md`
- Modify: `worklog/v2.2.3/sprint-68-202607-standard-content-library/features/F1-标准内容契约与来源治理/T02-定义manifest-v2与包依赖契约.md`
- Modify: `worklog/v2.2.3/sprint-68-202607-standard-content-library/features/F1-标准内容契约与来源治理/README.md`
- Modify: `worklog/v2.2.3/sprint-68-202607-standard-content-library/README.md`
- Modify: `worklog/v2.2.3/sprint-queue.md`

**Interfaces:**

- Documents the exact JSON contract, stable error codes, legacy behavior and dependency/install semantics used by code.

- [x] **Step 1: Document the implemented contract**

The document must include:

- full valid manifest example with the actual shipped-file digests;
- field table and compatibility table;
- dependency and version decision matrix;
- stable error-code table;
- explicit statement that `licenseConclusion` is metadata-only until T04;
- explicit statement that record-level provenance is deferred to T03.

- [x] **Step 2: Run documentation checks**

Run:

```bash
rg -n "T[B]D|T[O]DO|待[定]" \
  docs/superpowers/plans/2026-07-23-sprint-68-f1-t02-manifest-v2.md \
  worklog/v2.2.3/sprint-68-202607-standard-content-library
git diff --check
```

Expected: no deferred-work markers in delivered contract/worklog files and no whitespace errors.

- [x] **Step 3: Update Sprint statuses**

Set F1-T02 to `DONE` only after all tests pass. Keep Sprint-68 and F1 `IN_PROGRESS`, then set F1-T03 to `IN_PROGRESS` when its implementation begins.

## Self-Review

- Spec coverage: manifest identity, version, dependencies, replaces/deprecation, source reference, license conclusion, SHA-256, v1 compatibility, deterministic dependency order and version rollback are assigned to Tasks 1–4.
- Scope boundary: database provenance, customer override and license publication policy are explicitly deferred to T03, F2 and T04 respectively.
- Deferred-work scan: no executable code step uses an unresolved marker or an undefined method; every catalog example contains the actual current digest.
- Type consistency: `Manifest`, `Catalog`, `Dependency` and `InstalledPackage` names and fields are identical across all tasks.
