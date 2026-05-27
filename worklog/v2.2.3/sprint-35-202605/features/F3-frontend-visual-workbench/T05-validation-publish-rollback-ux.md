# T05: 验证诊断、发布和回滚 UX

**优先级**: P0
**状态**: READY
**依赖**: T02-T04

## 目标

让用户能在前端看到 graph、contract、dbt、review、publish 每个阶段的状态、失败定位和回滚路径。

## 技术设计

- 右侧诊断面板显示 `ValidationDiagnostic[]`，支持点击定位到 node/edge/field/metric。
- Action toolbar 按状态启用：preflight、validate、submit review、publish dry-run、publish、rollback。
- 发布页展示 platform publish reference、BI Dataset reference、lineage registration result。
- 回滚页展示 graph draft version、artifact version、platform publish record。

## 影响范围

- `source/dts-metrics-webapp/src/pages/semantic/**`
- `source/dts-metrics-webapp/src/features/semantic/**`
- `worklog/v2.2.3/sprint-35-202605/it/evidence/frontend-workbench/`

## 验证

- [ ] 诊断项点击可定位画布元素。
- [ ] 未 DBT_VALIDATED 时 publish 按钮禁用。
- [ ] 回滚 UI 不直接删除生产模型，只调用后端回滚 API。

## 完成标准

- [ ] 前端完整表达验证、发布、消费、回滚闭环。
