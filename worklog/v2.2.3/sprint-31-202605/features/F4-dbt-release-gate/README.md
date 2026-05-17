# F4: dbt 发布门禁与模型资产同步

**优先级**: P0
**状态**: DONE
**目标**: 让 dbt 从“可触发构建”升级为“可阻断上线、可追溯证据、可同步资产”的企业级发布能力。

**Sprint-31A 依赖**: 发布门禁必须读取 Sprint-31A 的 schema contract、governance gaps、lineage failures 和 asset permission check。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | release gate 增加 strict/prod 阻断模式 | DONE: release/quality blockers 不再降级为 warnings，生产发布直接 BLOCKED |
| T02 | schema.yml contract 完整性检查 | DONE: tests、expected_data_type、owner、classification 缺失进入 blockers |
| T03 | dbt build 证据与 Git 元数据固化 | DONE: 发布 conf 写入 gitRef、commitSha、buildInvocationId，结果返回 run_results 路径 |
| T04 | 模型资产同步增强 | DONE: dbt asset sync 已补模型资产层级、运行血缘和列级血缘，治理字段缺失由 gate 阻断 |
| T05 | 前端发布页展示 blockers/warnings/evidence | DONE: 发布接口返回 blockers、warnings、qualityGate、releaseGate、buildEvidence，前端发布弹窗已展示 |

## 代码关注点

- `DbtQualityGateService`
- `DbtReleaseGateService`
- `DbtReleaseSubmissionService`
- `DbtSourceService`
- `ModelingSqlModelService`

## 交付记录

- `DbtReleaseSubmissionService` 已区分 blockers 和 warnings；blockers 不允许通过确认继续发布。
- `DbtReleaseGateService` 对无构建证据、构建失败、构建过期和 selector 不一致按 strict/prod 规则阻断或告警。
- `DbtQualityGateService` 把 schema.yml 测试、`expected_data_type`、`owner`、`classification` 作为发布门禁检查项。
- `DbtReleaseSubmissionService` 继续把 `gitRef`、`commitSha`、`buildInvocationId` 写入 Airflow DAG conf，保留构建证据链。
- 交付契约见 `../../assets/dbt-release-gate-contract.md`。
