# F1: 资产身份和生命周期契约

**优先级**: P0
**状态**: DONE

## 目标

建立 platform 内统一资产身份和生命周期规则，让数据源、ODS、dbt 模型、数据集、BI 数据集、大屏和后续指标资产都能被同一套 `asset_type + asset_key + asset_id` 契约引用。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 现有资产表和调用面盘点 | P0 | DONE | - |
| T02 | 统一资产身份规则设计 | P0 | DONE | T01 |
| T03 | 资产生命周期状态契约 | P0 | DONE | T02 |
| T04 | 来源标识和外部 FQN 归一 | P0 | DONE | T02 |
| T05 | 历史资产映射和冲突报告 | P1 | DONE | T02-T04 |

## 完成标准

- [x] 有资产身份枚举、key 规则和冲突处理策略。
- [x] 自动发现资产不会直接进入可消费状态。
- [x] OpenMetadata 和 legacy Catalog 来源可以落到同一资产身份；dbt、OpenLineage、Addax 在 F3 继续接入。
- [x] 历史资产冲突能输出 dry-run 报告服务。
