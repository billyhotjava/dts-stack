# F5: 语义指标中心产品化

**优先级**: P0
**状态**: READY
**目标**: 把当前语义指标中心从“基础 CRUD + dbt 生成”升级为业务可用的指标/DWS/ADS 建模工具。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 指标公式 DSL v1 | 支持 sum、count_distinct、count_if、sum_if、ratio、case_when、date_trunc |
| T02 | 指标口径版本 | 指标包含业务含义、公式、单位、格式、负责人、状态、版本、适用粒度 |
| T03 | DWS/ADS 生成增强 | schema.yml 生成 tests、meta、类型、owner、classification |
| T04 | 预览 SQL 安全和性能限制 | 预览强制 limit、超时、只读查询、错误友好显示 |
| T05 | 审核发布闭环强化 | 未生成、未预览、未通过门禁的模型不能发布 |
| T06 | BI Dataset 注册真实化 | 先落 QueryDataset；Superset 远端注册作为后续可插拔能力 |

## 代码关注点

- `SemanticModelingService`
- `SemanticModelingResource`
- `SemanticMetricDesignerPage`
- `SemanticDatasetsPage`
- `SemanticPublishPage`
