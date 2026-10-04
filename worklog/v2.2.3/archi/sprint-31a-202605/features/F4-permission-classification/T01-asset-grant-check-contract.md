# T01: asset_grant 权限检查契约

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

定义并实现统一资产权限检查接口，支持 READ、PREVIEW、EDIT、PUBLISH、GRANT 等动作。

## 技术设计

- 扩展 `/api/internal/asset-permission/check`，输入 `asset_type`、`asset_id/asset_key`、action、user context。
- 输出 allow/deny、reason、requiredPermission、classificationDecision、grantSource。
- action 映射到 READ / EDIT / MANAGE 权限等级，`PUBLISH/GRANT` 需要 MANAGE。
- 服务间调用沿用内部鉴权。

## 影响范围

- Asset permission service
- dts-metrics PlatformContractClient
- analytics permission adapter

## 验证

- [x] 无权限、权限不足、密级不足返回明确拒绝原因。
- [x] 拥有显式授权时允许访问。
- [x] `asset.id` 缺失时可用 `asset.key` 兜底检查。

## 完成标准

- [x] 所有消费侧可以只读调用该契约。

## 交付物

- `AssetPermissionService.checkAction`
- `/api/internal/asset-permission/check` 响应契约扩展
- `worklog/v2.2.3/sprint-31a-202605/assets/asset-permission-check-contract.md`
