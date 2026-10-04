# DTS Frontend Patterns

## Webapp Ownership

- Admin console: users, roles, departments, PKI, audit administration, MDM, system security.
- Platform console: data sources, modeling, dbt, API publishing, workbench, SQL IDE.
- Analytics modern: dashboards, visualization, analysis workflows.

## Common UI Expectations

- Tables: server-side pagination for large datasets, explicit loading state, empty state, error state, and filter reset.
- Forms: validate required fields before submit and show backend errors without losing user input.
- Destructive actions: confirm, show affected resource, and refresh data after success.
- Long-running jobs: show state, last run time, logs or diagnostics link, and retry path.
- Import/export: show format requirements, progress/result summary, and partial failure details.

## Permissions

- Menu visibility must match route access.
- Route guards must match backend permissions.
- Button visibility should not be the only protection.
- Department or tenant context changes must refresh scoped data.

## Modeling And SQL IDE

- Preserve multi-tab or editor persistence behavior.
- Keep schema browser, result grid, logs, and chart/pivot features discoverable but not visually dominant.
- SQL execution must expose failure details without leaking credentials.
- For model release/run flows, make async state and warnings visible.

## Validation

- `source/dts-admin-webapp`: `pnpm build`
- `source/dts-platform-webapp`: `pnpm build`
- `source/dts-analytics-webapp/modern`: `pnpm typecheck` and `pnpm build`
- Add screenshot or E2E evidence for changed primary workflows.
