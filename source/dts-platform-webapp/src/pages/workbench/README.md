# 工作台 · 领导视角首页

Sprint-15 起，`/workbench` 不再呈现通用 KPI + 待办 + 图表组合，而是根据登录用户角色
（`INST_LEADER` / `DEPT_LEADER` / `EMP`）渲染差异化的领导视角总览页：
顶部滚动筛选器（部门 / 业务域 / 时间范围）→ 角色化 KPI 行 → 业务域访问热度 → TOP 大屏 + 核心资产。

核心实现入口：

- 页面组装：`LeaderOverviewPage.tsx`（本目录）
- 子块：`components/{KpiRow, DomainMatrix, TopReportsBlock, CoreAssetsBlock, ScreenStrip, WorkbenchFilterBar}.tsx`
- 数据源：`workbenchService.leaderOverview({ scope, deptCode, bizDomain, timeRange })`

完整方案与接口契约见
[`docs/superpowers/specs/2026-04-24-platform-workbench-leader-overview-design.md`](../../../../docs/superpowers/specs/2026-04-24-platform-workbench-leader-overview-design.md)。
