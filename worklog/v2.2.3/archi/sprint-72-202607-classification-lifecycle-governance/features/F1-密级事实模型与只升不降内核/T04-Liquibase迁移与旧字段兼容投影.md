# T04: Liquibase 迁移与旧字段兼容投影

**优先级**: P0
**状态**: IN_PROGRESS
**编码状态**: DONE（统一验证延后）
**依赖**: T02,T03

## 目标

在不一次性破坏 CRITICAL 共享实体的前提下引入新事实，并让旧 classification 字段成为兼容投影。

## 技术设计

- Liquibase 支持空库、现有库升级和 rollback 边界。
- 新建 projection bridge，从新事实向旧字段单向投影。
- 迁移期支持 old/new 双读对账，不允许旧字段反向覆盖新事实。
- 不在本 Sprint 删除旧列。

## 影响范围

平台数据库、`CatalogDataset` 等旧密级字段、API DTO 兼容。

## 验证

- [ ] 空库/升级库迁移契约。
- [ ] 投影只升不降且幂等。
- [ ] 旧客户端读取值与新 effectiveLevel 一致。

## 完成标准

- [ ] 可按资产类型启停新读路径。
- [ ] 回滚不会丢失已追加的密级事件。
