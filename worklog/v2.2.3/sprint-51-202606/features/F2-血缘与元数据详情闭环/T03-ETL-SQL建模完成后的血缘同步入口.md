# T03: ETL/SQL 建模完成后的血缘同步入口

**优先级**: P1  
**状态**: READY  
**依赖**: T02

## 目标

在现有 ETL/SQL 建模页面提供血缘同步的可见交接点，避免用户完成任务后血缘图仍为空。

## 技术设计

- `/explore/etl/transform*` 任务成功后提示可同步 Addax 血缘。
- `/studio/sql-modeling` 发布或构建完成后提示 dbt manifest/模型血缘状态。
- 同步失败不阻断主流程，但要记录可读提示和诊断入口。

## 影响范围

- `TransformPage`
- `TransformDetailPage`
- `SqlModelingPage`
- `/catalog/lineage/import`

## 验证

- [ ] source-contract 断言成功态存在血缘同步/查看入口。
- [ ] sync-addax 请求失败时页面仍可继续操作。

## 完成标准

- [ ] 血缘同步不再是后台隐式步骤。
