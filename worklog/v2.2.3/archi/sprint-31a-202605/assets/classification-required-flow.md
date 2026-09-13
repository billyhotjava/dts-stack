# Sprint-31A F4/T03 密级写入和缺失处理

## 目标

`classification` 是治理事实和权限控制字段，缺失时不能被解释为 `PUBLIC` 或“不限制”。

## 当前落地

### 访问检查

`AccessChecker.canRead` 对普通用户执行以下规则：

- `classification` 为空：拒绝读取。
- `classification` 有值：继续进入既有 ABAC / 角色密级判断。
- 超级管理员仍保留治理查看能力。

### 内部权限契约

`POST /api/internal/asset-permission/check` 对缺失资产密级返回：

```json
{
  "allowed": false,
  "reason": "classification_required",
  "classificationDecision": "MISSING_ASSET_CLASSIFICATION"
}
```

### 治理变更审计

`PATCH /api/catalog/assets-v2/{id}/governance` 的 `CATALOG_ASSET_UPDATE` 审计 payload 记录本次提交的治理字段，包括：

- `classification`
- `warehouseLayer`
- `ownerDept`
- `businessOwner`
- `lifecycleStatus`
- `domainId`
- `enabled`

## 治理口径

历史缺失密级资产不会被自动默认成公开资产：

- 资产契约 `missingFields` 包含 `classification`。
- 治理状态进入 `PENDING_CLASSIFICATION` 或 `PENDING_GOVERNANCE`。
- 治理缺口报告返回 blocking gap。
- 血缘失败/发布阻断报告按 blocking 处理。

## 验证

新增单测：

- `AccessCheckerTest.canRead_shouldDenyDatasetWithMissingClassification`
- `AssetPermissionServiceTest.checkAction_shouldDenyWhenAssetClassificationIsMissing`

## 后续依赖

- F4/T05 增加拒绝原因审计汇总。
- F5 资产门户给缺密级资产提供明确整改入口。
