# F5: 前端报表块与核心资产块

**优先级**: P0
**状态**: READY

## 目标

完成领导视角工作台的主内容区：

1. `TopReportsBlock`：左 2 栏，TOP 报表列表。
2. `CoreAssetsBlock`：右 1 栏，按密级排序的核心资产列表。
3. 数据大屏 chip strip：在 KPI 行之上，精简自旧版 Table 的大屏入口。
4. `LeaderOverviewPage` 壳：装配 FilterBar + KPI + Matrix + 两栏主块，负责数据拉取。
5. `workbench/index.tsx` 入口：删掉旧主体，切换到 `<LeaderOverviewPage />`；加空态 / 错误态 / 骨架屏。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | TopReportsBlock 组件 | P0 | READY | F3/T05, F1/T04 |
| T02 | CoreAssetsBlock 组件 | P0 | READY | F3/T05, F1/T05 |
| T03 | 数据大屏 chip strip | P1 | READY | - |
| T04 | LeaderOverviewPage 壳 + 数据装配 | P0 | READY | F3/T05, F4/T01, F4/T03, T01, T02, T03 |
| T05 | index.tsx 入口改造 + 空态 / 错误态 / 骨架 | P0 | READY | T04 |

## 完成标准

- [ ] 报表块按访问量降序取前 10；每行显示标题、访问、业务域 tag、密级 tag、最近访问相对时间。
- [ ] 资产块按密级降序、同级按更新时间降序取前 10；每行显示名称、密级 tag、更新相对时间、业务域 tag。
- [ ] 大屏 chip strip：有可见大屏时显示，0 个时整条消失；最多 6 个 chip + "更多 →" 跳转。
- [ ] `LeaderOverviewPage` 挂载即 fetch；filter 任一变更自动 refetch；skeleton / 错误态 / 空态齐备。
- [ ] `workbench/index.tsx` 仅保留 `<LeaderOverviewPage />` 调用，不再有旧 Mini 图表 / 待办 / 趋势等。
- [ ] 点击 TOP 报表行跳转到报表 URL + 打 `/reports/visit` 埋点。
