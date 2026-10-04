# No-SQL First Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a no-SQL first-report journey that helps a new user connect one business table and produce a report through existing DTS pages.

**Architecture:** Reuse the existing workbench and golden-chain surfaces instead of adding a new shell. Add a first-report entry card on the workbench home and a guided section in `DataManagementWorkbenchPage` that routes to existing data source, governance, semantic BI, dashboard, and ops pages.

**Tech Stack:** React, TypeScript, Ant Design, lucide-react, existing Node source-contract tests.

---

### Task 1: Pin The First-Report Journey Contract

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/services/BusinessConsumptionPage.source-contract.test.ts`

- [x] **Step 1: Extend the workbench route contract**

Add `/workbench?section=data-management&journey=first-report` to `expectedWorkbenchRoutes` and assert the registry contains customer-facing first-report copy:

```ts
assert.match(REGISTRY_SOURCE, /first-report/);
assert.match(REGISTRY_SOURCE, /首张报表/);
assert.match(REGISTRY_SOURCE, /接入一张业务表并生成报表/);
```

- [x] **Step 2: Extend the consumption/workbench contract**

Assert `DataManagementWorkbenchPage.tsx` includes the first-report guide and keeps the default journey no-SQL:

```ts
for (const label of ["接入一张业务表", "选择业务表", "生成同步任务", "生成报表", "查看运行证据"]) {
  assert.match(source, new RegExp(label));
}
assert.doesNotMatch(source, /预览 SQL|模板参数 \\(JSON\\)|dbt source/);
```

- [x] **Step 3: Run the focused source-contract tests**

Run:

```bash
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/services/BusinessConsumptionPage.source-contract.test.ts
```

Expected: tests fail before implementation because the first-report copy is missing.

### Task 2: Add The Workbench Entry Card

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/workbench/workbenchComponentRegistry.tsx`

- [x] **Step 1: Add the icon import**

Add `FileText` to the lucide-react import list.

- [x] **Step 2: Add the first-report entry**

Insert an entry near the existing data-source and golden-chain cards:

```tsx
entry({
  key: "first-report",
  title: "首张报表",
  description: "接入一张业务表并生成报表",
  actionText: "开始首单",
  route: "/workbench?section=data-management&journey=first-report",
  icon: FileText,
}),
```

- [x] **Step 3: Run the workbench contract test**

Run:

```bash
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts
```

Expected: the workbench entry assertions pass.

### Task 3: Add The Guided Section To The Data Management Workbench

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/workbench/DataManagementWorkbenchPage.tsx`

- [x] **Step 1: Add guide step data**

Define `firstReportJourneySteps` near the status helpers:

```tsx
const firstReportJourneySteps = [
  { title: "接入一张业务表", desc: "从连接器目录或数据源连接开始，完成连接测试。", route: "/foundation/data-sources", action: "配置数据源" },
  { title: "选择业务表", desc: "探测表结构，选择要进入治理链路的业务表和字段。", route: "/foundation/data-sources", action: "探测业务表" },
  { title: "生成同步任务", desc: "确认字段、同步方式和预检结果，生成可运行的同步任务。", route: "/explore/etl/transform", action: "查看同步任务" },
  { title: "生成报表", desc: "进入语义探索，选择指标、维度、筛选条件和图形。", route: "/bi/explore", action: "创建分析卡片" },
  { title: "查看运行证据", desc: "到运行概览确认任务成功率、告警和补数记录。", route: "/ops/overview", action: "查看运行证据" },
];
```

- [x] **Step 2: Render the guide before theme cards**

Add a `Card` titled `新手首单：接入业务表生成报表` with five bordered step rows and route buttons. Use `router.push(step.route)` for every action.

- [x] **Step 3: Keep technical copy out of the default guide**

Do not include visible strings `SQL`, `JSON`, `dbt`, `sourceId`, `DDL`, or `ODS 映射与 dbt source` in the new guide.

- [x] **Step 4: Run the consumption/workbench contract test**

Run:

```bash
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/services/BusinessConsumptionPage.source-contract.test.ts
```

Expected: the no-SQL first-report assertions pass.

### Task 4: Verify Scope

**Files:**
- Inspect: `source/dts-platform-webapp/src/pages/workbench/DataManagementWorkbenchPage.tsx`
- Inspect: `source/dts-platform-webapp/src/pages/workbench/workbenchComponentRegistry.tsx`
- Inspect: `source/dts-platform-webapp/src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts`
- Inspect: `source/dts-platform-webapp/src/pages/services/BusinessConsumptionPage.source-contract.test.ts`

- [x] **Step 1: Run focused tests together**

Run:

```bash
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts
node --test --experimental-strip-types source/dts-platform-webapp/src/pages/services/BusinessConsumptionPage.source-contract.test.ts
```

Expected: both test files pass.

- [x] **Step 2: Check formatting drift**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [x] **Step 3: Run GitNexus change detection**

Run GitNexus `detect_changes(scope: "all", repo: "s10-stack")`.

Expected: only the workbench first-report UI and source-contract tests are affected.
