# Sprint-31 F6: platform 权限事实源消费契约

## 目标

本版本把 platform `asset_grant` 作为 BI/大屏消费权限唯一事实源。analytics 本地
`analytics_screen_access` 只保留只读兼容和应急迁移窗口，不能再成为新授权写入点。

## 资产类型

| 场景 | asset_type | asset_id |
|---|---|---|
| analytics 同步大屏 | `SCREEN` | 大屏数值 ID，例如 `7` |
| 手工 BI 链接 | `DASHBOARD` | `bi_report_link.code` |
| 数据集 / 模型 | 业务资产类型 | Sprint-31A 资产身份 |

`bi_report_link.code=screen-7` 或 `report_type=SCREEN` 时，platform 必须按
`SCREEN:7` 查询授权，不能再按 `DASHBOARD:screen-7` 判断。

## 可见性规则

| 密级 | 无授权 | 有 READ/VIEW grant | 有 MANAGE/EDIT grant |
|---|---|---|---|
| PUBLIC | 可见 | 可见 | 可管理 |
| INTERNAL | 不可见 | 密级满足则可见 | 可管理 |
| SECRET | 不可见 | 密级满足则可见 | 可管理 |
| CONFIDENTIAL | 不可见 | 密级满足或 level_override 才可见 | 可管理 |

同步大屏不再依赖 `role_codes/dept_codes` 做非 PUBLIC 裸放行。角色共享必须进入
platform `asset_grant` 的 `grantee_type=ROLE`。

角色匹配兼容两种形态：`ROLE_PTR` 与 `PTR` 会同时参与 grant 查询，避免 Keycloak
authority 前缀和业务角色名展示值不一致导致授权丢失。

## analytics 本地 IAM 策略

默认配置：

```yaml
analytics.local-iam.read-only: true
dts.analytics.screen-permission.platform-source-enabled: true
dts.analytics.screen-permission.local-fallback-enabled: true
dts.analytics.screen-permission.local-write-enabled: false
```

含义：

- 新增、撤销、列表授权优先调用 platform internal asset-permission API。
- 本地写入默认关闭；即使误开 `local-write-enabled=true`，只要
  `analytics.local-iam.read-only=true`，仍不允许写本地表。
- 读取 fallback 仅用于迁移窗口，命中即记录 `event=analytics_permission_fallback`
  和中央审计 `screen.permission.local_fallback`。

## 存量迁移

接口：

```http
POST /api/screens/admin/migrate-local-grants
```

行为：

- 读取 `analytics_screen_access` 全量存量授权。
- 跳过已归档或不存在的大屏。
- 通过 `ScreenOwnershipService.createGrant` 写入 platform `asset_grant`，使用 upsert
  语义，允许重复执行。
- 返回 `total/migrated/skipped/conflicts/conflictItems`，冲突项不终止整批迁移。

## 验收

- `ptrdemo` 拥有角色 grant 时，大屏列表能看到对应 INTERNAL 大屏。
- 无 grant 的 INTERNAL/SECRET/CONFIDENTIAL 大屏不能出现在列表和我的概览 fallback。
- PUBLIC 大屏无授权仍可见。
- fallback 命中后，日志包含 `analytics_permission_fallback`，中央审计包含
  `screen.permission.local_fallback`。
