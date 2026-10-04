# T04: Asset Ownership Management API

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

提供资产归属管理的 CRUD API，供前端管理界面调用。

## 技术设计

### 端点

```
GET    /api/asset-ownership?deptCode=&assetType=&keyword=&page=&size=
PUT    /api/asset-ownership/{id}
POST   /api/asset-ownership/batch
```

### 权限控制

`@PreAuthorize` 限制为: INST_DATA_OWNER, SYS_ADMIN, OP_ADMIN

### 业务规则

- GET: 支持按部门、资产类型筛选，keyword 模糊搜索资产名称
- PUT: 修改归属部门，记录审计日志 (CHANGE_OWNERSHIP)
- POST batch: 批量设置归属部门（用于数据源同步后的批量分配）

### Schema Sync 集成

在现有 Schema Sync 流程末尾增加钩子：
- 新发现的表 → 自动创建 asset_ownership（继承数据源部门）
- `ON CONFLICT DO NOTHING`（已手动调整的不覆盖）

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/` — 新增 AssetOwnershipResource
- Schema Sync 相关 service — 增加 ownership 自动创建逻辑

## 验证

- [ ] CRUD 功能正确
- [ ] 非院级角色访问返回 403
- [ ] 批量操作正确处理部分失败
- [ ] Schema Sync 自动创建 ownership 记录

## 完成标准

- [ ] API 契约与设计文档一致
- [ ] 审计日志完整
