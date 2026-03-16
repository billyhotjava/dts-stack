# Project Management Command Center Screen Implementation Plan

> 2026-03-17 更新：按客户要求拆为“两阶段交付”。
> Phase 1 为 Java 聚合接口驱动的定制演示版，优先保障客户演示。
> Phase 2 再把指标逐步迁移到 `card/sql` 查询资产，减少后续现场对 Java 发版的依赖。

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 基于 `worklog/v2.2.1/req/pm/project3.xlsx` 新增一套三屏轮播的科研项目管理专用大屏模板，Phase 1 使用 Java 聚合接口交付客户演示版，保留现有 `/project-cockpit` 页面不变，并支持 `1920x1080` 与 `2K 16:9` 无边全屏展示。

**Architecture:** Phase 1 复用 `dts-analytics-webapp` 现有 Screen Factory 作为运行壳，在 `ProjectCockpitResource/ProjectCockpitService` 上新增大屏专用聚合接口，前端新增内置模板并补齐 `api` 数据源对全局变量的运行时透传。公共运行态与预览态共用一套缩放 helper，去掉当前只缩小不放大的限制，保证 `16:9` 画布在 `1080p` 和 `2K` 上都能铺满。Phase 2 再把模板数据源从 Java 接口切换到卡片资产。

**Tech Stack:** Spring Boot, Java, React 19, TypeScript, Screen Spec v2, ECharts/DataV, Node test runner, Maven

---

### Task 1: 为大屏专用接口补失败集成测试

**Files:**
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`

**Step 1: Write the failing test**

- 在 `ProjectCockpitResourceIT` 中新增 `screen/header`、`screen/overview`、`screen/execution`、`screen/risk` 四个接口断言
- 断言返回结构至少包含：
  - `header.title`
  - `overview.kpis`
  - `execution.milestoneKpis`
  - `risk.changeKpis`

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
Expected: FAIL because `/api/project-cockpit/screen/*` endpoints do not exist yet

**Step 3: Write minimal implementation**

- 在 `ProjectCockpitResource` 中新增四个 GET 接口
- 在 `ProjectCockpitService` 中新增四个对应方法，先返回最小可用 JSON 结构
- 继续复用现有筛选参数：`programId`、`majorProjectId`、`dateFrom`、`dateTo`、`deptId`、`riskLevel`

**Step 4: Run test to verify it passes**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java \
  source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java \
  source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java
git commit -m "feat: add project command center screen endpoints"
```

### Task 2: 把 `project3.xlsx` 指标口径固化到聚合接口

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`

**Step 1: Write the failing test**

- 在集成测试里补充断言，覆盖 `project3.xlsx` 的核心语义：
  - 项目总数
  - 完成项目数
  - 按时完成数/率
  - 超期完成数
  - 超期未完成未变更数
  - 超期未完成已变更数
  - 异常待完成已变更数
  - 本周期里程碑按时完成数
  - 本周期里程碑超期完成数
  - 未完成高/中风险节点数

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
Expected: FAIL because the new screen payload does not encode the required formulas yet

**Step 3: Write minimal implementation**

- 在 `ProjectCockpitService` 中基于现有 `NodeRow` 和过滤逻辑新增大屏专用聚合 helper
- 明确区分：
  - 项目级统计
  - 节点级统计
  - 截止当前未完成节点统计
  - 本周期节点统计
- 统一在后端产出面向大屏的字段，不在模板端重复计算百分比和异常口径

**Step 4: Run test to verify it passes**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java \
  source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java
git commit -m "feat: map project3 metrics for command center screen"
```

### Task 3: 为 Screen Runtime 的 API 数据源补全局变量透传能力

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.ts`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.test.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts`

**Step 1: Write the failing test**

- 新增 helper 测试，验证：
  - `apiConfig.params` 可以用 `{{programId}}` 这类占位符注入全局变量
  - `apiConfig.body` 可以注入 `dateFrom/dateTo/riskLevel`
  - `POST` 请求可携带 `queryContext.globalVariables`
  - 空变量不会把 `undefined` 直接发到接口

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts`
Expected: FAIL because helper does not exist yet

**Step 3: Write minimal implementation**

- 提供 API 数据源运行时展开 helper
- 在 `useCardDataSource` 的 `api` 分支里：
  - 展开 URL 参数
  - 展开 body 模板
  - 自动附带 `queryContext`
- 保持现有 `card/sql/dataset/metric` 分支行为不变

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.test.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts
git commit -m "feat: support runtime variables for screen api data sources"
```

### Task 4: 新增三屏轮播的项目管理专用模板

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`

**Step 1: Write the failing test**

- 断言新模板：
  - ID 为 `project-management-command-center`
  - `width/height` 为 `1920x1080`
  - `pages.length === 3`
  - `carouselConfig.enabled === true`
  - 包含 `programId`、`majorProjectId`、`dateFrom`、`dateTo`、`deptId`、`riskLevel`
  - 第一屏、第二屏、第三屏分别绑定 `/screen/overview`、`/screen/execution`、`/screen/risk`
- 同时断言原有 `project-management-cockpit` 仍然保留

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`
Expected: FAIL because the new template is not registered yet

**Step 3: Write minimal implementation**

- 新建模板文件，拆出公共变量、公共 header 组件、三个页面组件组装函数
- 每一屏都绑定对应的大屏聚合接口
- 配置多页轮播、深色传统大屏主题、全局过滤组件、页签/轮播指示
- 在 `screenTemplates.ts` 中注册新模板，不删除原有 `project-management-cockpit`

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/projectManagementCommandCenterTemplate.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts
git commit -m "feat: add project management command center template"
```

### Task 5: 调整运行态缩放逻辑以支持 1080p 和 2K 无边铺满

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/runtimeScale.ts`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/runtimeScale.test.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenRuntimeShell.css`

**Step 1: Write the failing test**

- 验证缩放 helper：
  - `1920x1080 -> 1`
  - `2560x1440 -> 1.3333...`
  - 相同 `16:9` 比例下不额外减去安全边距
  - 公共运行态允许放大，不再被 `1` 封顶

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/runtimeScale.test.ts`
Expected: FAIL because the helper does not exist yet and runtime still clamps auto-scale to `<= 1`

**Step 3: Write minimal implementation**

- 抽出统一的缩放 helper
- 让 `PublicScreenPage` 默认走无边全屏逻辑
- `ScreenPreviewPage` 保留调试能力，但在全屏预览态复用同一套缩放计算
- 清理导致画布四周留白的固定外边距和安全区减法

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/runtimeScale.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/runtimeScale.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/runtimeScale.test.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx \
  source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx \
  source/dts-analytics-webapp/modern/src/pages/screens/ScreenRuntimeShell.css
git commit -m "fix: support fullscreen scaling for command center screens"
```

### Task 6: 联调、类型检查与交付验证

**Files:**
- Verify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`
- Verify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- Verify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`
- Verify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts`
- Verify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- Verify: `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`

**Step 1: Run backend verification**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
Expected: PASS

**Step 2: Run frontend unit tests**

Run: `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test src/pages/screens/apiDataSourceRuntime.test.ts src/pages/screens/projectManagementCommandCenterTemplate.test.ts src/pages/screens/runtimeScale.test.ts`
Expected: PASS

**Step 3: Run frontend typecheck**

Run: `pnpm -C source/dts-analytics-webapp/modern typecheck`
Expected: PASS

**Step 4: Run frontend build**

Run: `pnpm -C source/dts-analytics-webapp/modern build`
Expected: PASS

**Step 5: Manual verification checklist**

- 模板库中可见 `科研项目管理指挥大屏`
- 由模板创建的新大屏包含 `3` 个页面，且开启自动轮播
- 全局筛选能驱动三屏接口取数
- 现有 `/project-cockpit` 页面行为不变
- `1920x1080` 与 `2560x1440` 公共运行态均无边铺满
- 第一屏、第二屏、第三屏分别对应“总体态势 / 执行与里程碑 / 风险与变更”

**Step 6: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java \
  source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java \
  source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts \
  source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx \
  source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx
git commit -m "feat: deliver project management command center screen"
```
