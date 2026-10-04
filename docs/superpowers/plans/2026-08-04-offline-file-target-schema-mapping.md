# Offline File Target Schema Mapping Implementation Plan

> **For Codex:** Execute this plan inline with focused red-green tests. Do not create a new page, do not alter database/API ingestion flows, and defer broad verification until all implementation tasks are complete.

**Goal:** Extend the existing offline-file ingestion wizard so an uploaded file can reuse an existing target table's field structure, map/edit fields, then either create a new table or explicitly recreate the selected table with a full `DROP` and reload.

**Architecture:** The current three-step access-plan wizard remains the only UI. Target-table discovery reuses the platform SQL metadata control plane and searches metadata only. The selected schema and landing intent are persisted as managed file metadata, while the ingestion backend alone performs validated DDL. Existing encrypted upload, classification sealing, admission, Addax execution, Airflow orchestration, and audit infrastructure remain authoritative.

**Tech Stack:** React/TypeScript, Vitest, Spring Boot/Java, JUnit 5/Mockito, JDBC metadata, PostgreSQL writer, Addax, Airflow.

---

## Guardrails

- Keep the change inside offline-file ingestion and shared target metadata APIs.
- Existing table selection is a schema template; source business rows are never scanned.
- Search by table name or column name and return the full `schema.table` identity.
- `CREATE_NEW` rejects an already-existing target table.
- `RECREATE_EXISTING` locks the selected full table name, requires explicit confirmation, and executes `DROP TABLE` without `CASCADE` before recreating it.
- A dependency-blocked drop fails closed. A load failure after the drop does not restore the former table automatically and must be explained in the UI.
- Never accept arbitrary DDL or preserve a user-supplied `DROP` in Addax `preSql`.
- Legacy file tasks without the new landing metadata keep their current non-destructive behavior.
- Preserve unrelated dirty worktree changes; stage and commit only files owned by this plan.

## Task 1: Add Pure Field-Mapping Domain Helpers

**Files:**

- Create: `source/dts-platform-webapp/src/pages/foundation/access/shared/fileTargetSchemaMapping.ts`
- Create: `source/dts-platform-webapp/src/pages/foundation/access/shared/fileTargetSchemaMapping.test.ts`
- Modify: `source/dts-platform-webapp/src/api/ingestion.ts`

**Steps:**

- [ ] Write failing tests for exact-name matching, normalized matching, unmatched fields, explicit positional fill, duplicate target names, invalid identifiers, and required target names.
- [ ] Add `FileLandingMode`, `FileStructureMode`, `FileLandingSpec`, and target-column metadata types.
- [ ] Implement deterministic mapping helpers. Do not silently match by ordinal position.
- [ ] Preserve column descriptions when normalizing managed file columns.
- [ ] Run only the new helper test file and confirm it passes.

## Task 2: Extend Target Metadata Search

**Files:**

- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SqlWorkbenchResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlMetadataService.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/sql/SqlMetadataServiceTest.java`
- Modify: `source/dts-platform-webapp/src/api/sql-workbench.ts`
- Modify or create focused frontend API test beside `sql-workbench.ts`

**Steps:**

- [ ] Write failing backend tests proving a keyword can match either a table name or a column name, results are deduplicated, full schema/name is retained, and the result limit is enforced.
- [ ] Add optional `keyword` and bounded `limit` parameters to the existing target table metadata endpoint; reuse datasource access checks, datasource credentials, cache policy, and audit path.
- [ ] Extend column metadata with ordinal position, description, default value, and auto-increment flags without breaking existing consumers.
- [ ] Update the frontend API types and optional query parameters.
- [ ] Run the focused Java metadata tests and frontend API tests.

## Task 3: Extend the Existing File Wizard UI

**Files:**

- Modify: `source/dts-platform-webapp/src/pages/foundation/access/FileAccessStep.tsx`
- Create: `source/dts-platform-webapp/src/pages/foundation/access/FileFieldMappingEditor.tsx`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/AccessPlanWizardPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/LandingScheduleStep.tsx`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/useAccessPlanWizard.ts`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/accessPlan.types.ts`
- Modify focused wizard/component tests under the same directory

**Steps:**

- [ ] Write failing UI tests for metadata search debounce/selection, full table identity, editable mapping rows, explicit positional fill, landing-mode validation, locked recreate table name, and destructive confirmation.
- [ ] Show the platform-managed target datasource in file step 2 and keep the target datasource editor out of file step 3.
- [ ] Add manual and reuse-existing-structure modes to the existing resource step; no route or page is added.
- [ ] Search after two characters with a 300 ms debounce and a maximum of 20 metadata results.
- [ ] Render an editable field mapping table with source field, target field, target type, description, and mapping status.
- [ ] Default to create-new. For recreate-existing, lock the selected `schema.table`, show the non-recoverable full-refresh warning, and require explicit confirmation.
- [ ] Validate nonempty/legal/unique target field names before advancing.
- [ ] Run focused component tests only.

## Task 4: Persist and Restore the Landing Contract

**Files:**

- Modify: `source/dts-platform-webapp/src/pages/foundation/access/shared/accessManagedFile.ts`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/shared/accessPlanPayload.ts`
- Modify focused tests beside those modules

**Steps:**

- [ ] Write failing payload tests for `_fileLanding`, editable target columns/descriptions, create-new target identity, recreate-existing locked identity, and edit-page restoration.
- [ ] Persist the landing contract in managed source configuration, including mode, selected source table identity, target full name, confirmation, and schema snapshot.
- [ ] Keep `_fileLanding` out of executable reader parameters.
- [ ] Restore the contract when editing a saved access plan without disturbing upload seal/checksum/classification data.
- [ ] Run focused payload and restoration tests.

## Task 5: Implement Controlled Server-Side Recreate

**Files:**

- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/TargetTableProvisioner.java`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java`
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
- Modify focused tests for the three services

**Steps:**

- [ ] Write failing tests for create-new collision rejection, recreate-existing missing confirmation rejection, exact-table drop/recreate, no `CASCADE`, dependency failure propagation, legacy behavior, `_fileLanding` stripping, and audit metadata.
- [ ] Parse and validate the landing contract on the server; never trust the UI confirmation alone.
- [ ] In `TargetTableProvisioner`, reject create-new collisions. For confirmed recreate-existing, verify the selected/target identity and issue server-generated `DROP TABLE` without `CASCADE`, then create the target from validated file columns.
- [ ] Apply safe PostgreSQL column comments from field descriptions.
- [ ] Add `_fileLanding` to Addax reader metadata stripping while keeping user `DROP`/`TRUNCATE` filtering unchanged.
- [ ] Include landing mode, qualified target, and `destructive=true` in the existing ingestion audit event for the recreate action.
- [ ] Run focused ingestion service tests.

## Task 6: Final Verification and Scope Review

**Files:** all files changed above

**Steps:**

- [ ] Run `gitnexus_detect_changes()` and confirm only expected symbols/processes are affected.
- [ ] Run all focused frontend tests touched by this plan.
- [ ] Run the `dts-platform-webapp` production build and Chrome 95 compatibility checks.
- [ ] Run focused `dts-platform` and `dts-ingestion` Java tests, then module builds if focused tests pass.
- [ ] Review the final diff for secrets, arbitrary DDL, `CASCADE`, duplicate pages/routes, legacy flow regressions, and unrelated worktree files.
- [ ] Report code/test/build results separately from deployment and live browser E2E. Do not claim deployment or real acceptance unless those actions were actually completed.

## Task 7: Show Configurable Uploaded-Data Preview

**Files:**

- Create: `source/dts-platform-webapp/src/pages/foundation/access/shared/filePreview.ts`
- Create: `source/dts-platform-webapp/src/pages/foundation/access/shared/filePreview.test.ts`
- Create: `source/dts-platform-webapp/src/pages/foundation/access/FileDataPreview.tsx`
- Modify: `source/dts-platform-webapp/src/pages/foundation/access/FileAccessStep.tsx`

**Interfaces:**

- Consumes: `ManagedFileUploadResult.preview?: string[][]` and the current editable `ManagedFileColumn[]`.
- Produces: `normalizeFilePreviewLimit(value: unknown): number`, `buildFilePreviewRows(preview, limit)`, and a presentation-only `FileDataPreview` component.

**Steps:**

- [ ] Write failing tests proving the default limit is 10, values are clamped to 1～20, preview rows are sliced without mutation, and short/empty samples remain valid.
- [ ] Run `pnpm exec vitest run src/pages/foundation/access/shared/filePreview.test.ts` and confirm failure because the helper does not exist.
- [ ] Implement the pure preview-limit and row-slicing helpers.
- [ ] Run the helper test and confirm it passes.
- [ ] Add `FileDataPreview` below the existing field mapping editor. Render current edited field names as headers, use a 1～20 row-count selector defaulting to 10, and enable horizontal table scrolling.
- [ ] Update the existing access-step source contract/component test to require the preview component without adding a new route or page.
- [ ] Run focused access tests, `pnpm build`, Chrome 95 static checks, `git diff --check`, and `gitnexus_detect_changes()`.
