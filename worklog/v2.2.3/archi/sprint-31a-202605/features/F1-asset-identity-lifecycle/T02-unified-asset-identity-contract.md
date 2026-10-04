# T02: 统一资产身份规则设计

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

定义企业级资产身份规则，避免同一物理表、dbt 模型、BI 数据集或大屏在不同模块中拥有不同事实源身份。

## 技术设计

- 定义 `asset_type` 枚举和 `asset_key` 生成规则。
- 明确 `asset_id` 为 platform 内部稳定 ID。
- 设计 `DATASET`、`DBT_MODEL`、`BI_DATASET`、`SCREEN`、`METRIC` 的兼容策略。
- 预留 dts-metrics 引用资产所需的稳定 key。

## 影响范围

- Catalog domain/entity
- asset_grant / asset_ownership
- dts-metrics platform contract

## 验证

- [x] 同名不同源、同源不同 schema、dbt model 重名场景有明确 key。
- [x] 权限表和资产表能用同一身份引用。
- [ ] 最终统一测试阶段运行 `CatalogAssetKeyTest`。

## 完成标准

- [x] 文档和实现都能表达唯一身份规则。
