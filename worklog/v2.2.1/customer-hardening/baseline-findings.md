# Baseline Findings

## Build Findings

- `dts-admin`
  - Command: `mvn -f source/dts-admin/pom.xml -DskipTests compile`
  - Result: `PASS`
  - Notes: compile succeeded; baseline has deprecation warnings in `RestTemplateBuilder` usage but no blocker.
- `dts-platform`
  - Command: `mvn -f source/dts-platform/pom.xml -DskipTests compile`
  - Result: `PASS with warnings`
  - Notes: build succeeded, but Maven enforcer reported dependency convergence problems for:
    - `commons-io`
    - `commons-compress`
    - `nimbus-jose-jwt`
- `dts-admin-webapp`
  - Command: `pnpm -C source/dts-admin-webapp build`
  - Result: `PASS with warnings`
  - Notes:
    - dynamic import and static import overlap warnings remain
    - several large production chunks are still emitted
- `dts-platform-webapp`
  - Command: `pnpm -C source/dts-platform-webapp build`
  - Result: `PASS with warnings`
  - Notes:
    - dynamic import and static import overlap warnings remain
    - route bundle size is still large for key pages

## Runtime Findings

- Not executed yet.
- Next baseline step should be a focused walkthrough of the `platform development / modeling / ETL` main flow.

## UI Findings

- Build output shows large route chunks in customer-critical pages such as:
  - `SqlModelingPage`
  - `TransformCreatePage`
  - `QueryWorkbenchPage`
- Both webapps still show mixed static/dynamic import warnings, which weakens route-level code splitting and can affect first-load performance on onsite environments.

## Candidate First-Wave Fixes

- `BUG-001`: done; `dts-platform` dependency convergence is now aligned and the regression script passes
- `UI-001`: improve platform route chunking and first-screen load behavior for development-center pages
