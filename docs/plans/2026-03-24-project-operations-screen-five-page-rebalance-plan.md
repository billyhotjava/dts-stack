# 项目运营管理大屏五屏重平衡 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将项目运营管理大屏重平衡为 5 屏，删除原第 4 屏指标全览，新增项目树与重点项目屏，并补齐 1/2/3 屏和项目看板的关键差异。

**Architecture:** 前端模板重排为 `overview / execution / risk / tree / compare` 五屏；后端新增轻量 `screen/tree` 聚合供大屏第 4 屏使用，并补齐 public/runtime 改写。执行上先删旧屏和补新第 4 屏，再增强 1/2/3 屏，最后统一回归。

**Tech Stack:** Spring Boot, Jackson, React, TypeScript, Vite, screen template runtime

---

### Task 1: 用测试锁住五屏重平衡结构

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Write the failing test**

- 断言模板页顺序变为：
  - 第 1 屏 `总体态势`
  - 第 2 屏 `执行与里程碑`
  - 第 3 屏 `风险归因`
  - 第 4 屏 `项目树与重点项目`
  - 第 5 屏 `指标对账`
- 断言不存在 `指标全览`

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 3: Write minimal implementation**

- 先调整模板页定义，删掉 `buildMetricsPage()`
- 增加占位 `buildTreePage()`

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 2: 为第 4 屏新增 screen/tree 后端接口

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`

**Step 1: Write the failing test**

- 新增 `/api/project-cockpit/screen/tree`
- 断言返回：
  - `summary`
  - `focusProjects`
  - `subprojectRows`
  - `focusNodes`

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT#projectCockpitScreenTreeShouldExposeTreeSnapshot test`

**Step 3: Write minimal implementation**

- 新增 `screenTree(Filters filters)`
- 复用 `majorProjectTree()` 的过滤和树构建逻辑
- 输出适合大屏组件直接绑定的扁平列表

**Step 4: Run test to verify it passes**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT#projectCockpitScreenTreeShouldExposeTreeSnapshot test`

### Task 3: 补齐 public screen/tree 代理与 runtime 改写

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/PublicResource.java`
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.test.ts`

**Step 1: Write the failing tests**

- 新增 public `screen/tree` 代理测试
- 新增 runtime rewrite `screen/tree` 测试

**Step 2: Run tests to verify they fail**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`
Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts`

**Step 3: Write minimal implementation**

- 新增 `/api/public/screen/{uuid}/project-cockpit/tree`
- 让 public 模式下 `/analytics/api/project-cockpit/screen/tree` 自动改写

**Step 4: Run tests to verify they pass**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`
Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts`

### Task 4: 实现第 4 屏项目树与重点项目

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Write the failing test**

- 断言第 4 屏绑定 `screen/tree`
- 断言包含：
  - `summary` 摘要卡
  - `focusProjects` 表
  - `subprojectRows` 表
  - `focusNodes` 表

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 3: Write minimal implementation**

- 实现 `buildTreePage()`
- 用 number-card + table + markdown 组合布局替代旧第 4 屏

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 5: 增强第 1 屏总体态势

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Write the failing test**

- 断言第 1 屏新增：
  - 完成率环形图
  - 健康度雷达图

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 3: Write minimal implementation**

- 在 overview 屏补环形图和雷达图
- 调整右侧摘要布局

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 6: 增强第 2 屏执行与里程碑

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Write the failing test**

- 断言第 2 屏包含：
  - 甘特图
  - 里程碑完成率环形
  - 执行摘要 KPI

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 3: Write minimal implementation**

- 重新整理 execution 屏布局
- 强化甘特图区
- 补里程碑环形和执行摘要

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 7: 增强第 3 屏风险归因

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 1: Write the failing test**

- 断言第 3 屏包含：
  - 归因热力表达
  - 治理摘要

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Step 3: Write minimal implementation**

- 复用 risk 数据源
- 增加矩阵/摘要组件

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

### Task 8: 全量回归

**Files:**
- Verify only

**Step 1: Run backend tests**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT,ScreenResourceIT test`

**Step 2: Run frontend tests**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts src/pages/screens/apiDataSourceRuntime.test.ts`

**Step 3: Run typecheck**

Run: `pnpm -C source/dts-analytics-webapp/modern typecheck`

**Step 4: Run build**

Run: `pnpm -C source/dts-analytics-webapp/modern build`
