# Foundation Temporary Features Retirement Design

**Date:** 2026-03-28

**Scope:** Retire the temporary `项目主体域接入` and `专题绑定中心` features from `dts-platform` and `dts-platform-webapp`, including UI entry points, routes, backend APIs, runtime integrations, tests, and database objects.

## Goal

Remove both temporary features as runnable product capabilities, eliminate their backend and frontend dependencies, and clean up the related database tables through a forward-only Liquibase migration.

## Confirmed Requirements

- Remove the two feature entries from the product UI.
- Remove the corresponding frontend pages, routes, API clients, and jump links.
- Remove the corresponding backend REST resources, service logic, repositories, domain objects, and tests.
- Remove the supporting database objects.
- Do not rewrite historical Liquibase execution history in a way that breaks upgraded environments.

## Current Footprint

### Frontend

- Static routes:
  - `/foundation/project-cockpit-imports`
  - `/foundation/topic-bindings`
- Pages:
  - `ProjectCockpitImportsPage`
  - `TopicBindingCenterPage`
- Shared API client:
  - `src/api/services/topicBindingService.ts`
- Entry points and links:
  - `DataSourcesPage`
  - `SqlModelingPage`
- Feature-specific helpers and tests under `src/pages/foundation/`

### Backend

- Project cockpit import flow:
  - `ExcelImportResource`
  - project-cockpit methods inside `ExcelImportService`
  - `InfraProjectCockpitBatch/Row/Issue` domain and repositories
- Topic binding flow:
  - `TopicBindingResource`
  - `service/topic/*`
  - `domain/topic/*`
  - `repository/topic/*`
- Runtime coupling:
  - `EtlResource` and related tests depend on `TopicBindingRuntimeService`
  - `SqlModelingPage` consumes topic binding diagnostics

### Database

- `infra_project_cockpit_batch`
- `infra_project_cockpit_row`
- `infra_project_cockpit_issue`
- `topic_template`
- `topic_template_entity`
- `topic_binding`

Historical create-table changelogs already exist:

- `20260316_01_project_cockpit_import.xml`
- `20260316_02_topic_binding_center.xml`

## Recommended Approach

Use a full retirement with forward-only schema cleanup:

1. Keep the historical create-table changelogs in place.
2. Add a new Liquibase changelog that drops the six feature tables in dependency-safe order.
3. Remove all frontend and backend code paths that expose or depend on these features.
4. Remove tests that only verify retired behavior.
5. Re-run compile/build verification on both modules.

This approach preserves upgrade safety for environments that have already executed the original changelogs while still removing the tables from the deployed schema.

## Why Historical Changelogs Stay

Removing previously shipped changelogs from `master.xml` or deleting those files would create drift between repository history and deployed `DATABASECHANGELOG` state. A forward-only drop migration is operationally safer and keeps upgrade semantics clear.

## Deletion Boundaries

### Frontend retirement boundary

Delete:

- feature pages
- feature helper modules that only serve those pages
- topic binding API client
- routes and entry buttons
- modeling page links and diagnostics UI tied to topic binding status

Keep:

- unrelated foundation pages
- unrelated ETL/modeling functions

### Backend retirement boundary

Delete:

- topic binding REST resource
- topic binding services, repositories, entities, tests
- project cockpit batch load and issue preview endpoints
- project cockpit batch persistence entities, repositories, tests

Refactor:

- `ExcelImportService` to retain only generic excel parsing/error-preview behavior if still used elsewhere
- `EtlResource` to remove topic-binding runtime compilation and variable merge behavior

Keep:

- generic excel import capability if it still has non-project-cockpit callers
- unrelated ETL/dbt execution APIs

## Data and Migration Strategy

Add a new Liquibase changelog, included from `master.xml`, that:

1. Drops `topic_binding`
2. Drops `topic_template_entity`
3. Drops `topic_template`
4. Drops `infra_project_cockpit_issue`
5. Drops `infra_project_cockpit_row`
6. Drops `infra_project_cockpit_batch`

Use preconditions such as `tableExists` where appropriate so upgrades remain resilient on partially provisioned environments.

## Runtime Behavior After Retirement

- The two UI buttons disappear.
- Direct navigation to the retired frontend routes stops working because the routes are removed.
- Backend `/api/topic-bindings/**` and `/api/infra/excel-import/project-cockpit/**` endpoints are removed.
- dbt execution no longer attempts topic-binding runtime compilation.
- Logical modeling no longer shows topic-binding related diagnostics and jump links.

## Risks

- `ExcelImportService` currently mixes generic parsing behavior and project-cockpit-specific persistence. Removal must avoid breaking remaining generic import endpoints.
- `EtlResource` tests currently mock `TopicBindingRuntimeService`; removing that dependency will require coordinated test updates.
- If any external script or operator still calls the retired endpoints directly, those callers will break immediately after deploy.

## Verification Strategy

### Backend

- Compile `source/dts-platform`
- Run focused tests for:
  - `EtlResource`
  - `ExcelImportResource` or its replacement coverage
  - any residual excel-import tests
- Confirm no Spring wiring remains for removed topic/project-cockpit components

### Frontend

- Build `source/dts-platform-webapp`
- Confirm no imports remain for retired pages or `topicBindingService`
- Confirm data source page and logical modeling page render without those entries

## Non-Goals

- Replacing the retired features with a new workflow
- Preserving compatibility aliases for the retired routes or APIs
- Data migration from retired tables into another feature area
