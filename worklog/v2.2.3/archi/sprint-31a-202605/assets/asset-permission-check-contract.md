# Sprint-31A F4/T01 asset_grant 权限检查契约

## 目标

所有增值服务和前端消费方通过 platform 统一检查资产权限，不再直接读取 `asset_grant` 或本地权限表。

## 当前落地

内部端点：

```text
POST /api/internal/asset-permission/check
```

服务鉴权继续由 `ServiceDependencyAuthenticationFilter` 控制，允许已登记的 analytics / metrics 内部调用方读取权限契约。

## 请求契约

```json
{
  "username": "ptrdemo",
  "userRoles": ["ROLE_PTR"],
  "userDeptCode": "D01",
  "userClassification": "INTERNAL",
  "assetClassification": "INTERNAL",
  "action": "PREVIEW",
  "asset": {
    "type": "DATASET",
    "id": "7a9b...",
    "key": "source:demo/schema:dwd/table:orders"
  }
}
```

说明：

- `asset.id` 优先使用 Catalog 读取契约返回的 `grantAssetId`。
- `asset.key` 用于历史资产还没有稳定 ID 时的兼容检查；当 `id` 为空时作为 `asset_grant.asset_id` 参与匹配。
- `action` 支持 `READ`、`PREVIEW`、`EDIT`、`PUBLISH`、`GRANT`、`MANAGE`。

## 动作到权限映射

| action | requiredPermission |
|--------|--------------------|
| `READ` / `VIEW` / `PREVIEW` | `READ` |
| `EDIT` / `UPDATE` | `EDIT` |
| `PUBLISH` / `GRANT` / `MANAGE` / `ADMIN` | `MANAGE` |

## 返回契约

| 字段 | 说明 |
|------|------|
| `allowed` | 是否允许 |
| `permission` | 当前用户获得的最高权限 |
| `reason` | 允许或拒绝原因 |
| `requiredPermission` | 当前 action 需要的权限 |
| `action` | 规范化后的动作 |
| `assetType` | 规范化后的资产类型 |
| `assetId` | 实际用于检查的资产 ID，优先 id，兜底 key |
| `assetKey` | 请求传入的资产 key |
| `classificationDecision` | `MISSING_ASSET_CLASSIFICATION` / `ALLOWED` / `DENIED` / `OVERRIDDEN` / `SUPERUSER` |
| `grantSource` | `superuser` / `inst_read` / `dept_ownership` / `explicit_grant` / `level_override` 等 |

## 拒绝原因

| reason | 说明 |
|--------|------|
| `asset_required` | 缺资产类型或资产 ID/key |
| `unsupported_action` | action 不在支持范围内 |
| `denied` | 没有角色、归属或显式授权 |
| `classification_required` | 资产缺密级，不能按公开资产访问 |
| `classification_denied` | 用户密级低于资产密级且无越级授权 |
| `insufficient_permission` | 有权限但不足以执行当前 action |

## 后续依赖

- F4/T02 用该契约对齐资产列表和详情过滤。
- F4/T04 要求 analytics / metrics 只读调用该契约。
- Sprint-31 发布门禁使用 `PUBLISH` action 检查发布人是否具备 `MANAGE` 权限。
