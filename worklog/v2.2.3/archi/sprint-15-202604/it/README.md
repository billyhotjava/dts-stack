# Sprint-15 集成测试证据

**状态**: PLACEHOLDER（验证阶段由 F6/T03、F6/T04 填充）

## 证据清单（交付前必须齐备）

### 后端接口证据（F1）

- [ ] `curl-leader-overview-ALL.json`：`GET /api/workbench/leader-overview?scope=ALL&timeRange=MONTH`（所领导登录态）原始响应。
- [ ] `curl-leader-overview-DEPT.json`：同上，部门领导登录态，`scope=DEPT&deptCode=<own>`。
- [ ] `curl-leader-overview-MINE.json`：同上，员工登录态，`scope=MINE`。
- [ ] `curl-downgrade-emp-requesting-ALL.json`：员工强行请求 `scope=ALL`，响应的 `scope` 字段应为 `MINE`。
- [ ] `curl-bizDomain-filter.json`：带 `bizDomain=FIN` 的响应，验证 KPI / TOP 报表 / TOP 资产都被过滤。
- [ ] `postgres-schema-after-drop.txt`：`\d portal_user_favorite` 执行应报错"relation does not exist"。

### 前端截图 / E2E 证据（F5, F6）

- [ ] `screens/inst-leader-overview-month.png`：所领导首屏本月视图。
- [ ] `screens/inst-leader-overview-quarter.png`：切到本季后截图。
- [ ] `screens/dept-leader-overview.png`：部门领导视图，验证部门下拉锁定。
- [ ] `screens/emp-overview.png`：员工视图。
- [ ] `screens/bizDomain-api-fail.png`：mock /catalog/domains 500 后截图，业务域下拉消失。
- [ ] `e2e/trace.zip`：Playwright 所领导整路流 trace。
- [ ] `coverage-workbench.json`：前端 workbench 目录下覆盖率 summary。

### 审计证据（F6/T01）

- [ ] `audit-events-sample.txt`：从审计表查询近期 `WORKBENCH_*` 事件示例（`select event, payload, created_date from audit_event where event like 'WORKBENCH_%' order by created_date desc limit 20;`）。

### Liquibase 证据（F2/T01）

- [ ] `liquibase-update.log`：执行 `./mvnw liquibase:update` 日志，含 changeset `20260424-1000-drop-portal-user-favorite` 成功执行。
- [ ] `checksum.matched.txt`：`sha256sum` 对比 dump 文件与 `assets/dump.checksum`。

## 回滚演练

- [ ] `rollback-dry-run.log`：在 dev 环境执行 Liquibase rollback → 表恢复 → 手动跑 `portal_user_favorite_full.sql` → 数据恢复；记录整个过程。

## 签收人

- 前端：__________ (date: ________)
- 后端：__________ (date: ________)
- 运维：__________ (date: ________)
- QA：__________ (date: ________)

## How to run

### Vitest coverage gate (F6/T02)

Scoped run that produces `coverage/coverage-summary.json` for the `src/pages/workbench/**` zone:

```bash
pnpm --filter dts-platform-webapp exec vitest run src/pages/workbench \
  --coverage --coverage.provider=v8 \
  --coverage.include='src/pages/workbench/**' \
  --coverage.exclude='**/*.test.*' \
  --coverage.exclude='**/README.md' \
  --coverage.reporter=json-summary \
  --coverage.reporter=text \
  --coverage.reporter=html \
  --testTimeout=20000
```

The trimmed slice is committed at `coverage-workbench.json`. Red-line gates:

| Zone | Target | Last run |
|---|---|---|
| `hooks/` | ≥ 90% lines | 100% |
| `components/` | ≥ 80% lines | 90.74% |
| `LeaderOverviewPage.tsx` | ≥ 75% lines | 96.55% |

### Vitest integration test (F6/T03)

```bash
pnpm --filter dts-platform-webapp exec vitest run \
  src/pages/workbench/LeaderOverviewPage.integration.test.tsx
```

This file renders the page with the **real** child components (KpiRow, TopReportsBlock,
CoreAssetsBlock, DomainMatrix, WorkbenchFilterBar) and only mocks the data services. It
complements the existing `LeaderOverviewPage.test.tsx`, which keeps fast page-level
behavior assertions with stubbed children.

### Playwright E2E (F6/T04)

Spec file: `source/dts-platform-webapp/e2e/workbench-leader-overview.spec.ts`.

Local run:

```bash
pnpm --filter dts-platform-webapp e2e -- workbench-leader-overview.spec.ts
# Or for the UI runner:
pnpm --filter dts-platform-webapp e2e:ui -- workbench-leader-overview.spec.ts
```

Required env (consumed by `e2e/auth.setup.ts`):

| Var | Purpose | Default |
|---|---|---|
| `E2E_BASE_URL` | Base URL the spec hits and where login API lives | `http://localhost:3001` |
| `E2E_USERNAME` | Login user (must have `ROLE_INST_LEADER`) | `opadmin` |
| `E2E_PASSWORD` | Password for the above | `opadmin123` |
| `E2E_DEPT_LEADER_AUTH` | Optional path to a dept-leader `storageState.json`; the third test self-skips when unset | unset |

Artifacts on each run land here:

- `source/dts-platform-webapp/test-results/` — per-test trace.zip / screenshots / videos
- `source/dts-platform-webapp/playwright-report/` — HTML report (`open` it locally)

To copy artifacts into this sprint's evidence folder:

```bash
mkdir -p worklog/v2.2.3/sprint-15-202604/assets/e2e
cp source/dts-platform-webapp/test-results/*workbench-leader-overview*/trace.zip \
   worklog/v2.2.3/sprint-15-202604/assets/e2e/trace.zip
cp source/dts-platform-webapp/test-results/*workbench-leader-overview*/test-finished-1.png \
   worklog/v2.2.3/sprint-15-202604/assets/e2e/inst-leader-overview-month.png
```

> Note: this sandbox does not have a live backend, so the Playwright suite was not
> executed here. The spec is type-checked and discoverable via
> `pnpm --filter dts-platform-webapp exec playwright test --list`. QA should run it
> against the dev cluster before sign-off.
