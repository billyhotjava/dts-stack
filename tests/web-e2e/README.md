# DTS Web E2E

## Scope

`tests/web-e2e` 是 DTS 真实浏览器自动化入口，使用 Playwright 覆盖三端高价值 UI 流程：

- `platform`：AI 助手、审批、trace
- `analytics`：AI 助手、查询结果、图表/大屏动作
- `admin`：AI 配置、Pack 管理
- `auth`：登录、回跳、storage state

## Core Suite

`web-e2e-core` 用于 PR gate，要求高价值、低波动、真实 UI。

| Spec | Owner | 业务目标 |
|---|---|---|
| `specs/core/auth-gateway.spec.ts` | `platform-webapp` / `auth-gateway` | 保证登录、回跳、预授权 storage state 可用 |
| `specs/core/platform-ai-assistant.spec.ts` | `platform-webapp` / `dts-platform` | 保证 AI 助手对话、审批、会话管理真实可用 |
| `specs/core/analytics-ai-query.spec.ts` | `analytics-webapp` | 保证 AI 助手、查询结果、大屏发布链路真实可用 |
| `specs/core/admin-ai-pack.spec.ts` | `admin-webapp` | 保证 AI 配置和 Pack 管理关键动作真实可用 |
| `specs/contracts/testid-contract.spec.ts` | `frontend shared QA` | 保证关键 `data-testid` 契约不被破坏 |

## Full Suite

`web-e2e-full` 在 `customer/2.2.1` 上不再复制 `core` 套件定义，而是包含：

- `web-e2e-core` 的 5 条核心用例
- `biz-e2e` 的 5 条业务流用例

这样可以保持 `v2.5.0` 的覆盖目标，同时避免 `full` 与 `core` 重复。

## Biz Suite

`biz-e2e` 聚焦五条业务链路：

- `ERP -> Metadata -> Visible Data`
- `AI Modeling -> Approval -> Materialization`
- `Analytics Query -> Dashboard -> Screen Publish`
- `Auth Gateway -> RBAC -> HITL`
- `Quality Rule -> Alert -> Remediation`

## Artifact Rules

失败工件统一保存在以下目录：

- HTML 报告：`tests/web-e2e/reports/html/<case>`
- screenshot / video / trace：`tests/web-e2e/reports/artifacts/<case>`
- suite 级 JSON / Markdown / JUnit：`tests/reports/<timestamp>-<suite>.{json,md,xml}`

默认情况下：

- `screenshot=only-on-failure`
- `video=retain-on-failure`
- `trace=retain-on-failure`

可以通过环境变量覆盖目录：

- `DTS_WEB_E2E_HTML_REPORT_DIR`
- `DTS_WEB_E2E_ARTIFACTS_DIR`

## Commands

核心套件干跑：

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
```

核心套件执行：

```bash
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
```

全量 Web 回归：

```bash
python3 tests/run_suite.py --suite web-e2e-full --fail-fast
```

业务链路回归：

```bash
python3 tests/run_suite.py --suite biz-e2e --fail-fast
```

隔离区用例：

```bash
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --fail-fast
```
