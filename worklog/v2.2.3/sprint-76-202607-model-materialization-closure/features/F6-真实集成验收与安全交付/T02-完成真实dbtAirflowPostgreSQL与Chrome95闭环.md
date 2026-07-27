# T02：完成真实 dbt、Airflow、PostgreSQL 与 Chrome95 闭环

**优先级**：P0
**状态**：DRAFT
**依赖**：T01、F5

## 目标

在运行实例上从普通模型详情和高级建模页分别点击构建，验证二者进入同一候选/DAG/运行链，真实生成并核验四个目标关系；完成审核发布后再触发计划级生产计算，并在物理资产页和台账验证。

## 技术设计（Contract-first）

- **代表路径**：
  1. DIMENSION DESIGNER_GENERATED FULL table；
  2. FACT DESIGNER_GENERATED INCREMENTAL + KEY；
  3. SUMMARY DESIGNER_GENERATED view/table（按 capability）；
  4. APPLICATION DBT_MANAGED。
- **前置**：确认四 relation 初始不存在；创建隔离 candidate，不覆盖用户模型。
- **构建证据**：candidate id、每 entry pipeline run、dagRunId、dbt invocationId、bundle checksum。
- **数据库证据**：系统表 locator/type/columns、最小 `select ... limit 1`；FACT 第二次构建唯一键无重复。
- **发布证据**：quality/review/approval/PUBLISH、CatalogDataset id、physicalAssetRef、lineage。
- **DAG/持续计算**：验证共享 RELEASE_BUILD executor DAG、dbt ref 顺序、plan DAG 稳定、scope 聚合全部已发布模型、binding ACTIVE；完成一次 MANUAL 和一次 CRON OPERATIONAL_RUN。
- **落账顺序**：MANUAL 必须先有 pipeline run 再有 DagRun；CRON 必须先有 Airflow DagRun，再由首任务幂等 open pipeline run；均无孤儿/重复。
- **Airflow 真值**：在 UTC/CST 边界验证 Airflow actual schedule/nextRun/paused，与页面一致；cron 修改不改变 dagId。
- **失败传播**：manifest sync 或 relation probe 故障必须使 DagRun FAILED，页面不显示成功。
- **凭据**：只使用 F2/T04 安全 target；完成 runtime profile permission/cleanup 和 secret rotation，不把凭据写入证据。
- **Chrome95**：模型详情快捷构建/提交上线、工作台 reviewer/operator 独立动作、构建四态、失败重试、已发布/部署中、上线完成、运行异常、调度/运行摘要、窄屏；禁止 route mock。
- **失败注入**：至少覆盖 relation missing 和 stale implementation。

## 影响范围

- `it/evidence/it-02-*` 至 `it-14-*`
- Chrome95 screenshots/traces
- 隔离验收 candidate/run/asset

## 验证

- [ ] IT-01～IT-20 全部执行。
- [ ] 四 relation 名与 targetPhysicalName 一致。
- [ ] 普通/高级两路从 BUILD 开始共享相同 evidence。
- [ ] 上线后运行使用相同 gateway/probe，但 runPurpose=OPERATIONAL_RUN。
- [ ] Airflow actual 状态、平台 pipeline run 与 relation observation 一一对账。
- [ ] 页面与数据库/运行记录逐项对得上。

## Definition of Done

- [ ] 真实 relation + observation + published asset 三层同时存在。
- [ ] 失败场景不产生 published asset。
- [ ] 敏感信息未进入任何证据。
