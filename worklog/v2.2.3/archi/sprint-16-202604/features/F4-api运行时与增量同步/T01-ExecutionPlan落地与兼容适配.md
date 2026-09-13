# T01: ExecutionPlan 落地与兼容适配

**优先级**: P0
**状态**: DRAFT
**依赖**: F1/T04

## 目标

把 execution plan 抽象接入实际任务创建和执行链路，同时保持现有文件、数据库任务兼容。

## 范围

- 新增 plan 生成和持久化结构。
- 将 existing Addax job path 作为一种 plan artifact。
- 任务执行时按 plan engine 分派。
- 保留 Airflow DAG 触发和 execution history 更新。

## 完成标准

- [ ] 数据库和文件任务行为不变。
- [ ] API 任务不依赖 JDBC-only 分支。
- [ ] plan artifact 可追踪、可审计、可重建。
- [ ] plan 中只有 secretRef，没有明文 secret。

