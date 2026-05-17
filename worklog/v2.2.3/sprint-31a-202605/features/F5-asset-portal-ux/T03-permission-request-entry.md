# T03: 权限申请和可见性提示

**优先级**: P1
**状态**: DONE
**依赖**: F4

## 目标

在资产门户中提供清晰的权限状态和申请入口，避免无权访问直接进入 403 页面。

## 技术设计

- 显示当前用户权限、密级约束和可申请动作。
- 拒绝原因友好化。
- 申请流先接入 platform 既有审批或保留候选记录。
- 详情页按资产合同中的 `grantAssetType + grantAssetId` 跳转到 platform 资产授权管理。
- 资产授权管理页支持从 URL 查询参数自动带入授权对象并加载已有授权。

## 影响范围

- AssetGrantPage / MyGrantsPage
- DatasetDetailPage
- permission audit

## 验证

- [x] 无权用户能看到可申请路径。
- [x] 不显示敏感元数据。

## 完成标准

- [x] 权限体验与治理边界一致。
