# Domain Modeling Candidate Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the domain-scoped manual conformed-dimension, optional-template, candidate-confirmation, and confirmed-only bus-matrix loop inside the existing subject-area page.

**Architecture:** `Sprint64GovernanceService` remains the authority for domain-owned modeling facts. Manual records are created confirmed; template records remain unconfirmed until an atomic domain-scoped confirmation command succeeds. The existing subject-area page consumes backend truth, delegates dimension/template controls to one focused card component, and filters the matrix to confirmed facts only.

**Tech Stack:** Java 21, Spring Boot 3.4, JDBC, PostgreSQL/Testcontainers, React 18, TypeScript 5.6, Ant Design 5, Node test runner, Vitest, Vite legacy build for Chrome 95.

## Global Constraints

- Do not add a menu or a new page.
- Do not auto-confirm template-installed records.
- Do not allow unconfirmed processes or dimensions into the bus matrix or modeling recommendations.
- Existing rows remain confirmed through the first-stage migration default.
- The backend is the saved-state authority; session storage must not impersonate a successful remote write.
- Do not use `:has()`, container queries, new viewport units, `toSorted()`, or other Chrome 95-incompatible APIs.
- Preserve all unrelated working-tree changes.
- Do not commit or push unless the user explicitly asks.

---

### Task 1: Backend modeling-fact lifecycle

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateInstallerTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sprint64/Sprint64GovernanceService.java`

**Interfaces:**
- Consumes: existing `sprint64_business_process`, `sprint64_conformed_dimension`, and `sprint64_bus_matrix` tables.
- Produces:
  - `ConformedDimensionDto createConformedDimension(UUID, ConformedDimensionRequest)`
  - `void deleteConformedDimension(UUID, String)`
  - `ModelingCandidateConfirmationResult confirmModelingCandidates(UUID, ModelingCandidateConfirmationRequest)`
  - confirmed-only validation inside `saveBusMatrix(UUID, BusMatrixRequest)`

- [x] **Step 1: Run GitNexus upstream impact analysis**

Run impact analysis for `Sprint64GovernanceService`, `createProcess`, `deleteProcess`, and `saveBusMatrix`. Stop and report before editing if any result is `HIGH` or `CRITICAL`.

- [x] **Step 2: Write failing PostgreSQL integration tests**

Add these behaviors to `IndustryModelingTemplateInstallerTest`:

```java
@Test
void manualDimensionIsImmediatelyConfirmedAndCanEnterTheMatrix() {
    governanceService.createProcess(domainId, new BusinessProcessRequest("manual-process", "手工过程", null));
    var dimension = governanceService.createConformedDimension(
        domainId,
        new ConformedDimensionRequest("organization", "组织机构", "dim_organization")
    );

    assertThat(dimension.sourceType()).isEqualTo("MANUAL");
    assertThat(dimension.confirmed()).isTrue();
    assertThat(governanceService.saveBusMatrix(domainId, new BusMatrixRequest("manual-process", "organization", true)).enabled()).isTrue();
}

@Test
void templateFactsMustBeConfirmedAtomicallyBeforeTheyEnterTheMatrix() {
    installer.install(domainId, catalog.requireTemplate("pjm"));

    assertThatThrownBy(() -> governanceService.saveBusMatrix(domainId, new BusMatrixRequest("node-plan-loop", "completion-status", true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("尚未确认");

    var result = governanceService.confirmModelingCandidates(
        domainId,
        new ModelingCandidateConfirmationRequest(List.of("node-plan-loop"), List.of("completion-status"))
    );

    assertThat(result.confirmedProcesses()).isEqualTo(1);
    assertThat(result.confirmedDimensions()).isEqualTo(1);
    assertThat(governanceService.saveBusMatrix(domainId, new BusMatrixRequest("node-plan-loop", "completion-status", true)).enabled()).isTrue();
}

@Test
void confirmationRejectsForeignDomainIdsWithoutPartialUpdates() {
    installer.install(domainId, catalog.requireTemplate("pjm"));

    assertThatThrownBy(() -> governanceService.confirmModelingCandidates(
        UUID.randomUUID(),
        new ModelingCandidateConfirmationRequest(List.of("node-plan-loop"), List.of("completion-status"))
    )).isInstanceOf(IllegalArgumentException.class);

    assertThat(governanceService.listProcesses(domainId)).allMatch(item -> !item.confirmed());
    assertThat(governanceService.listConformedDimensions(domainId)).allMatch(item -> !item.confirmed());
}

@Test
void referencedDimensionCannotBeDeleted() {
    governanceService.createProcess(domainId, new BusinessProcessRequest("manual-process", "手工过程", null));
    governanceService.createConformedDimension(domainId, new ConformedDimensionRequest("organization", "组织机构", null));
    governanceService.saveBusMatrix(domainId, new BusMatrixRequest("manual-process", "organization", true));

    assertThatThrownBy(() -> governanceService.deleteConformedDimension(domainId, "organization"))
        .isInstanceOf(DimensionInUseException.class);
}
```

- [x] **Step 3: Verify RED**

Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=IndustryModelingTemplateInstallerTest test
```

Expected: test compilation fails because the new requests, result, exception, and service methods do not exist.

- [x] **Step 4: Implement the minimal service lifecycle**

Add records and exception under `Sprint64GovernanceService`:

```java
public record ConformedDimensionRequest(String dimensionId, String name, String sourceModel) {}

public record ModelingCandidateConfirmationRequest(List<String> processIds, List<String> dimensionIds) {}

public record ModelingCandidateConfirmationResult(int confirmedProcesses, int confirmedDimensions) {}

public static final class DimensionInUseException extends RuntimeException {
    public DimensionInUseException(String dimensionId) {
        super("一致性维度已被总线矩阵引用: " + dimensionId);
    }
}
```

Implementation requirements:

- Normalize and validate dimension IDs with `[a-z0-9][a-z0-9_-]{1,127}`.
- Insert manual dimensions with `source_type='MANUAL'` and `confirmed=true`.
- Validate every confirmation ID before executing either update.
- Update only rows where `confirmed=false`; repeated confirmation returns zero counts.
- Reject bus-matrix writes unless both selected facts are confirmed.
- Reject deletion when an enabled matrix row exists; delete disabled rows before deleting the dimension.
- Use `java.sql.Timestamp` for PostgreSQL timestamp parameters.

- [x] **Step 5: Verify GREEN**

Run the Task 1 Maven command again. Expected: all installer integration tests pass against real PostgreSQL.

---

### Task 2: REST lifecycle contract and audit

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/Sprint64GovernanceResourceTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/Sprint64GovernanceResource.java`

**Interfaces:**
- Consumes: Task 1 service methods and records.
- Produces:
  - `POST /domains/{domainId}/conformed-dimensions`
  - `DELETE /domains/{domainId}/conformed-dimensions/{dimensionId}`
  - `POST /domains/{domainId}/modeling-candidates/confirm`

- [x] **Step 1: Run GitNexus upstream impact analysis**

Analyze `Sprint64GovernanceResource`. Continue only for `LOW` or `MEDIUM` risk.

- [x] **Step 2: Write failing resource tests**

Add tests which call the resource directly and verify service delegation:

```java
@Test
void dimensionAndCandidateEndpointsDelegateToTheDomainService() {
    UUID domainId = UUID.randomUUID();
    var request = new ConformedDimensionRequest("organization", "组织机构", "dim_organization");
    var dimension = new ConformedDimensionDto("organization", "组织机构", "dim_organization", List.of(domainId.toString()), "MANUAL", null, null, true);
    var confirmation = new ModelingCandidateConfirmationRequest(List.of("process-a"), List.of("organization"));
    var confirmationResult = new ModelingCandidateConfirmationResult(1, 1);
    when(service.createConformedDimension(domainId, request)).thenReturn(dimension);
    when(service.confirmModelingCandidates(domainId, confirmation)).thenReturn(confirmationResult);

    assertThat(resource.createConformedDimension(domainId, request).getData()).isEqualTo(dimension);
    assertThat(resource.confirmModelingCandidates(domainId, confirmation).getData()).isEqualTo(confirmationResult);
    assertThat(resource.deleteConformedDimension(domainId, "organization").getData()).isTrue();
}

@Test
void referencedDimensionMapsToConflict() {
    UUID domainId = UUID.randomUUID();
    doThrow(new DimensionInUseException("organization")).when(service).deleteConformedDimension(domainId, "organization");

    assertThatThrownBy(() -> resource.deleteConformedDimension(domainId, "organization"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
}
```

- [x] **Step 3: Verify RED**

Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=Sprint64GovernanceResourceTest test
```

Expected: compilation fails because the resource methods are absent.

- [x] **Step 4: Implement endpoints, authority checks, and audit actions**

Use the existing `GOVERNANCE_MAINTAINER_EXPRESSION` for all three mutations. Emit:

```text
SPRINT64_DIMENSION_CREATE
SPRINT64_DIMENSION_DELETE
SPRINT64_MODELING_CANDIDATES_CONFIRM
```

Catch only `DimensionInUseException` in the delete endpoint and map it to `ResponseStatusException(HttpStatus.CONFLICT, ...)`.

- [x] **Step 5: Verify GREEN**

Run the Task 2 Maven command. Expected: all resource tests pass.

---

### Task 3: Frontend API and confirmed-fact selectors

**Files:**
- Modify: `source/dts-platform-webapp/src/api/sprint64GovernanceApi.test.ts`
- Modify: `source/dts-platform-webapp/src/api/sprint64GovernanceApi.ts`
- Create: `source/dts-platform-webapp/src/pages/governance/modelingCandidates.test.ts`
- Create: `source/dts-platform-webapp/src/pages/governance/modelingCandidates.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/businessProcess.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/conformedDimensions.ts`

**Interfaces:**
- Produces API functions:
  - `createConformedDimensionApi`
  - `deleteConformedDimensionApi`
  - `confirmModelingCandidatesApi`
  - `listModelingTemplatesApi`
  - `installModelingTemplateApi`
- Produces selectors:
  - `confirmedProcesses(processes)`
  - `confirmedDimensions(dimensions)`
  - `pendingCandidateCount(processes, dimensions)`

- [x] **Step 1: Run GitNexus impact analysis**

Analyze `Sprint64BusinessProcess`, `Sprint64ConformedDimension`, `recommendDimensionsForProcess`, and `SubjectAreasPage` before changing their consumers.

- [x] **Step 2: Write failing API tests**

Extend the Vitest suite to assert these exact requests:

```ts
await createConformedDimensionApi("domain-1", { dimensionId: "organization", name: "组织机构", sourceModel: "dim_organization" });
await deleteConformedDimensionApi("domain-1", "organization");
await confirmModelingCandidatesApi("domain-1", { processIds: ["process-a"], dimensionIds: ["organization"] });
await listModelingTemplatesApi();
await installModelingTemplateApi("pjm", "domain-1");

expect(post).toHaveBeenCalledWith({
  url: "/governance/sprint64/domains/domain-1/conformed-dimensions",
  data: { dimensionId: "organization", name: "组织机构", sourceModel: "dim_organization" },
});
expect(post).toHaveBeenCalledWith({
  url: "/governance/sprint64/domains/domain-1/modeling-candidates/confirm",
  data: { processIds: ["process-a"], dimensionIds: ["organization"] },
});
expect(post).toHaveBeenCalledWith({
  url: "/governance/modeling-templates/pjm/install",
  params: { domainId: "domain-1" },
});
```

- [x] **Step 3: Write failing selector tests**

```ts
test("only confirmed domain facts enter the modeling workbench", () => {
  const processes = [
    { processId: "manual", confirmed: true },
    { processId: "candidate", confirmed: false },
  ] as Sprint64BusinessProcess[];
  const dimensions = [
    { dimensionId: "organization", confirmed: true },
    { dimensionId: "status", confirmed: false },
  ] as Sprint64ConformedDimension[];

  assert.deepEqual(confirmedProcesses(processes).map(item => item.processId), ["manual"]);
  assert.deepEqual(confirmedDimensions(dimensions).map(item => item.dimensionId), ["organization"]);
  assert.equal(pendingCandidateCount(processes, dimensions), 2);
});
```

- [x] **Step 4: Verify RED**

Run:

```bash
cd source/dts-platform-webapp
pnpm exec vitest run src/api/sprint64GovernanceApi.test.ts
node --test src/pages/governance/modelingCandidates.test.ts
```

Expected: imports fail because the API functions and selectors do not exist.

- [x] **Step 5: Implement types, API functions, and selectors**

Add this shared shape to the API file:

```ts
export type ModelingFactProvenance = {
  sourceType: "MANUAL" | "TEMPLATE" | "IMPORTED" | "SCANNED" | string;
  sourceId?: string;
  sourceVersion?: string;
  confirmed: boolean;
};
```

Make process and dimension API types extend it. Carry the same optional provenance fields into the page-level `BusinessProcess` and `ConformedDimension` types. Update `recommendDimensionsForProcess` to reject unconfirmed dimensions defensively.

- [x] **Step 6: Verify GREEN**

Run the Task 3 commands. Expected: both suites pass.

---

### Task 4: Subject-area candidate review UI

**Files:**
- Create: `source/dts-platform-webapp/src/pages/governance/ConformedDimensionCatalogCard.tsx`
- Create: `source/dts-platform-webapp/src/pages/governance/ConformedDimensionCatalogCard.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/semantic-workspace/ConformedDimensionRecommendations.tsx`

**Interfaces:**
- Consumes: Task 3 API functions and selectors.
- Produces one in-page card with manual dimension CRUD, candidate batch confirmation, and an optional-template drawer.

- [x] **Step 1: Write failing source-contract tests**

The new card test must assert:

```ts
assert.match(source, /新增一致性维度/);
assert.match(source, /选择行业模板/);
assert.match(source, /安装后仍需确认/);
assert.match(source, /全部确认/);
assert.match(source, /confirmModelingCandidatesApi/);
assert.match(source, /installModelingTemplateApi/);
assert.doesNotMatch(source, /自动确认|新增菜单/);
```

Extend the subject-area source contract to assert:

```ts
assert.match(source, /confirmedProcesses/);
assert.match(source, /confirmedDimensions/);
assert.match(source, /ConformedDimensionCatalogCard/);
assert.doesNotMatch(source, /Session state remains the immediate UI source of truth/);
assert.doesNotMatch(source, /Keep the session draft usable/);
```

- [x] **Step 2: Verify RED**

Run:

```bash
cd source/dts-platform-webapp
node --test src/pages/governance/ConformedDimensionCatalogCard.source-contract.test.ts src/pages/governance/SubjectAreasPage.source-contract.test.ts
```

Expected: the new component is absent and the subject page still contains session-as-saved-state fallbacks.

- [x] **Step 3: Implement `ConformedDimensionCatalogCard`**

Use this prop contract:

```ts
export type ConformedDimensionCatalogCardProps = {
  domainId: string;
  canManage: boolean;
  processes: Sprint64BusinessProcess[];
  dimensions: Sprint64ConformedDimension[];
  onChanged: () => Promise<void> | void;
};
```

The component must:

- Render one card, one create modal, and one right-side template drawer.
- Keep form values and selected candidate IDs after failed mutations.
- Use the same confirmation API for single-item and bulk confirmation.
- Show source/version and confirmed/pending tags.
- Map delete HTTP 409 to “该维度已被总线矩阵引用，请先取消对应勾选”.
- Reload through `onChanged` only after a successful create, delete, confirm, or install.

- [x] **Step 4: Converge `SubjectAreasPage` on backend truth**

Implement a reusable `loadDomainModelingFacts(domainId)` callback. It must set remote empty arrays as empty, not retain session rows. Process create/delete and matrix toggles must update visible state only after their API call succeeds; failures retain modal/input or current checkbox state and display an error.

Render:

- provenance and pending/confirmed tags on process rows;
- a single-process confirm action using `confirmModelingCandidatesApi`;
- `ConformedDimensionCatalogCard` between process and matrix cards;
- only `confirmedProcesses(...)` rows and `confirmedDimensions(...)` columns in the matrix;
- a compact pending count in the matrix title.

- [x] **Step 5: Harden modeling recommendations**

Filter the API dimension catalog to `confirmed === true` before building recommendations. If the backend returns an unconfirmed legacy matrix relation, it must still not be shown as reusable.

- [x] **Step 6: Verify GREEN**

Run Task 3 tests plus both Task 4 source-contract tests. Expected: all pass.

---

### Task 5: Full verification and evidence

**Files:**
- Modify only files from Tasks 1-4 if verification finds defects.
- Modify: `docs/superpowers/plans/2026-07-17-domain-modeling-candidate-review.md` to mark completed checkboxes.

**Interfaces:**
- Produces release evidence without adding capability.

- [x] **Step 1: Backend regression**

Run:

```bash
cd source/dts-platform
./mvnw -ntp --batch-mode -Dtest=IndustryModelingTemplateInstallerTest,Sprint64GovernanceContractTest,Sprint64GovernanceResourceTest,IndustryModelingTemplateResourceTest test
```

- [x] **Step 2: Frontend regression**

Run focused Node/Vitest tests, then:

```bash
cd source/dts-platform-webapp
pnpm exec tsc --noEmit
pnpm build
```

- [x] **Step 3: Chrome 95 source and browser checks**

Search the touched UI for unsupported syntax and APIs. Open `/governance/subjects` at 1366×768 and a narrow viewport; verify the card, modal, drawer, matrix scrolling, console, and network behavior. Record any environment blocker rather than claiming browser proof.

- [x] **Step 4: Repository gates**

Run:

```bash
git diff --check
```

Run GitNexus `detect_changes(scope=all)`. Expected: no `HIGH` or `CRITICAL` affected process. Confirm production modeling/governance code contains no PJM identifiers outside the optional template resource.

- [x] **Step 5: Report without committing**

Report changed files, RED/GREEN evidence, database behavior, Chrome 95 evidence, existing non-blocking warnings, and remaining next-phase work. Do not commit or push.

## Verification Evidence (2026-07-17)

- Backend focused regression: `IndustryModelingTemplateInstallerTest`, `Sprint64GovernanceContractTest`, `Sprint64GovernanceResourceTest`, and `IndustryModelingTemplateResourceTest` passed 16/16 against PostgreSQL 17 Testcontainers.
- Frontend focused regression: governance API Vitest passed 3/3; Node domain/source-contract suites passed 20/20; targeted Biome check passed; the production legacy build completed after TypeScript validation and transformed 10,578 modules.
- Compatibility scan: no touched UI source used `:has()`, container queries, new viewport units, `structuredClone`, `toSorted()`, `toReversed()`, `toSpliced()`, or `Object.groupBy`.
- Browser smoke: `/governance/subjects` passed at 1366x768 and 390x844, covering the candidate card, create modal, optional-template drawer, confirmed-only matrix shell, and the responsive domain-directory collapse. The available runtime browser was Chrome 150, not Chrome 95; Chrome 95 compatibility is supported by the configured legacy build target and source scan rather than a Chrome 95 runtime claim.
- Browser environment evidence: the currently deployed backend predates this change, so `GET /api/governance/modeling-templates` returned 404 during the smoke test. The page degraded without blanking; coordinated backend/Liquibase/frontend deployment remains required for production end-to-end proof. One external Iconify request also failed and is unrelated to this feature.
- Repository gates: `git diff --check` passed; GitNexus `detect_changes(scope=all)` reported LOW risk, 129 changed indexed symbols, and zero affected execution flows; the touched production modeling/governance code contains no PJM identifier outside the optional template resource.
- No commit or push was performed.
