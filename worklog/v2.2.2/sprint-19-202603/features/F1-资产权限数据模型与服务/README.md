# F1: 资产权限数据模型与服务

**优先级**: P0
**状态**: READY

## 目标

在 platform 侧建立统一的资产权限数据模型和服务，作为全系统权限的唯一真相源。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Liquibase 建表与数据源扩展 | P0 | READY | - |
| T02 | 资产权限判定服务 (AssetPermissionService) | P0 | READY | T01 |
| T03 | Internal Permission API | P0 | READY | T02 |
| T04 | Asset Ownership Management API | P0 | READY | T01 |
| T05 | Asset Grant Management API | P0 | READY | T01 |

## 完成标准

- [ ] 三张新表 + infra_data_source 扩展列创建成功
- [ ] 权限判定逻辑覆盖角色矩阵全部场景
- [ ] Internal API 支持 check / batch-check / accessible-ids
- [ ] 管理 API 支持 CRUD + 权限校验
