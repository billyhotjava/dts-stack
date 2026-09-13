# T04: source-contract、单测与浏览器验证证据

**优先级**: P0  
**状态**: IN_PROGRESS
**依赖**: T01,T02,T03

## 目标

为指标工作台语义编排编辑器补齐可回归的验证证据，确保 Dify 风格编辑能力、派生关系和原有拖拽绑定能力不会再次退化成只读画布。

## 技术设计

- 扩展 `metricWorkbench.source-contract.test.ts`：
  - 锁定画布工具栏。
  - 锁定 `METRIC_DERIVES`。
  - 锁定边配置、删除关系、预检入口。
- 扩展 `metricCanvas.helpers.test.ts`：
  - 派生边构造。
  - 环依赖检测。
  - 公式 JSON 合并/保护。
  - 删除依赖。
- 更新 `worklog/v2.2.3/sprint-57-202607/it/README.md`：
  - 增加指标工作台语义编排 smoke。
  - 留存截图和请求证据路径。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`
- `worklog/v2.2.3/sprint-57-202607/it/README.md`
- `worklog/v2.2.3/sprint-57-202607/assets/`

## 验证

- [x] `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`
- [x] `node --test --experimental-strip-types src/pages/modeling/metricWorkbench.source-contract.test.ts`
- [x] `pnpm exec tsc --noEmit`
- [x] `git diff --check`
- [x] 浏览器 smoke：空数据/接口失败场景下工具栏可见，截图 `assets/it-12-metric-workbench-toolbar.png`
- [x] 浏览器 smoke：mock 语义数据下指标 -> 指标连线形成 `METRIC_DERIVES`，截图 `assets/it-10-metric-workbench-derives-edge.png`，payload `assets/it-10-metric-derives-put-payload.json`
- [x] 浏览器 smoke：mock 语义数据下删除 `METRIC_DERIVES`，payload `assets/it-11-metric-derives-delete-payload.json`
- [x] 浏览器 smoke：窄屏可用性截图 `assets/it-12-metric-workbench-narrow.png`
- [ ] 浏览器 smoke：真实后端指标数据下指标 -> 指标连线、边配置保存、预检定位、原业务对象绑定回归（当前本地 `/api/semantic/*` 经 Vite 代理返回 500，需修复/切换可用后端后补证）。

## 完成标准

- [x] 所有目标自动化测试通过。
- [x] IT README 记录验证命令与证据路径。
- [x] 截图覆盖桌面视口和窄屏可用性。

## 实现记录（2026-07-04）

- `MetricNode` 补右侧 `source` handle，修复指标节点无法作为 `METRIC_DERIVES` 起点的 UI 缺陷。
- `metricWorkbench.source-contract.test.ts` 新增契约：指标节点必须同时具备左侧 target handle 与右侧 source handle。
- Playwright mock smoke 覆盖：
  - `订单金额 -> 客单价` 拖线后，`PUT /api/semantic/metrics/metric-avg` 写入 `formulaJson.dependsOnMetricIds=["metric-amount"]`。
  - 删除派生边后，`PUT /api/semantic/metrics/metric-avg` 移除 `dependsOnMetricIds`。
