# DTS Web E2E Tests

This directory hosts the top-level Playwright automation layer for `customer/2.2.1`.
Unit tests remain inside each source module. The restored `tests/` surface is intentionally
web-focused and does not bring back the old non-web gate suites from `v2.5.0`.

## Scope

- `platform`: auth bootstrap, AI assistant, business journeys
- `admin`: AI config and pack management
- `analytics`: AI query and publish flow
- shared browser plumbing: storage state, route mocks, artifacts, suite runner

## Layout

- `tests/run_suite.py`: unified suite runner and report writer
- `tests/run_gates.sh`: web-only gate wrapper
- `tests/suites.json`: web suite registry
- `tests/web-e2e/`: Playwright project
- `tests/reports/`: suite and gate reports

## Suite Model

- `web-e2e-core`: 5 core browser cases
- `biz-e2e`: 5 business-journey browser cases
- `web-e2e-full`: `core + biz`
- `web-e2e-quarantine`: optional quarantined browser cases

## Quick Start

Run suite discovery only:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
python3 tests/run_suite.py --suite web-e2e-full --dry-run
python3 tests/run_suite.py --suite biz-e2e --dry-run
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run
```

Run the core suite:

```bash
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
```

Run business journeys:

```bash
python3 tests/run_suite.py --suite biz-e2e --fail-fast
```

Run the full suite:

```bash
python3 tests/run_suite.py --suite web-e2e-full --fail-fast
```

Run web gates:

```bash
bash tests/run_gates.sh --gate pr
bash tests/run_gates.sh --gate nightly
bash tests/run_gates.sh --with-web-e2e-quarantine --include-optional
```

Load local env:

```bash
cp tests/.env.example tests/.env
set -a
source tests/.env
set +a
```

## Runtime Notes

- `platform` dev server points to `source/dts-platform-webapp`
- `admin` dev server points to `source/dts-admin-webapp`
- `analytics` dev server points to `source/dts-analytics-webapp/modern`
- current sprint uses a mock-first strategy:
  - real browser
  - real frontend dev server
  - local auth bootstrap
  - route mocks for unstable backend flows

## Artifacts

- suite-level JSON / Markdown / JUnit: `tests/reports/`
- Playwright HTML report: `tests/web-e2e/reports/html/<case>`
- screenshots / video / trace: `tests/web-e2e/reports/artifacts/<case>`

## Case Design Rules

- keep stable case IDs
- keep one spec per traceable user journey
- prefer mock-first determinism over brittle backend coupling in this sprint
- quarantine flaky cross-app cases instead of silently dropping them
