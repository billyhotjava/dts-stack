# Generic Modeling Template Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove implicit PJM semantics from the generic modeling runtime and introduce an explicit, versioned, domain-scoped industry-template installation boundary.

**Architecture:** Persist business-process provenance and conformed dimensions as domain-owned facts. Load optional industry packs through a generic classpath catalog and copy them idempotently into the domain only after an authorized install request. Keep PJM data in the optional template and test resources, never in generic contracts or default page state.

**Tech Stack:** Java 17, Spring Boot, JDBC, Liquibase, Jackson, React/TypeScript, Node test runner.

## Global Constraints

- DTS is a generic product; PJM is an optional example/template only.
- No customer-specific process IDs, model names, or fields in generic contracts or validators.
- No automatic template installation for new tenants or domains.
- Preserve existing customer rows and existing API paths.
- New schema changes use a new forward-only Liquibase changeSet.
- Chrome 95 compatibility must be preserved.
- Do not commit or push unless the user explicitly asks.

---

### Task 1: Pin the genericity boundary with RED tests

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextContractTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/sprint64/Sprint64GovernanceContractTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/Sprint64GovernanceResourceTest.java`
- Create: `source/dts-platform-webapp/src/pages/governance/GenericModelingBoundary.source-contract.test.ts`

**Interfaces:**
- Produces failing expectations for an empty generic catalog, no PJM fixture methods, and no frontend product seeds.

- [ ] Replace the PJM fixture assertion with reflection assertions that `ModelingVNextContract` has no `pjm*` methods or `Pjm*` nested records.
- [ ] Assert the generic Sprint-64 contract exposes only warehouse/grain rules and no static conformed-dimension catalog.
- [ ] Assert a mocked domain with no persisted dimensions returns an empty list.
- [ ] Add a source-contract test that rejects PJM seed identifiers from production page/helper files.
- [ ] Run the four targeted tests and confirm they fail because the old fixture/seeds still exist.

### Task 2: Add the forward-only persistence expansion

**Files:**
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260717_01_generic_modeling_template_boundary.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/GenericModelingTemplateLiquibaseTest.java`

**Interfaces:**
- Produces `sprint64_business_process` provenance columns and `sprint64_conformed_dimension`.

- [ ] Write the Liquibase test first: master include, new nullable/defaulted provenance columns, dimension table, unique key, and rollback.
- [ ] Run the test and confirm RED because the changelog does not exist.
- [ ] Add the changelog and master include without modifying old migrations.
- [ ] Re-run the test and confirm GREEN.

### Task 3: Introduce a generic industry-template catalog

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateContract.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateCatalog.java`
- Create: `source/dts-platform/src/main/resources/config/modeling-templates/index.json`
- Create: `source/dts-platform/src/main/resources/config/modeling-templates/pjm-v1.json`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateCatalogTest.java`

**Interfaces:**
- Produces `listTemplates()` and `requireTemplate(String templateId)`.
- Template identity is `templateId + version`; catalog parsing validates required IDs/names and contract compatibility.

- [ ] Write tests for listing one optional PJM pack, resolving it by ID, rejecting an unknown ID, and proving no implicit installation side effect.
- [ ] Run tests and confirm RED because the catalog classes are absent.
- [ ] Implement the minimal records and classpath JSON loader.
- [ ] Re-run tests and confirm GREEN.

### Task 4: Persist domain dimensions and install templates idempotently

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sprint64/Sprint64GovernanceService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sprint64/Sprint64GovernanceContract.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateInstaller.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/template/IndustryModelingTemplateInstallerTest.java`

**Interfaces:**
- `Sprint64GovernanceService.listConformedDimensions(UUID)` reads persisted rows.
- `IndustryModelingTemplateInstaller.install(UUID, IndustryModelingTemplate)` inserts missing rows with `sourceType=TEMPLATE`, `sourceId`, `sourceVersion`, and `confirmed=false`.
- Produces `InstallationResult` with created counts and status.

- [ ] Write JDBC-backed installer tests for first install, repeated install, and preservation of a manually changed row.
- [ ] Run tests and confirm RED because persistence/install behavior is missing.
- [ ] Remove the static conformed-dimension catalog from the generic contract.
- [ ] Implement persisted dimension reads and idempotent inserts using `ON CONFLICT DO NOTHING`.
- [ ] Re-run tests and existing Sprint-64 tests.

### Task 5: Expose explicit template read/install API

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/IndustryModelingTemplateResource.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/IndustryModelingTemplateResourceTest.java`

**Interfaces:**
- `GET /api/governance/modeling-templates`
- `GET /api/governance/modeling-templates/{templateId}`
- `POST /api/governance/modeling-templates/{templateId}/install?domainId={uuid}`

- [ ] Write resource tests for list/detail, authorized install delegation, and unknown-template error mapping.
- [ ] Run tests and confirm RED because the resource is absent.
- [ ] Implement the resource using the governance-maintainer authority expression and audit actions.
- [ ] Re-run resource tests and confirm GREEN.

### Task 6: Remove PJM from generic runtime and page defaults

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextContract.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/PjmModelingFixture.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextContractTest.java`
- Modify: `source/dts-platform-webapp/src/pages/governance/businessProcess.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/conformedDimensions.ts`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/semantic-workspace/ConformedDimensionRecommendations.tsx`
- Modify: corresponding Node tests.

**Interfaces:**
- Generic helpers operate only on caller-provided process/dimension data.
- Recommendation component loads the domain catalog and bus matrix through existing Sprint-64 APIs.

- [ ] Move PJM fixture construction to test scope and remove production `Pjm*` records/methods.
- [ ] Delete automatic process/dimension seeds and update helper tests to use neutral test fixtures.
- [ ] Remove “from PJM example” controls and leave manual creation as the empty-state action.
- [ ] Load dimension recommendations from persisted APIs rather than static constants.
- [ ] Run backend and Node boundary/helper tests and confirm GREEN.

### Task 7: Verify the architecture boundary

**Files:**
- Modify only files introduced by this plan if verification finds defects.

**Interfaces:**
- Produces reproducible verification evidence; no new product capability.

- [ ] Run targeted Maven tests for contract, migration, catalog, installer, and resources.
- [ ] Run targeted Node tests for business processes, dimensions, subject-area source contract, and genericity boundary.
- [ ] Run `pnpm build` in `source/dts-platform-webapp`.
- [ ] Run `git diff --check`.
- [ ] Run GitNexus `detect_changes(scope=all)` and verify only expected modeling/governance flows are affected.
- [ ] Report files changed, migration behavior, verification results, and the next UI/backend phase without committing.
