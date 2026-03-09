# Sprint-2: 三端 Web E2E 回迁与 Playwright 自动化落地

## 目标

在 `customer/2.2.1` 分支上完成 `v2.5.0` Web 自动化测试体系的首轮回迁，并把三端 `platform`、`admin`、`analytics` 纳入统一的 Playwright 回归入口：

1. **回迁 Web 测试产品层** — 恢复 `tests/` 与 `tests/web-e2e/`，保留 `v2.5.0` 的 page object、mock-first、suite 编排与报告产物模型
2. **适配当前仓库布局** — 校正 analytics 工程位于 `source/dts-analytics-webapp/modern` 的现状，剥离旧版非 Web release/infra 测试噪音
3. **形成可继续演进的自动化基线** — 让 `web-e2e-core`、`biz-e2e`、`web-e2e-full`、`web-e2e-quarantine` 四组 suite 可发现、可执行、可出工件

## 范围

### 测试工程
- `tests/` — Web-only suite 注册、runner、gate、报告目录、环境变量模板
- `tests/web-e2e/` — Playwright 项目、fixtures、page objects、support、specs、artifact 约定

### 应用适配
- `source/dts-platform-webapp` — platform Web E2E dev server 与核心/业务流用例
- `source/dts-admin-webapp` — admin Web E2E dev server 与核心用例
- `source/dts-analytics-webapp/modern` — analytics Web E2E dev server 与核心/业务流用例

### 交付文档
- `worklog/v2.2.1/sprint-2/` — Sprint README、task 卡片、集成测试说明
- `docs/plans/2026-03-09-v221-web-e2e-migration-*.md` — 设计与实施计划

## Task 列表

### 批次一：基础设施回迁（WE-001 ~ WE-004）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WE-001 | `tests/` 顶层骨架与 Web-only 文档恢复 | 测试工程 | 0.5天 | DONE |
| WE-002 | Web suite registry 与 gate 编排裁剪 | 测试工程 | 0.5天 | DONE |
| WE-003 | Playwright runtime 与 project scaffold 回迁 | 测试工程 | 1天 | DONE |
| WE-004 | auth/storage-state/mock server 基础支撑适配 | 测试工程 | 0.5天 | DONE |

### 批次二：Platform 端迁移（WE-005 ~ WE-006）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WE-005 | Platform page objects、core spec 与 dev server 接线 | 前端测试 | 1天 | DONE |
| WE-006 | Platform 业务流 spec 与 mock 支撑迁移 | 前端测试 | 1天 | DONE |

### 批次三：Admin 与 Analytics 端迁移（WE-007 ~ WE-009）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WE-007 | Admin page objects、core spec 与 dev server 接线 | 前端测试 | 0.5天 | DONE |
| WE-008 | Analytics runtime 路径适配与 page objects 迁移 | 前端测试 | 0.5天 | DONE |
| WE-009 | Analytics core/biz spec 迁移 | 前端测试 | 0.5天 | DONE |

### 批次四：跨端契约与 Suite 语义（WE-010 ~ WE-012）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WE-010 | 跨端 auth/testid 合同用例迁移 | 测试工程 | 0.5天 | DONE |
| WE-011 | `core` / `biz` / `full` / `quarantine` suite 语义重组 | 测试工程 | 0.5天 | DONE |
| WE-012 | Gate 入口、报告目录与运行文档收口 | 测试工程 | 0.5天 | DONE |

### 批次五：Worklog 与验证（WE-013 ~ WE-014）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WE-013 | Sprint-2 README、task 卡片、IT 说明落地 | 文档 | 0.5天 | DONE |
| WE-014 | dry-run、构建、Playwright 首轮执行验证 | 验证 | 1天 | DONE |

## 本 Sprint 不做

- 不把 `v2.5.0` 的 Helm、GitOps、offline bundle、release gate、audit 契约测试一起回带
- 不把本轮目标扩大成真实后端黑盒验收；当前仍以 mock-first 的浏览器自动化为主
- 不做 CI 平台接线、定时任务接线与 flaky 管理自动化
- 不重写三端业务页面以适配测试；仅做 Web E2E 迁移所需的最小适配

## 集成测试

`it/` 目录记录本 Sprint 的集成验证入口与执行方式，覆盖：
- suite discoverability：`web-e2e-core`、`web-e2e-full`、`biz-e2e`、`web-e2e-quarantine`
- 三端前端构建：platform、admin、analytics modern
- Playwright spec discoverability：`playwright test --list`
- 浏览器执行：`web-e2e-core`、`biz-e2e`、`web-e2e-full`、`web-e2e-quarantine`
- 工件产出：HTML report、artifacts、runner summary

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：
- 空白 = 未开始
- WIP = 进行中
- DONE = 已完成
- BLOCK = 阻塞

当前 Sprint 收口结果：
- `web-e2e-core` 已真实执行通过
- `biz-e2e` 已真实执行通过
- `web-e2e-full` 已真实执行通过
- `web-e2e-quarantine` 已真实执行通过
- 若在受限执行环境中运行，需要允许本地 `localhost` 端口绑定，否则 mock auth server 无法启动
