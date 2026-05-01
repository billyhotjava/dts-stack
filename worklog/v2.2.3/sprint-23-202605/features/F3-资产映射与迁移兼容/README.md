# F3: 资产映射、迁移与兼容

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

建立 `om_entity_id` / `fqn` / `legacy_dataset_id` 的稳定映射，迁移存量 `catalog_dataset` 治理属性，并保留旧 API 的兼容路径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `catalog_asset_mapping` 模型与匹配状态 | P0 | DONE | F1 |
| T02 | FQN 规则诊断和自动匹配器 | P0 | DONE | T01 |
| T03 | `catalog_dataset` 存量治理属性迁移 dry-run | P0 | DONE | F2,T02 |
| T04 | 迁移执行与回滚脚本 | P0 | IN_PROGRESS | T03 |
| T05 | 旧 `/catalog/datasets` 路径兼容与 deprecation 标识 | P1 | DONE | T04 |

## 完成标准

- [x] 每个可匹配资产有 `om_entity_id`、`fqn`、`legacy_dataset_id` 映射。
- [x] 自动匹配输出置信度和原因。
- [x] 无法匹配的资产进入人工确认，不被静默合并。
- [x] 存量密级、部门、生命周期、主题域迁移后可校验。
- [x] 回滚后旧数据资产页面仍可读。
