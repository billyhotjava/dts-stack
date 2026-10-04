# 项目运营管理大屏指标对账屏 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为“项目运营管理大屏”新增第 5 屏“指标对账”，展示全量 34 项系统值、Excel 值及差异结果。

**Architecture:** 后端新增 `metrics-compare` 聚合接口，系统值复用现有 `screenMetricsOverview` 逻辑，Excel 值由 ODS 明细聚合器计算，并保留重复校验项；前端模板新增第 5 屏并接入 public/runtime 链路。

**Tech Stack:** Spring Boot, Jackson, React, TypeScript, Vite, screen template runtime

---

### Task 1: 补充后端接口测试

**Files:**
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`

**Step 1: Write the failing test**

- 为 `/api/project-cockpit/screen/metrics-compare` 增加断言：
  - `summary.metricTotal`
  - `groups[0].items[0].systemValue`
  - `groups[0].items[0].excelValue`
  - `mismatchTop`

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`

**Step 3: Write minimal implementation**

- 先补 Resource 路由与 Service 空实现，确保测试可编译。

**Step 4: Run test to verify it passes or fails deeper**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`

### Task 2: 实现后端 metrics-compare 聚合

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`

**Step 1: Add service method**

- 新增 `screenMetricsCompare(Filters filters)`。
- 新增内部对账模型与聚合 helper。

**Step 2: Implement all metrics**

- 实现全量 34 项指标的：
  - 系统值抽取
  - Excel 聚合值
  - 差值/偏差率/一致性
- 对重复校验项保留独立展示 key，但复用同一底层口径值

**Step 3: Add summary + mismatchTop**

- 生成摘要卡与差异榜数据。

**Step 4: Run test**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`

### Task 3: 接入 public 代理与前端 runtime 改写

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/PublicResource.java`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.test.ts`

**Step 1: Add public endpoint**

- 新增 `/api/public/screen/{uuid}/project-cockpit/metrics-compare`

**Step 2: Extend runtime rewrite**

- 让 public 模式下 `/project-cockpit/screen/metrics-compare` 自动改写。

**Step 3: Add tests**

- 补 public URL rewrite 断言。

**Step 4: Run tests**

Run:
- `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT,ScreenResourceIT test`
- `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts`

### Task 4: 新增第 5 屏模板

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Add compare page helpers**

- 增加摘要卡 helper
- 增加分组表格 helper
- 增加异常榜 helper

**Step 2: Add page**

- 新增 `buildMetricsComparePage()`
- 将模板从 4 屏改为 5 屏
- 更新模板说明

**Step 3: Update tests**

- 断言新增第 5 屏存在
- 断言其数据源绑定到 `metrics-compare`

**Step 4: Run tests**

Run:
- `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 5: 完整验证

**Files:**
- No code changes expected

**Step 1: Typecheck/build**

Run:
- `pnpm -C source/dts-analytics-webapp/modern typecheck`
- `pnpm -C source/dts-analytics-webapp/modern build`

**Step 2: Backend targeted tests**

Run:
- `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT,ScreenResourceIT test`

**Step 3: Review**

- 确认不覆盖现有未提交修改
- 确认 public/preview/editor 三种模式都能命中新接口
