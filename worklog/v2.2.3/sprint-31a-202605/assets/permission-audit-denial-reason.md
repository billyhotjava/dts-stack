# Sprint-31A F4/T05 权限审计和拒绝原因

## 目标

资产权限问题必须可运营、可排查：能看到谁检查了什么资产、执行什么动作、是否允许、拒绝原因、授权来源和密级判定。

## 当前落地

### 审计写入

`POST /api/internal/asset-permission/check` 每次检查写入 `asset_permission_audit`：

| 字段 | 内容 |
|------|------|
| `action` | `CHECK_ALLOW` 或 `CHECK_DENY` |
| `assetType` | 权限检查资产类型 |
| `assetId` | 实际用于检查的资产 ID |
| `targetUser` | 被检查用户 |
| `permission` | 当前用户获得的最高权限 |
| `operator` | 当前服务调用方或当前用户 |
| `detail` | JSON，包含 action、requiredPermission、reason、grantSource、classificationDecision、assetKey |

### 运维查询

`PermissionAuditPage` 增加：

- `CHECK_ALLOW`
- `CHECK_DENY`

用于按检查结果过滤权限审计日志。

### 既有审计

- 授权新增：`GRANT`
- 授权撤销：`REVOKE`
- 归属变更：`CHANGE_OWNERSHIP`
- 资产治理字段变更：`CATALOG_ASSET_UPDATE` 审计 payload 包含 classification 等治理字段

## 拒绝原因

| reason | 说明 |
|--------|------|
| `denied` | 无角色、归属或显式授权 |
| `asset_required` | 缺资产类型或资产 ID/key |
| `unsupported_action` | action 不支持 |
| `classification_required` | 资产缺密级 |
| `classification_denied` | 用户密级不足 |
| `insufficient_permission` | 权限等级不足 |

## 后续依赖

- 运维可按 `CHECK_DENY` 追踪权限问题。
- analytics fallback 告警日志仍用于迁移期补充排查。
- Sprint-31 发布门禁失败可引用相同 reason 字段。
