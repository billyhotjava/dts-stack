# F6: 消费层发布与 platform 权限统一

**优先级**: P0
**状态**: DONE
**目标**: 稳住本版本权限边界：platform 成为唯一权限事实源，analytics 本地权限只读/应急 fallback。

**Sprint-31A 依赖**: 消费层权限必须使用 Sprint-31A 的 `asset_grant`、classification deny、permission audit 和 read-only fallback 契约。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 迁移存量 `analytics_screen_access` 到 `asset_grant` | DONE: `POST /api/screens/admin/migrate-local-grants` 通过 `ScreenOwnershipService` 幂等 upsert 到 platform asset_grant，返回 total/migrated/skipped/conflicts |
| T02 | local fallback 命中报表 | DONE: fallback 命中输出 `event=analytics_permission_fallback` WARN，并写中央审计 `screen.permission.local_fallback` |
| T03 | 默认关闭 local write | DONE: `analytics.local-iam.read-only=true` 默认生效，`DTS_ANALYTICS_SCREEN_PERMISSION_LOCAL_WRITE_ENABLED=true` 也不能绕过只读开关 |
| T04 | 大屏列表权限回归 | DONE: platform 对同步大屏统一用 `SCREEN:{id}`；PUBLIC 无授权可见，INTERNAL/SECRET/CONFIDENTIAL 必须显式 grant 且满足密级/override |
| T05 | platform-webapp 只保留入口和消费态 | DONE: platform-webapp 不复制授权判断；消费侧读取 platform/analytics 后端返回结果 |

## 代码关注点

- `AssetPermissionService`
- `AssetPermissionInternalResource`
- `ScreenPermissionService`
- `ScreenOwnershipService`
- `ScreenResource`

## 交付记录

- `DashboardAccessGuard` 对 `screen-*` / `reportType=SCREEN` 的报表镜像切换到 `SCREEN:{id}` 授权事实源，不再使用 `DASHBOARD:screen-*`。
- 同步大屏的非 PUBLIC 可见性不再依赖 `bi_report_link.role_codes/dept_codes` 裸放行，只依赖 platform `asset_grant`、密级清单和 level override。
- analytics 本地授权写入默认只读；platform grant 写失败时不会静默回落本地写，除非显式 break-glass 且关闭 `analytics.local-iam.read-only`。
- 本地 fallback 仍保留一个 sprint 的读取兼容窗口，但每次命中都会产生 WARN 日志和中央审计事件，作为迁移残留清单来源。
- 迁移和验收契约见 `../../assets/platform-permission-consumption-contract.md`。
