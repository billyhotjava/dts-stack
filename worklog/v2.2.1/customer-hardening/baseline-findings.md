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

## Test Findings

- `dts-platform`
  - Command: `./mvnw -ntp -Dtest=SecuritySqlRewriterTest,QueryDatasetServiceTest,IngestionTaskProxyResourceTest test`
  - Result: `PASS`
  - Notes:
    - directly affected regression tests are green after the `2.2.1` downgrade hardening
- `dts-platform`
  - Command: `./mvnw -ntp test`
  - Result: `PASS`
  - Notes:
    - surefire phase is now stable on the customer branch
    - latest run finished with `Tests run: 65, Failures: 0, Errors: 0, Skipped: 0`
- `dts-platform`
  - Command: `npm run backend:unit:test`
  - Result: `FAIL in current sandbox`
  - Notes:
    - this script runs Maven `verify`, not only unit tests
    - surefire/unit tests are green, but later phases still fail because:
      - Testcontainers integration tests cannot access `/var/run/docker.sock`
      - checkstyle dependency resolution cannot write under `/home/billy/.m2/repository/com/puppycrawl`
    - remaining failures are environment and permission related, not the original unit-test baseline issue

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
- `BUG-002`: done; `dts-platform` unit-test baseline is stable after Mockito agent wiring, stale target cleanup, and test updates
- `UI-001`: done; route-splitting overlap warnings for `workbench`, `sys/login`, and `sys/error` are removed
- next candidate should be selected from:
  - runtime main-flow walkthrough findings
  - integration environment enablement if onsite validation needs Docker-backed IT coverage
  - large chunk optimization if onsite first-load performance is still a real issue
