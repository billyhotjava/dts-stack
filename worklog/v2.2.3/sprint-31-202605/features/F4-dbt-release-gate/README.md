# F4: dbt 发布门禁与模型资产同步

**优先级**: P0
**状态**: READY
**目标**: 让 dbt 从“可触发构建”升级为“可阻断上线、可追溯证据、可同步资产”的企业级发布能力。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | release gate 增加 strict/prod 阻断模式 | 测试失败、构建过期、缺 schema 时 prod 阻断发布 |
| T02 | schema.yml contract 完整性检查 | columns、tests、expected_data_type、owner、classification 缺失可阻断 |
| T03 | dbt build 证据与 Git 元数据固化 | 记录 gitRef、commitSha、invocationId、run_results 路径 |
| T04 | 模型资产同步增强 | dbt 模型同步到 Catalog 时补 warehouseLayer、ownerDept、classification |
| T05 | 前端发布页展示 blockers/warnings/evidence | 工程师能看到为什么不能发布 |

## 代码关注点

- `DbtQualityGateService`
- `DbtReleaseGateService`
- `DbtReleaseSubmissionService`
- `DbtSourceService`
- `ModelingSqlModelService`
