# 数据资产门户体验收敛说明

**Sprint**: Sprint-31A
**Feature**: F5 数据资产门户体验收敛
**状态**: DONE

## 目标

把数据资产门户从“资产卡片列表”收敛成资产治理入口，使业务用户能判断资产是否可引用，工程用户能进入资产合同、字段合同、血缘和权限治理链路。

## 本次收敛内容

1. 资产地图按仓库分层和治理状态展示，卡片显示“可引用 / 治理阻断 / 待确认 / 主目录缓存”等业务化状态。
2. 列表页接入治理缺口和血缘证据缺口报告，用于提示当前筛选范围是否存在阻断项。
3. 详情页接入资产合同和字段合同，展示资产键、授权资产标识、字段来源和缺失治理字段。
4. 权限申请页从详情页可直达资产授权，并且使用 platform 资产合同中的 `grantAssetType + grantAssetId`。
5. 空状态从“暂无资产地图”调整为“未发现当前账号可见资产”，避免把权限、密级或治理过滤误报成无数据。

## 前端契约

- `resolveAssetReadiness` 是资产门户的前端可用性判断入口。
- `buildAssetGrantUrl` 是从资产详情跳转到 platform 资产授权的统一入口。
- `DatasetDetailPage` 优先使用 `/api/catalog/assets-v2/{id}/contract` 和 `/api/catalog/assets-v2/{id}/schema-contract`。
- `DatasetsPage` 使用 `/api/catalog/assets-v2/governance-gaps` 和 `/api/catalog/assets-v2/lineage-failures` 做页面级治理信号。

## 验收边界

- 本轮不实现新的审批流，只把权限治理入口接到既有 `asset_grant` 管理页面。
- 本轮不重构资产门户视觉框架，只做业务状态和治理入口收敛。
- 按当前执行约束，本轮只做静态 diff 检查；编译、测试和容器构建在 Sprint-31A/31/32 全部完成后统一执行。
