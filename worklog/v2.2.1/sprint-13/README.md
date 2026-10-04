# Sprint-13：项目管理专用大屏模板

## 目标

基于 `worklog/v2.2.1/req/pm/project3.xlsx` 的指标需求，新增一套科研院所项目管理专用的传统大屏模板，采用 `3` 屏轮播，不改现有 `/project-cockpit` 页面，并满足 `1920x1080` 与 `2K 16:9` 显示屏无边全屏展示。

## 范围

### 需求依据

- `worklog/v2.2.1/req/pm/project3.xlsx`
- `worklog/v2.2.1/sprint-9/project-cockpit-system-design.md`

### 前端

- `source/dts-analytics-webapp/modern/src/pages/screens/`
- `source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`

### 后端

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`

### 验证

- `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT test`
- `pnpm -C source/dts-analytics-webapp/modern exec node --import tsx --test ...`
- `pnpm -C source/dts-analytics-webapp/modern typecheck`
- `pnpm -C source/dts-analytics-webapp/modern build`

## 结论

- 新建模板，不替换也不改造现有项目看板页面
- 模板形态为 `3` 屏轮播：`总体态势 / 执行与里程碑 / 风险与变更`
- `project3.xlsx` 中的项目管理指标口径收敛到后端聚合接口，不在模板里重复算业务公式
- 运行态需要支持 `1080p` 与 `2K 16:9` 无边铺满

## 文档

- [design.md](./design.md)
- [plan.md](./plan.md)
