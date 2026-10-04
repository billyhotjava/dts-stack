# Sprint-12: 数据接入中心产品功能优化

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 以“数据源与驱动管理体验 + 入湖任务详情观测 + 基础配置协同入口”三条线为主，提升数据接入中心的产品完成度、易用性和信息闭环。

## 背景

当前数据接入中心的主链已经可用，但产品层面还有明显缺口：

- [DataSourcesPage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx) 同时承载列表、创建、编辑、测试连接、Excel 解析和回滚影响提示，页面状态多、信息密度高。
- [TransformDetailPage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx) 与 [ExecutionHistoryTable.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/components/ExecutionHistoryTable.tsx) 已具备执行、日志、增量状态、重试与重建 DAG 能力，但仍偏“工程接口直出”，对业务用户不够聚焦。
- 基础配置链路分散在数据源管理、驱动管理、项目主体接入、专题绑定中心、接入变更记录等多个页面，协同闭环还不清晰。

因此本 sprint 聚焦“产品完成度”，不再从底层稳定性入手，而是优先打通高频配置入口与任务详情体验。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 数据源与驱动管理体验优化 | 3 | IN_PROGRESS |
| F2 | 入湖任务详情与执行观测优化 | 3 | IN_PROGRESS |
| F3 | 基础配置协同入口优化 | 3 | READY |

## 当前诊断结论

- 数据源管理页的首屏信息架构偏重“表单能力堆叠”，轻“产品主任务分组”。
- 入湖任务详情页已经有丰富的数据，但“最新执行 / 日志 / 观测 / 增量状态”的层次还不够聚合。
- 基础配置页之间已存在逻辑关联，但入口之间仍更像平铺菜单，而不是协同流程。

## 完成标准

- [ ] 数据源与驱动管理页的高频操作路径更清晰
- [ ] 入湖任务详情与执行历史的信息层级更易读
- [ ] 主体接入、专题绑定、接入变更之间出现更明确的产品闭环
- [ ] 至少形成一批可交付的产品侧优化，而不是只停留在诊断
