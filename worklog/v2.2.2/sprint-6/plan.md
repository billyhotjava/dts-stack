# ELT Stability Governance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为“数据接入中心 + 数据开发中心”建立系统诊断、质量基线和首批稳定性修复闭环，并让关键异常路径进入可重复回归状态。

**Architecture:** 以 `dts-ingestion`、`dts-platform`、`dts-platform-webapp` 和 `tests/web-e2e` 为核心，先完成诊断与基线沉淀，再对接入中心和开发中心分别实施首批 P0/P1 修复，最后以统一门禁和人工验收清单收口。

**Tech Stack:** Java 21, Spring Boot, Maven Surefire, React + Vite, Playwright, Airflow, dbt, Addax

---

### Task 1: 诊断接入中心主链路与异常链路

**Files:**
- Create: `worklog/v2.2.2/sprint-6/req/elt-architecture-and-dependency-map.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-001-ingestion-architecture-and-exception-diagnosis.md`
- Modify: `worklog/v2.2.2/sprint-6/README.md`

**Step 1: Write the diagnostic checklist**

- 列出接入中心关键入口：
  - `TransformPage`
  - `TransformCreatePage`
  - `TransformDetailPage`
  - `TransformExecutionHistoryPage`
- 列出后端主链：
  - `IngestionTaskResource`
  - `IngestionTaskService`
  - `DagPreheatService`
  - `AirflowAdapter`
  - `AirflowExecutionSyncService`

**Step 2: Verify current code references**

Run:
```bash
rg -n "Transform(Create|Detail|ExecutionHistory|Page)|IngestionTask(Resource|Service)|DagPreheatService" \
  source/dts-platform-webapp/src source/dts-ingestion/src
```
Expected: 能定位接入中心主链的关键文件

**Step 3: Write the diagnostic material**

- 输出主链路图
- 输出异常链路图
- 标记高风险依赖点和现有痛点

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/README.md worklog/v2.2.2/sprint-6/req/elt-architecture-and-dependency-map.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-001-ingestion-architecture-and-exception-diagnosis.md
git commit -m "docs: capture ingestion architecture and exception diagnosis"
```

### Task 2: 诊断开发中心主链路与异常链路

**Files:**
- Modify: `worklog/v2.2.2/sprint-6/req/elt-architecture-and-dependency-map.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-002-development-center-architecture-and-exception-diagnosis.md`

**Step 1: Write the diagnostic checklist**

- 列出开发中心关键入口：
  - `SqlModelingPage`
  - `QueryWorkbenchPage`
  - `OrchestrationPage`
  - `ScriptStudioPage`
- 列出后端主链：
  - `EtlResource`
  - `DbtDagService`
  - `DbtRunResultService`
  - `DbtOutputRelationService`
  - `RollbackCascadeService`

**Step 2: Verify current code references**

Run:
```bash
rg -n "SqlModelingPage|QueryWorkbenchPage|OrchestrationPage|ScriptStudioPage|EtlResource|DbtDagService|DbtRunResultService|RollbackCascadeService" \
  source/dts-platform-webapp/src source/dts-platform/src
```
Expected: 能定位开发中心主链的关键文件

**Step 3: Write the diagnostic material**

- 补全开发中心主链和异常链路
- 标记与 Airflow/dbt/回滚/门禁的关键耦合点

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/req/elt-architecture-and-dependency-map.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-002-development-center-architecture-and-exception-diagnosis.md
git commit -m "docs: capture development center architecture and exception diagnosis"
```

### Task 3: 建立质量基线与回归门禁

**Files:**
- Create: `worklog/v2.2.2/sprint-6/req/elt-quality-baseline-gap-analysis.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-003-quality-baseline-and-regression-gates.md`

**Step 1: Write the failing baseline definition**

- 明确哪些测试当前缺失：
  - 接入中心前端行为测试
  - 开发中心前端行为测试
  - 接入中心 E2E 执行链
  - 开发中心 E2E 发布链

**Step 2: Verify existing tests**

Run:
```bash
find source/dts-ingestion/src/test/java -type f | sort
find source/dts-platform/src/test/java -type f | sort
find tests/web-e2e/specs -type f | sort
```
Expected: 能确认现有测试覆盖点与缺口

**Step 3: Write the baseline document**

- 列出当前已有测试
- 列出缺口
- 定义最小门禁命令

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/req/elt-quality-baseline-gap-analysis.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-003-quality-baseline-and-regression-gates.md
git commit -m "docs: define ELT quality baseline and regression gates"
```

### Task 4: 定义接入中心首批修复范围

**Files:**
- Create: `worklog/v2.2.2/sprint-6/req/elt-first-batch-fix-requirements.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-004-ingestion-first-batch-stability-fixes.md`

**Step 1: Write the fix scope**

- 聚焦问题：
  - DAG 预热 / ready 失败传播
  - 执行中状态同步
  - 日志回显可靠性
  - 边缘入口一致性

**Step 2: Verify related tests**

Run:
```bash
cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test
```
Expected: 暴露当前接入中心稳定性相关测试现状

**Step 3: Write the requirements**

- 明确首批修复目标
- 明确非目标
- 明确验收标准

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/req/elt-first-batch-fix-requirements.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-004-ingestion-first-batch-stability-fixes.md
git commit -m "docs: define first batch ingestion stability fixes"
```

### Task 5: 定义开发中心首批修复范围

**Files:**
- Modify: `worklog/v2.2.2/sprint-6/req/elt-first-batch-fix-requirements.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-005-development-center-first-batch-stability-fixes.md`

**Step 1: Write the fix scope**

- 聚焦问题：
  - dbt / Airflow 触发失败传播
  - compile / test / build 状态同步
  - 发布门禁 / 重建 / 回滚一致性

**Step 2: Verify related tests**

Run:
```bash
cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest,DbtQualityGateServiceTest,DbtReleaseGateServiceTest test
```
Expected: 暴露当前开发中心稳定性相关测试现状

**Step 3: Write the requirements**

- 补齐开发中心的首批修复范围和验收标准

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/req/elt-first-batch-fix-requirements.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-005-development-center-first-batch-stability-fixes.md
git commit -m "docs: define first batch development center stability fixes"
```

### Task 6: 建立 E2E 冒烟与人工异常路径验收清单

**Files:**
- Create: `worklog/v2.2.2/sprint-6/req/elt-stability-findings-and-priority.md`
- Create: `worklog/v2.2.2/sprint-6/tasks/ELT-006-e2e-smoke-and-manual-failure-runbook.md`

**Step 1: Write the failing acceptance checklist**

- 接入中心：
  - 创建任务
  - 执行任务
  - 查看状态/日志/历史
- 开发中心：
  - compile
  - test
  - build
  - 查看运行记录与错误

**Step 2: Verify current E2E coverage**

Run:
```bash
find tests/web-e2e/specs -type f | sort
```
Expected: 能确认当前没有成体系覆盖 ELT 两大中心异常路径

**Step 3: Write the runbook**

- 给出新增 E2E 冒烟建议
- 给出人工异常路径清单
- 给出问题优先级列表

**Step 4: Commit**

```bash
git add worklog/v2.2.2/sprint-6/req/elt-stability-findings-and-priority.md \
  worklog/v2.2.2/sprint-6/tasks/ELT-006-e2e-smoke-and-manual-failure-runbook.md
git commit -m "docs: define ELT smoke and manual failure runbook"
```
